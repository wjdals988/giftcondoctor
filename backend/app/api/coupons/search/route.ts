import { requireUser } from "@/lib/auth";
import { getAdminDb } from "@/lib/firebaseAdmin";
import { ApiError, json, jsonError } from "@/lib/http";
import {
  SEARCH_MIN_QUERY_LENGTH,
  SEARCH_PER_ROOM_LIMIT,
  normalizeSearchQuery,
  selectSearchResults,
  type CouponSearchResult,
  type RawSearchCoupon
} from "@/lib/couponSearch";

export const runtime = "nodejs";

/** 한 번에 훑는 방 수 상한. `expiring` 과 같은 값을 쓴다. */
const MAX_ROOMS = 30;

/**
 * 사용자의 쿠폰을 방을 가로질러 검색한다.
 *
 * 구현이 `coupons/expiring` 과 거의 같다. 방 멤버십을 먼저 읽고, 방마다 쿠폰을
 * 병렬로 읽은 뒤, 메모리에서 거른다. 다른 점은 날짜 조건 대신 문자열 조건이라
 * Firestore 쿼리로 좁힐 수 없다는 것뿐이다. 그래서 읽기 상한이 이 경로에서
 * 더 중요하다.
 */
export async function GET(request: Request) {
  try {
    const token = await requireUser(request);
    const url = new URL(request.url);

    const query = normalizeSearchQuery(url.searchParams.get("q"));
    if (query == null) {
      throw new ApiError(400, `검색어는 ${SEARCH_MIN_QUERY_LENGTH}글자 이상이어야 합니다.`);
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

    const perRoom = await Promise.all(
      rooms.map(async (room) => {
        const snapshot = await db
          .collection(`rooms/${room.roomId}/coupons`)
          .limit(SEARCH_PER_ROOM_LIMIT)
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
        })) satisfies RawSearchCoupon[];
      })
    );

    const raw: RawSearchCoupon[] = [];
    let roomHitReadLimit = false;
    for (const group of perRoom) {
      if (group.length >= SEARCH_PER_ROOM_LIMIT) roomHitReadLimit = true;
      raw.push(...group);
    }

    const { coupons, truncated } = selectSearchResults(raw, token.uid, query);
    const result: CouponSearchResult = {
      query,
      coupons,
      roomCount: rooms.length,
      // 방 읽기 상한과 방 수 상한도 "다 보지 못했다" 는 같은 사실이다.
      truncated: truncated || roomHitReadLimit || membershipSnapshot.size >= MAX_ROOMS
    };
    return json(result);
  } catch (error) {
    return jsonError(error);
  }
}
