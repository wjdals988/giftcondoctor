import { FieldValue } from "firebase-admin/firestore";
import { requireRoomMember, requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
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

    const member = await requireRoomMember(roomId, token.uid);
    if (member.get("role") === "owner") {
      throw new ApiError(400, "방장은 MVP에서 방을 나갈 수 없습니다. 먼저 v1.1의 방장 이전 기능이 필요합니다.");
    }

    const db = getAdminDb();
    const roomRef = db.doc(`rooms/${roomId}`);
    const memberRef = db.doc(`rooms/${roomId}/members/${token.uid}`);
    const membershipRef = db.doc(`users/${token.uid}/roomMemberships/${roomId}`);
    await db.runTransaction(async (transaction) => {
      const room = await transaction.get(roomRef);
      const members = await transaction.get(roomRef.collection("members"));
      const ownedCoupons = await transaction.get(
        roomRef.collection("coupons").where("ownerUid", "==", token.uid).limit(1)
      );

      if (!room.exists || !members.docs.some((item) => item.id === token.uid)) {
        throw new ApiError(404, "방 멤버 정보를 찾을 수 없습니다.");
      }
      if (!ownedCoupons.empty) {
        throw new ApiError(409, "등록한 쿠폰이 남아 있어 방을 나갈 수 없습니다. 쿠폰을 먼저 정리해 주세요.");
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
