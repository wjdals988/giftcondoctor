import { del } from "@vercel/blob";
import type { DocumentReference } from "firebase-admin/firestore";
import { requireRoomOwner, requireUser } from "@/lib/auth";
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

    const coupons = await db.collection(`rooms/${roomId}/coupons`).get();
    for (const coupon of coupons.docs) {
      const blobPath = coupon.get("imageBlobPath");
      if (typeof blobPath === "string" && blobPath.length > 0) blobPaths.push(blobPath);

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

    refs.push(db.doc(`roomSecrets/${roomId}`));
    refs.push(db.doc(`rooms/${roomId}`));

    await deleteDocumentRefs(db, refs);

    if (blobPaths.length > 0) {
      await del(blobPaths);
    }

    return json({ ok: true, deletedCoupons: coupons.size, deletedMembers: members.size });
  } catch (error) {
    return jsonError(error);
  }
}
