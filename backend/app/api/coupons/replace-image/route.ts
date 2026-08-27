import { FieldValue } from "firebase-admin/firestore";
import { requireCouponAccess, requireUser } from "@/lib/auth";
import { requireCouponBlobPath } from "@/lib/blobPath";
import { enqueueBlobCleanup } from "@/lib/blobCleanupQueue";
import { deleteCouponImages, storeCouponImage } from "@/lib/couponImageStorage";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, serverTiming } from "@/lib/http";
import { enforceUserRateLimit } from "@/lib/rateLimit";

export const runtime = "nodejs";

export async function POST(request: Request) {
  const startedAt = performance.now();
  try {
    const token = await requireUser(request);
    await enforceUserRateLimit(token.uid, { action: "coupon-image-replace", limit: 20, windowSeconds: 3600 });
    const form = await request.formData();
    const roomId = String(form.get("roomId") ?? "").trim();
    const couponId = String(form.get("couponId") ?? "").trim();
    const image = form.get("image");

    if (!roomId || !couponId) throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    if (!(image instanceof File)) throw new ApiError(400, "이미지 파일이 필요합니다.");

    const coupon = await requireCouponAccess(roomId, couponId, token.uid);
    if (coupon.get("ownerUid") !== token.uid) {
      throw new ApiError(403, "쿠폰 등록자만 이미지를 교체할 수 있습니다.");
    }

    const oldBlobPath = requireCouponBlobPath(coupon.get("imageBlobPath"), roomId, couponId);
    const oldThumbnailValue = coupon.get("thumbnailBlobPath");
    const oldThumbnailBlobPath = oldThumbnailValue
      ? requireCouponBlobPath(oldThumbnailValue, roomId, couponId)
      : null;
    const storageStartedAt = performance.now();
    const stored = await storeCouponImage(roomId, couponId, image);
    const commitStartedAt = performance.now();
    const db = getAdminDb();

    try {
      await db.runTransaction(async (transaction) => {
        const current = await transaction.get(coupon.ref);
        if (!current.exists) throw new ApiError(404, "쿠폰을 찾을 수 없습니다.");
        if (current.get("ownerUid") !== token.uid) {
          throw new ApiError(403, "쿠폰 등록자만 이미지를 교체할 수 있습니다.");
        }
        if (
          current.get("imageBlobPath") !== oldBlobPath ||
          (current.get("thumbnailBlobPath") ?? null) !== oldThumbnailBlobPath
        ) {
          throw new ApiError(409, "다른 기기에서 이미지가 먼저 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
        transaction.update(coupon.ref, {
          imageBlobPath: stored.blobPath,
          thumbnailBlobPath: stored.thumbnailBlobPath,
          imageWidth: stored.imageWidth,
          imageHeight: stored.imageHeight,
          barcodeValue: FieldValue.delete(),
          barcodeFormat: FieldValue.delete(),
          updatedAt: FieldValue.serverTimestamp()
        });
      });
    } catch (error) {
      await deleteCouponImages([stored.blobPath, stored.thumbnailBlobPath]).catch(() => undefined);
      throw error;
    }

    const cleanupStartedAt = performance.now();
    const oldPaths = [oldBlobPath, oldThumbnailBlobPath].filter((path): path is string => path !== null);
    let cleanupJob = null;
    try {
      cleanupJob = await enqueueBlobCleanup(roomId, couponId, oldPaths);
    } catch (error) {
      console.error("failed to persist blob cleanup job", { roomId, couponId, error });
    }

    let cleanupPending = false;
    try {
      await deleteCouponImages(oldPaths);
      if (cleanupJob) {
        await cleanupJob.delete().catch((error) => {
          console.error("failed to remove completed blob cleanup job", { roomId, couponId, error });
        });
      }
    } catch (error) {
      cleanupPending = true;
      if (!cleanupJob) {
        await enqueueBlobCleanup(roomId, couponId, oldPaths).catch((enqueueError) => {
          console.error("failed to recover blob cleanup job", { roomId, couponId, error: enqueueError });
        });
      }
    }
    const finishedAt = performance.now();
    return json({ ...stored, cleanupPending }, {
      headers: {
        "Server-Timing": serverTiming([
          { name: "prepare", durationMs: storageStartedAt - startedAt },
          { name: "blob-store", durationMs: commitStartedAt - storageStartedAt },
          { name: "commit", durationMs: cleanupStartedAt - commitStartedAt },
          { name: "cleanup", durationMs: finishedAt - cleanupStartedAt }
        ])
      }
    });
  } catch (error) {
    return jsonError(error);
  }
}
