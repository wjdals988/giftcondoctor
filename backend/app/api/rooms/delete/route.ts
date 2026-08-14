import type { DocumentReference } from "firebase-admin/firestore";
import { requireRoomOwner, requireUser } from "@/lib/auth";
import { requireCouponBlobPath } from "@/lib/blobPath";
import { enqueueBlobCleanup } from "@/lib/blobCleanupQueue";
import { deleteCouponImages } from "@/lib/couponImageStorage";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { deleteDocumentRefs } from "@/lib/firestoreDelete";
import { ApiError, json, jsonError, readJson } from "@/lib/http";

export const runtime = "nodejs";

type Body = {
  roomId?: string;
};

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Body>(request);
    const roomId = body.roomId?.trim();
    if (!roomId) throw new ApiError(400, "roomId가 필요합니다.");
    if (roomId === "push-test-room") throw new ApiError(400, "푸시 테스트방은 삭제할 수 없습니다.");

    await requireRoomOwner(roomId, token.uid);

    const db = getAdminDb();
    const refs: DocumentReference[] = [];
    const blobPaths: string[] = [];
    const cleanupTargets: Array<{ couponId: string; paths: string[] }> = [];

    const coupons = await db.collection(`rooms/${roomId}/coupons`).get();
    for (const coupon of coupons.docs) {
      const couponPaths = [requireCouponBlobPath(coupon.get("imageBlobPath"), roomId, coupon.id)];
      const thumbnailBlobPath = coupon.get("thumbnailBlobPath");
      if (thumbnailBlobPath) {
        couponPaths.push(requireCouponBlobPath(thumbnailBlobPath, roomId, coupon.id));
      }
      blobPaths.push(...couponPaths);
      cleanupTargets.push({ couponId: coupon.id, paths: couponPaths });

      const comments = await coupon.ref.collection("comments").get();
      comments.docs.forEach((comment) => refs.push(comment.ref));
      refs.push(coupon.ref);
    }

    const members = await db.collection(`rooms/${roomId}/members`).get();
    members.docs.forEach((member) => {
      refs.push(member.ref);
      refs.push(db.doc(`users/${member.id}/roomMemberships/${roomId}`));
    });

    const logs = await db.collection("notificationLogs").where("roomId", "==", roomId).get();
    logs.docs.forEach((log) => refs.push(log.ref));

    const deliveries = await db.collection("notificationOutbox").where("roomId", "==", roomId).get();
    deliveries.docs.forEach((delivery) => refs.push(delivery.ref));

    refs.push(db.doc(`roomSecrets/${roomId}`));
    refs.push(db.doc(`rooms/${roomId}`));

    const cleanupJobs = await Promise.all(
      cleanupTargets.map((target) => enqueueBlobCleanup(roomId, target.couponId, target.paths))
    );
    await deleteDocumentRefs(db, refs);

    let cleanupPending = false;
    if (blobPaths.length > 0) {
      try {
        await deleteCouponImages(blobPaths);
        await Promise.all(cleanupJobs.map((job) => job.delete().catch((error) => {
          console.error("failed to remove completed room cleanup job", { roomId, jobId: job.id, error });
        })));
      } catch {
        cleanupPending = true;
      }
    }

    return json({
      ok: true,
      deletedCoupons: coupons.size,
      deletedMembers: members.size,
      cleanupPending
    });
  } catch (error) {
    return jsonError(error);
  }
}
