import type { DecodedIdToken } from "firebase-admin/auth";
import type { DocumentSnapshot } from "firebase-admin/firestore";
import { getAdminAuth, getAdminDb } from "./firebaseAdmin";
import { ApiError } from "./http";

export async function requireUser(request: Request): Promise<DecodedIdToken> {
  const header = request.headers.get("authorization") ?? "";
  const match = header.match(/^Bearer (.+)$/);
  if (!match) {
    throw new ApiError(401, "Firebase ID token이 필요합니다.");
  }

  try {
    return await getAdminAuth().verifyIdToken(match[1]);
  } catch {
    throw new ApiError(401, "Firebase ID token이 유효하지 않습니다.");
  }
}

export async function requireRoomMember(roomId: string, uid: string) {
  const db = getAdminDb();
  const memberRef = db.doc(`rooms/${roomId}/members/${uid}`);
  const member = await memberRef.get();
  if (!member.exists) {
    throw new ApiError(403, "방 멤버만 접근할 수 있습니다.");
  }
  return member;
}

export async function requireRoomOwner(roomId: string, uid: string) {
  const db = getAdminDb();
  const room = await db.doc(`rooms/${roomId}`).get();
  if (!room.exists) {
    throw new ApiError(404, "방을 찾을 수 없습니다.");
  }

  if (room.get("ownerUid") !== uid) {
    throw new ApiError(403, "방장만 실행할 수 있습니다.");
  }

  return room;
}

export async function requireCouponAccess(roomId: string, couponId: string, uid: string) {
  await requireRoomMember(roomId, uid);

  const db = getAdminDb();
  const coupon = await db.doc(`rooms/${roomId}/coupons/${couponId}`).get();
  if (!coupon.exists) {
    throw new ApiError(404, "쿠폰을 찾을 수 없습니다.");
  }

  const visibility = coupon.get("visibility") ?? "room";
  const ownerUid = coupon.get("ownerUid");
  if (visibility === "private" && ownerUid !== uid) {
    throw new ApiError(403, "비공개 쿠폰은 등록자만 접근할 수 있습니다.");
  }

  return coupon;
}

export function userProfile(token: DecodedIdToken) {
  return {
    displayName: token.name ?? token.email ?? "이름 없음",
    email: token.email ?? null,
    photoUrl: token.picture ?? null
  };
}

export async function assertPublicCouponDeleteAllowed(
  roomId: string,
  uid: string,
  coupon: DocumentSnapshot
) {
  const visibility = coupon.get("visibility") ?? "room";
  const ownerUid = coupon.get("ownerUid");

  if (visibility === "private") {
    if (ownerUid !== uid) {
      throw new ApiError(403, "비공개 쿠폰은 등록자만 삭제할 수 있습니다.");
    }
    return;
  }

  const room = await getAdminDb().doc(`rooms/${roomId}`).get();
  if (ownerUid !== uid && room.get("ownerUid") !== uid) {
    throw new ApiError(403, "쿠폰 등록자 또는 방장만 삭제할 수 있습니다.");
  }
}
