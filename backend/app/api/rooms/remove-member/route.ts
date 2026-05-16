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
    const batch = db.batch();
    batch.delete(db.doc(`rooms/${roomId}/members/${targetUid}`));
    batch.delete(db.doc(`users/${targetUid}/roomMemberships/${roomId}`));
    batch.update(db.doc(`rooms/${roomId}`), { updatedAt: FieldValue.serverTimestamp() });
    await batch.commit();

    return json({ ok: true });
  } catch (error) {
    return jsonError(error);
  }
}
