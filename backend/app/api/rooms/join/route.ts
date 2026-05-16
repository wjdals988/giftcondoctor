import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { requireUser, userProfile } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError, readJson } from "@/lib/http";
import { verifyRoomPassword } from "@/lib/roomPassword";
import { upsertUserProfile } from "@/lib/users";

export const runtime = "nodejs";

type Body = {
  inviteCode?: string;
  roomId?: string;
  password?: string;
};

export async function POST(request: Request) {
  try {
    const token = await requireUser(request);
    const body = await readJson<Body>(request);
    const inviteCode = body.inviteCode?.trim().toUpperCase();
    const publicRoomId = body.roomId?.trim();
    const password = body.password?.trim() ?? "";

    await upsertUserProfile(token);

    const db = getAdminDb();
    const room = publicRoomId
      ? await findPublicRoomByPassword(db, publicRoomId, password)
      : await findRoomByInviteCode(db, inviteCode);

    if (!room) {
      throw new ApiError(404, publicRoomId ? "방을 찾을 수 없거나 비밀번호가 올바르지 않습니다." : "초대코드가 없거나 만료되었습니다.");
    }

    const roomId = room.id;
    const profile = userProfile(token);
    const now = FieldValue.serverTimestamp();
    const memberRef = db.doc(`rooms/${roomId}/members/${token.uid}`);
    const alreadyMember = await memberRef.get();

    const batch = db.batch();
    batch.set(memberRef, {
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
    if (!alreadyMember.exists) {
      batch.update(room.ref, {
        memberCount: FieldValue.increment(1),
        updatedAt: now
      });
    }
    await batch.commit();

    return json({ roomId, name: room.get("name") });
  } catch (error) {
    return jsonError(error);
  }
}

async function findRoomByInviteCode(db: FirebaseFirestore.Firestore, inviteCode?: string) {
  if (!inviteCode) throw new ApiError(400, "초대코드를 입력해 주세요.");

  const rooms = await db
    .collection("rooms")
    .where("inviteCode", "==", inviteCode)
    .limit(10)
    .get();

  const inviteNow = Timestamp.now();
  return rooms.docs.find((doc) => {
    const expiresAt = doc.get("inviteExpiresAt");
    return expiresAt instanceof Timestamp && expiresAt.toMillis() > inviteNow.toMillis();
  });
}

async function findPublicRoomByPassword(db: FirebaseFirestore.Firestore, roomId: string, password: string) {
  if (!password) throw new ApiError(400, "방 비밀번호를 입력해 주세요.");

  const room = await db.collection("rooms").doc(roomId).get();
  if (!room.exists || room.get("isPublic") !== true) return null;
  const secret = await db.doc(`roomSecrets/${roomId}`).get();
  if (!secret.exists) return null;

  const ok = verifyRoomPassword({
    password,
    passwordHash: secret.get("passwordHash"),
    passwordSalt: secret.get("passwordSalt"),
    passwordIterations: secret.get("passwordIterations")
  });
  return ok ? room : null;
}
