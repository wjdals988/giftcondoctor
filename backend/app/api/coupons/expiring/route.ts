import { requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError } from "@/lib/http";
import { seoulLocalDate } from "@/lib/reminders";
import {
  EXPIRING_SOON_LIMIT,
  parseExpiringDays,
  selectExpiringCoupons,
  type ExpiringCouponsResult,
  type RawCoupon
} from "@/lib/expiringCoupons";

export const runtime = "nodejs";

/** 한 번에 훑는 방 수 상한. 방이 아주 많은 계정에서 응답이 무한정 길어지지 않게 한다. */
const MAX_ROOMS = 30;

/**
 * 사용자의 만료 임박 쿠폰을 방을 가로질러 모은다.
 *
 * 클라이언트가 직접 collectionGroup 을 쓸 수 없어 서버가 대신 모은다. 쿠폰 문서에
 * 멤버 식별자가 없고 Firestore 규칙이 rooms/{roomId}/members/{uid} 존재로 접근을
 * 판정하기 때문이다.
 *
 * 방마다 쿼리를 나눠 던지는 이유도 같다. collectionGroup 으로 한 번에 긁으면 사용자가
 * 속하지 않은 방의 쿠폰까지 읽게 되므로, 멤버십을 먼저 읽고 그 범위로 제한한다.
 */
export async function GET(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);

    let days: number;
    try {
      days = parseExpiringDays(url.searchParams.get("days"));
    } catch (error) {
      throw new ApiError(400, error instanceof Error ? error.message : "days가 올바르지 않습니다.");
    }

    const db = getAdminDb();
    const membershipSnapshot = await db
      .collection(`users/${token.uid}/roomMemberships`)
      .limit(MAX_ROOMS)
      .get();

    const rooms = membershipSnapshot.docs.map((doc) => ({
      roomId: doc.id,
      roomName: typeof doc.get("name") === "string" ? (doc.get("name") as string) : "이름 없는 방"
    }));

    const today = seoulLocalDate();
    const raw: RawCoupon[] = [];

    // 방 단위 쿼리를 병렬로 던진다. 방 수가 MAX_ROOMS 로 묶여 있어 폭주하지 않는다.
    const perRoom = await Promise.all(
      rooms.map(async (room) => {
        const snapshot = await db
          .collection(`rooms/${room.roomId}/coupons`)
          .where("expiresLocalDate", "<=", addDays(today, days))
          .limit(EXPIRING_SOON_LIMIT)
          .get();
        return snapshot.docs.map((doc) => ({
          roomId: room.roomId,
          roomName: room.roomName,
          couponId: doc.id,
          title: doc.get("title"),
          brand: doc.get("brand"),
          status: doc.get("status"),
          visibility: doc.get("visibility"),
          ownerUid: doc.get("ownerUid"),
          expiresLocalDate: doc.get("expiresLocalDate")
        })) satisfies RawCoupon[];
      })
    );
    for (const group of perRoom) raw.push(...group);

    const { coupons, truncated } = selectExpiringCoupons(raw, token.uid, today, days);
    const result: ExpiringCouponsResult = {
      days,
      coupons,
      roomCount: rooms.length,
      truncated: truncated || membershipSnapshot.size >= MAX_ROOMS
    };
    return json(result);
  } catch (error) {
    return jsonError(error);
  }
}

function addDays(localDate: string, days: number) {
  const [year, month, day] = localDate.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}
