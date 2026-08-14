import { createHash } from "node:crypto";
import {
  FieldValue,
  Timestamp,
  type DocumentReference
} from "firebase-admin/firestore";
import { requireCouponBlobPath } from "./blobPath";
import { getAdminDb } from "./firebaseAdmin";

export const BLOB_CLEANUP_LEASE_MS = 2 * 60 * 1_000;
export const MAX_BLOB_CLEANUP_ATTEMPTS = 5;

export type BlobCleanupJob = {
  roomId: string;
  couponId: string;
  paths: string[];
  attempts: number;
};

export type BlobCleanupMetrics = {
  pending: number;
  deleting: number;
  retry: number;
  deleted: number;
  deadLetter: number;
  due: number;
  staleDeleting: number;
};

export function blobCleanupHealth(metrics: BlobCleanupMetrics): "healthy" | "warning" | "critical" {
  if (metrics.deadLetter > 0 || metrics.staleDeleting > 0) return "critical";
  if (metrics.retry > 0 || metrics.due > 0) return "warning";
  return "healthy";
}

function normalizedPaths(paths: string[]) {
  return [...new Set(paths)].sort();
}

export function blobCleanupJobId(roomId: string, couponId: string, paths: string[]) {
  const identity = [roomId, couponId, ...normalizedPaths(paths)].join("\u001f");
  return createHash("sha256").update(identity).digest("hex");
}

export function blobCleanupRetryDelayMs(attempt: number, random = Math.random) {
  const normalizedAttempt = Math.max(1, Math.floor(attempt));
  const base = Math.min(24 * 60 * 60 * 1_000, 5 * 60_000 * (2 ** (normalizedAttempt - 1)));
  const jitter = 0.8 + Math.min(1, Math.max(0, random())) * 0.4;
  return Math.round(base * jitter);
}

export function isDueBlobCleanup(params: {
  status: unknown;
  nextAttemptAtMs?: number;
  leaseUntilMs?: number;
  nowMs: number;
}) {
  if (params.status === "pending" || params.status === "retry") {
    return (params.nextAttemptAtMs ?? 0) <= params.nowMs;
  }
  return params.status === "deleting" && (params.leaseUntilMs ?? 0) <= params.nowMs;
}

export function parseBlobCleanupData(data: Record<string, unknown> | undefined): BlobCleanupJob | null {
  if (!data) return null;
  const { roomId, couponId, paths, attempts } = data;
  if (typeof roomId !== "string" || typeof couponId !== "string") return null;
  if (!Array.isArray(paths) || paths.length === 0 || paths.length > 4) return null;
  if (!Number.isInteger(attempts) || (attempts as number) < 0) return null;

  try {
    const validated = normalizedPaths(paths.map((path) => requireCouponBlobPath(path, roomId, couponId)));
    return { roomId, couponId, paths: validated, attempts: attempts as number };
  } catch {
    return null;
  }
}

export function blobCleanupHasLiveReference(
  paths: string[],
  imageBlobPath: unknown,
  thumbnailBlobPath: unknown
) {
  const references = new Set(
    [imageBlobPath, thumbnailBlobPath].filter((path): path is string => typeof path === "string")
  );
  return paths.some((path) => references.has(path));
}

export function blobCleanupDeletablePaths(
  paths: string[],
  imageBlobPath: unknown,
  thumbnailBlobPath: unknown
) {
  const references = new Set(
    [imageBlobPath, thumbnailBlobPath].filter((path): path is string => typeof path === "string")
  );
  return paths.filter((path) => !references.has(path));
}

function isAlreadyExistsError(error: unknown) {
  if (typeof error !== "object" || error === null || !("code" in error)) return false;
  const code = (error as { code?: unknown }).code;
  return code === 6 || code === "6" || code === "already-exists";
}

export async function enqueueBlobCleanup(
  roomId: string,
  couponId: string,
  paths: string[],
  now = new Date()
): Promise<DocumentReference> {
  const validated = normalizedPaths(paths.map((path) => requireCouponBlobPath(path, roomId, couponId)));
  if (validated.length === 0 || validated.length > 4) {
    throw new Error("정리할 쿠폰 이미지 경로 수가 올바르지 않습니다.");
  }

  const ref = getAdminDb().doc(`blobCleanupQueue/${blobCleanupJobId(roomId, couponId, validated)}`);
  try {
    await ref.create({
      roomId,
      couponId,
      paths: validated,
      status: "pending",
      attempts: 0,
      nextAttemptAt: Timestamp.fromDate(now),
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });
  } catch (error) {
    if (!isAlreadyExistsError(error)) throw error;
  }
  return ref;
}
