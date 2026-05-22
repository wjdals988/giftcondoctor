import { del } from "@vercel/blob";
import { assertPublicCouponDeleteAllowed, requireCouponAccess, requireUser } from "@/lib/auth";
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

    const blobPath = coupon.get("imageBlobPath");
    const db = getAdminDb();
    const comments = await db.collection(`rooms/${roomId}/coupons/${couponId}/comments`).get();
    await deleteDocumentRefs(db, [
      ...comments.docs.map((doc) => doc.ref),
      db.doc(`rooms/${roomId}/coupons/${couponId}`)
    ]);

    if (typeof blobPath === "string" && blobPath.length > 0) {
      await del(blobPath);
    }

    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
