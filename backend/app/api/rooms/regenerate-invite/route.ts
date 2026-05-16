import { FieldValue } from "firebase-admin/firestore";
import { requireRoomOwner, requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, readJson } from "@/lib/http";
import { createUniqueInviteCode, inviteExpiresAt } from "@/lib/invite";

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

    await requireRoomOwner(roomId, token.uid);

    const inviteCode = await createUniqueInviteCode();
    await getAdminDb().doc(`rooms/${roomId}`).update({
      inviteCode,
      inviteExpiresAt: inviteExpiresAt(24),
      updatedAt: FieldValue.serverTimestamp()
    });

    return json({ inviteCode });
  } catch (error) {
    return jsonError(error);
  }
}
