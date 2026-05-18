import type { DecodedIdToken } from "firebase-admin/auth";
import { FieldValue } from "firebase-admin/firestore";
import { getAdminDb } from "./firebaseAdmin";
import { userProfile } from "./auth";
import { upsertUserProfile } from "./users";

export const PUSH_TEST_ROOM_ID = "push-test-room";
export const PUSH_TEST_ROOM_NAME = "푸시 알림 테스트방";

export async function ensurePushTestRoom() {
  const db = getAdminDb();
  const roomRef = db.doc(`rooms/${PUSH_TEST_ROOM_ID}`);
  const room = await roomRef.get();
  const now = FieldValue.serverTimestamp();

  if (!room.exists) {
    await roomRef.set({
      name: PUSH_TEST_ROOM_NAME,
      ownerUid: "system",
      isPublic: false,
      hasPassword: false,
      memberCount: 0,
      inviteCode: null,
      inviteExpiresAt: null,
      defaultNotificationMode: "careful",
      defaultNotificationDays: [7, 5, 3, 2, 1, 0],
      roomType: "pushTest",
      dailyTestPushEnabled: true,
      createdAt: now,
      updatedAt: now
    });
  } else {
    await roomRef.set(
      {
        name: PUSH_TEST_ROOM_NAME,
        ownerUid: "system",
        isPublic: false,
        hasPassword: false,
        roomType: "pushTest",
        dailyTestPushEnabled: true,
        updatedAt: now
      },
      { merge: true }
    );
  }

  return roomRef;
}

export async function joinPushTestRoom(token: DecodedIdToken) {
  await upsertUserProfile(token);

  const db = getAdminDb();
  const roomRef = await ensurePushTestRoom();
  const profile = userProfile(token);
  const now = FieldValue.serverTimestamp();
  const memberRef = roomRef.collection("members").doc(token.uid);
  const alreadyMember = await memberRef.get();

  const batch = db.batch();
  batch.set(
    memberRef,
    {
      role: "member",
      displayName: profile.displayName,
      joinedAt: alreadyMember.exists ? alreadyMember.get("joinedAt") ?? now : now,
      notificationEnabled: true,
      notificationMode: null,
      notificationDays: null,
      updatedAt: now
    },
    { merge: true }
  );
  batch.set(
    db.doc(`users/${token.uid}/roomMemberships/${PUSH_TEST_ROOM_ID}`),
    {
      roomId: PUSH_TEST_ROOM_ID,
      name: PUSH_TEST_ROOM_NAME,
      role: "member",
      joinedAt: alreadyMember.exists ? alreadyMember.get("joinedAt") ?? now : now,
      updatedAt: now,
      roomType: "pushTest"
    },
    { merge: true }
  );
  if (!alreadyMember.exists) {
    batch.update(roomRef, {
      memberCount: FieldValue.increment(1),
      updatedAt: now
    });
  }
  await batch.commit();

  return { roomId: PUSH_TEST_ROOM_ID, name: PUSH_TEST_ROOM_NAME };
}
