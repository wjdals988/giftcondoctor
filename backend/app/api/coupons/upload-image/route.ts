import { del } from "@vercel/blob";
import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { requireRoomMember, requireUser } from "@/lib/auth";
import { requireCouponBlobPath } from "@/lib/blobPath";
import {
  blobCleanupHasLiveReference,
  blobCleanupJobId,
  enqueueBlobCleanup
} from "@/lib/blobCleanupQueue";
import {
  couponUploadCandidatePaths,
  requireCouponUploadId,
  storeCouponImage
} from "@/lib/couponImageStorage";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, serverTiming } from "@/lib/http";
import { enforceUserRateLimit } from "@/lib/rateLimit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const startedAt = performance.now();
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "coupon-image-upload", limit: 20, windowSeconds: 3600 });
    const form = await request.formData();
    const roomId = String(form.get("roomId") ?? "").trim();
    const couponId = String(form.get("couponId") ?? "").trim();
    const uploadId = requireCouponUploadId(form.get("uploadId"));
    const image = form.get("image");

    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }
    if (!(image instanceof File)) {
      throw new ApiError(400, "이미지 파일이 필요합니다.");
    }
    await requireRoomMember(roomId, token.uid);
    const candidatePaths = couponUploadCandidatePaths(roomId, couponId, uploadId);
    const cleanupJob = await enqueueBlobCleanup(
      roomId,
      couponId,
      candidatePaths,
      new Date(Date.now() + 60 * 60_000)
    );
    try {
      const storageStartedAt = performance.now();
      const stored = await storeCouponImage(roomId, couponId, image, { uploadId });
      const finishedAt = performance.now();
      return json(stored, {
        headers: {
          "Server-Timing": serverTiming([
            { name: "prepare", durationMs: storageStartedAt - startedAt },
            { name: "blob-store", durationMs: finishedAt - storageStartedAt }
          ])
        }
      });
    } catch (error) {
      await cleanupJob.set({
        status: "pending",
        nextAttemptAt: Timestamp.now(),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true }).catch((cleanupError) => {
        console.error("failed to expedite failed upload cleanup", { roomId, couponId, uploadId, cleanupError });
      });
      throw error;
    }
  } catch (error) {
    return jsonError(error);
  }
}

export async function DELETE(request: Request) {
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "coupon-image-cleanup", limit: 40, windowSeconds: 3600 });
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();
    const blobPath = url.searchParams.get("blobPath")?.trim();
    const thumbnailBlobPath = url.searchParams.get("thumbnailBlobPath")?.trim();
    const uploadIdValue = url.searchParams.get("uploadId")?.trim();
    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");

    await requireRoomMember(roomId, token.uid);
    const uploadId = uploadIdValue ? requireCouponUploadId(uploadIdValue) : null;
    const paths = uploadId
      ? couponUploadCandidatePaths(roomId, couponId, uploadId)
      : [requireCouponBlobPath(blobPath, roomId, couponId)];
    if (!uploadId && thumbnailBlobPath) paths.push(requireCouponBlobPath(thumbnailBlobPath, roomId, couponId));
    const coupon = await getAdminDb().doc(`rooms/${roomId}/coupons/${couponId}`).get();
    if (coupon.exists) {
      if (coupon.get("ownerUid") !== token.uid) {
        throw new ApiError(403, "등록자만 업로드 이미지를 정리할 수 있습니다.");
      }
      if (blobCleanupHasLiveReference(paths, coupon.get("imageBlobPath"), coupon.get("thumbnailBlobPath"))) {
        return json({ ok: true, cleanupPending: false, preserved: true });
      }
    }
    if (uploadId) {
      const cleanupJob = await enqueueBlobCleanup(
        roomId,
        couponId,
        paths,
        new Date(Date.now() + 10 * 60_000)
      );
      await cleanupJob.set({
        status: "pending",
        nextAttemptAt: Timestamp.fromMillis(Date.now() + 10 * 60_000),
        updatedAt: FieldValue.serverTimestamp()
      }, { merge: true });
      await del(paths).catch((error) => {
        console.error("immediate upload session cleanup deferred", { roomId, couponId, uploadId, error });
      });
      return json({ ok: true, cleanupPending: true, cleanupJobId: cleanupJob.id });
    }

    let cleanupJob = null;
    try {
      cleanupJob = await enqueueBlobCleanup(roomId, couponId, paths);
    } catch (error) {
      console.error("failed to persist abandoned upload cleanup", { roomId, couponId, error });
    }

    try {
      await del(paths);
      if (cleanupJob) {
        await cleanupJob.delete().catch((error) => {
          console.error("failed to remove completed abandoned upload cleanup", { roomId, couponId, error });
        });
      }
      return json({ ok: true, cleanupPending: false });
    } catch (error) {
      if (!cleanupJob) {
        cleanupJob = await enqueueBlobCleanup(roomId, couponId, paths).catch((enqueueError) => {
          console.error("failed to recover abandoned upload cleanup", { roomId, couponId, error: enqueueError });
          return null;
        });
      }
      if (!cleanupJob) throw error;
      return json({ ok: true, cleanupPending: true });
    }
  } catch (error) {
    return jsonError(error);
  }
}

export async function PATCH(request: Request) {
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "coupon-image-finalize", limit: 60, windowSeconds: 3600 });
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();
    const uploadId = requireCouponUploadId(url.searchParams.get("uploadId")?.trim());
    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");

    await requireRoomMember(roomId, token.uid);
    const coupon = await getAdminDb().doc(`rooms/${roomId}/coupons/${couponId}`).get();
    if (!coupon.exists) throw new ApiError(409, "쿠폰 저장이 아직 완료되지 않았습니다.");
    if (coupon.get("ownerUid") !== token.uid) {
      throw new ApiError(403, "등록자만 업로드 세션을 확정할 수 있습니다.");
    }
    const candidatePaths = couponUploadCandidatePaths(roomId, couponId, uploadId);
    const candidateSet = new Set(candidatePaths);
    const imageBlobPath = coupon.get("imageBlobPath");
    const thumbnailBlobPath = coupon.get("thumbnailBlobPath");
    if (!candidateSet.has(imageBlobPath) || !candidateSet.has(thumbnailBlobPath)) {
      throw new ApiError(409, "업로드 세션 경로가 현재 쿠폰 이미지와 일치하지 않습니다.");
    }

    await getAdminDb().doc(
      `blobCleanupQueue/${blobCleanupJobId(roomId, couponId, candidatePaths)}`
    ).delete();
    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
