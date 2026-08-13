import { createHash } from "crypto";
import { Timestamp } from "firebase-admin/firestore";
import { getAdminDb } from "./firebaseAdmin";
import { ApiError } from "./http";

export type RateLimitPolicy = {
  action: string;
  limit: number;
  windowSeconds: number;
};

export function rateLimitWindow(nowMs: number, windowSeconds: number): number {
  if (!Number.isInteger(windowSeconds) || windowSeconds <= 0) {
    throw new Error("windowSeconds는 양의 정수여야 합니다.");
  }
  return Math.floor(nowMs / (windowSeconds * 1000));
}

export function rateLimitDocumentId(subject: string, policy: RateLimitPolicy, nowMs: number): string {
  const window = rateLimitWindow(nowMs, policy.windowSeconds);
  return createHash("sha256")
    .update(`${policy.action}:${subject}:${window}`)
    .digest("hex");
}

export async function enforceUserRateLimit(
  uid: string,
  policy: RateLimitPolicy,
  nowMs = Date.now()
): Promise<void> {
  if (!Number.isInteger(policy.limit) || policy.limit <= 0) {
    throw new Error("limit은 양의 정수여야 합니다.");
  }

  const db = getAdminDb();
  const documentId = rateLimitDocumentId(uid, policy, nowMs);
  const ref = db.doc(`rateLimits/${documentId}`);
  const expiresAtMs = (rateLimitWindow(nowMs, policy.windowSeconds) + 1) * policy.windowSeconds * 1000;

  await db.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(ref);
    const count = snapshot.exists ? Number(snapshot.get("count") ?? 0) : 0;
    if (count >= policy.limit) {
      throw new ApiError(429, "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.", {
        retryAfterSeconds: Math.max(1, Math.ceil((expiresAtMs - nowMs) / 1000))
      });
    }

    transaction.set(ref, {
      action: policy.action,
      subjectHash: createHash("sha256").update(uid).digest("hex"),
      count: count + 1,
      expiresAt: Timestamp.fromMillis(expiresAtMs),
      updatedAt: Timestamp.fromMillis(nowMs)
    });
  });
}
