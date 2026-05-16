import { FieldValue } from "firebase-admin/firestore";
import { requireUser, userProfile } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, readJson } from "@/lib/http";
import { createUniqueInviteCode, inviteExpiresAt } from "@/lib/invite";
import { upsertUserProfile } from "@/lib/users";

export const runtime = "nodejs";

type Body = {
  name?: string;
};

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Body>(request);
    const name = body.name?.trim();

    if (!name || name.length > 40) {
      throw new ApiError(400, "방 이름은 1~40자로 입력해 주세요.");
    }

    await upsertUserProfile(token);

    const db = getAdminDb();
    const roomRef = db.collection("rooms").doc();
    const memberRef = roomRef.collection("members").doc(token.uid);
    const userRoomRef = db.doc(`users/${token.uid}/roomMemberships/${roomRef.id}`);
    const inviteCode = await createUniqueInviteCode();
    const now = FieldValue.serverTimestamp();
    const profile = userProfile(token);

    const batch = db.batch();
    batch.set(roomRef, {
      name,
      ownerUid: token.uid,
      inviteCode,
      inviteExpiresAt: inviteExpiresAt(24),
      defaultNotificationMode: "basic",
      defaultNotificationDays: [7, 3, 1, 0],
      createdAt: now,
      updatedAt: now
    });
    batch.set(memberRef, {
      role: "owner",
      displayName: profile.displayName,
      joinedAt: now,
      notificationEnabled: true,
      notificationMode: null,
      notificationDays: null
    });
    batch.set(userRoomRef, {
      roomId: roomRef.id,
      name,
      role: "owner",
      joinedAt: now,
      updatedAt: now
    });
    await batch.commit();

    return json({ roomId: roomRef.id, inviteCode });
  } catch (error) {
    return jsonError(error);
  }
}
