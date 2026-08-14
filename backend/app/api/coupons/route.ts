import { assertPublicCouponDeleteAllowed, requireCouponAccess, requireUser } from "@/lib/auth";
import { requireCouponBlobPath } from "@/lib/blobPath";
import { enqueueBlobCleanup } from "@/lib/blobCleanupQueue";
import { deleteCouponImages } from "@/lib/couponImageStorage";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { deleteDocumentRefs } from "@/lib/firestoreDelete";
import { ApiError, json, jsonError } from "@/lib/http";

export const runtime = "nodejs";

export async function DELETE(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);
    const roomId = url.searchParams.get("roomId")?.trim();
    const couponId = url.searchParams.get("couponId")?.trim();

    if (!roomId || !couponId) {
      throw new ApiError(400, "roomId와 couponId가 필요합니다.");
    }

    const coupon = await requireCouponAccess(roomId, couponId, token.uid);
    await assertPublicCouponDeleteAllowed(roomId, token.uid, coupon);

    const blobPaths = [requireCouponBlobPath(coupon.get("imageBlobPath"), roomId, couponId)];
    const thumbnailBlobPath = coupon.get("thumbnailBlobPath");
    if (thumbnailBlobPath) {
      blobPaths.push(requireCouponBlobPath(thumbnailBlobPath, roomId, couponId));
    }
    const cleanupJob = await enqueueBlobCleanup(roomId, couponId, blobPaths);
    const db = getAdminDb();
    const comments = await db.collection(`rooms/${roomId}/coupons/${couponId}/comments`).get();
    await deleteDocumentRefs(db, [
      ...comments.docs.map((doc) => doc.ref),
      db.doc(`rooms/${roomId}/coupons/${couponId}`)
    ]);

    let cleanupPending = false;
    try {
      await deleteCouponImages(blobPaths);
      await cleanupJob.delete().catch((error) => {
        console.error("failed to remove completed coupon cleanup job", { roomId, couponId, error });
      });
    } catch {
      cleanupPending = true;
    }

    return json({ ok: true, cleanupPending });
  } catch (error) {
    return jsonError(error);
  }
}
