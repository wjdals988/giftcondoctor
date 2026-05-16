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
    const batch = db.batch();
    batch.delete(db.doc(`rooms/${roomId}/members/${token.uid}`));
    batch.delete(db.doc(`users/${token.uid}/roomMemberships/${roomId}`));
    batch.update(db.doc(`rooms/${roomId}`), { updatedAt: FieldValue.serverTimestamp() });
    await batch.commit();

    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
