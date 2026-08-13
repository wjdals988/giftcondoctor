import { FieldValue } from "firebase-admin/firestore";
import { requireRoomOwner, requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, readJson } from "@/lib/http";

export const runtime = "nodejs";

type Body = {
  roomId?: string;
  targetUid?: string;
};

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Body>(request);
    const roomId = body.roomId?.trim();
    const targetUid = body.targetUid?.trim();

    if (!roomId || !targetUid) {
      throw new ApiError(400, "roomId와 targetUid가 필요합니다.");
    }

    const room = await requireRoomOwner(roomId, token.uid);
    if (room.get("ownerUid") === targetUid) {
      throw new ApiError(400, "방장은 제거할 수 없습니다.");
    }

    const db = getAdminDb();
    const roomRef = db.doc(`rooms/${roomId}`);
    const memberRef = db.doc(`rooms/${roomId}/members/${targetUid}`);
    const membershipRef = db.doc(`users/${targetUid}/roomMemberships/${roomId}`);
    await db.runTransaction(async (transaction) => {
      const freshRoom = await transaction.get(roomRef);
      const members = await transaction.get(roomRef.collection("members"));
      const ownedCoupons = await transaction.get(
        roomRef.collection("coupons").where("ownerUid", "==", targetUid).limit(1)
      );

      if (!freshRoom.exists || !members.docs.some((item) => item.id === targetUid)) {
        throw new ApiError(404, "제거할 멤버를 찾을 수 없습니다.");
      }
      if (!ownedCoupons.empty) {
        throw new ApiError(409, "이 멤버가 등록한 쿠폰이 남아 있어 제거할 수 없습니다. 쿠폰을 먼저 정리해 주세요.");
      }

      transaction.delete(memberRef);
      transaction.delete(membershipRef);
      transaction.update(roomRef, {
        memberCount: Math.max(0, members.size - 1),
        updatedAt: FieldValue.serverTimestamp()
      });
    });

    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
