import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { requireUser, userProfile } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, readJson } from "@/lib/http";
import { upsertUserProfile } from "@/lib/users";

export const runtime = "nodejs";

type Body = {
  inviteCode?: string;
};

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Body>(request);
    const inviteCode = body.inviteCode?.trim().toUpperCase();

    if (!inviteCode) {
      throw new ApiError(400, "초대코드를 입력해 주세요.");
    }

    await upsertUserProfile(token);

    const db = getAdminDb();
    const rooms = await db
      .collection("rooms")
      .where("inviteCode", "==", inviteCode)
      .where("inviteExpiresAt", ">", Timestamp.now())
      .limit(1)
      .get();

    if (rooms.empty) {
      throw new ApiError(404, "초대코드가 없거나 만료되었습니다.");
    }

    const room = rooms.docs[0];
    const roomId = room.id;
    const profile = userProfile(token);
    const now = FieldValue.serverTimestamp();

    const batch = db.batch();
    batch.set(db.doc(`rooms/${roomId}/members/${token.uid}`), {
      role: "member",
      displayName: profile.displayName,
      joinedAt: now,
      notificationEnabled: true,
      notificationMode: null,
      notificationDays: null
    }, { merge: true });
    batch.set(db.doc(`users/${token.uid}/roomMemberships/${roomId}`), {
      roomId,
      name: room.get("name"),
      role: room.get("ownerUid") === token.uid ? "owner" : "member",
      joinedAt: now,
      updatedAt: now
    }, { merge: true });
    await batch.commit();

    return json({ roomId, name: room.get("name") });
  } catch (error) {
    return jsonError(error);
  }
}
