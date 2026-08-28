/**
 * 방을 가로지르는 쿠폰 검색.
 *
 * 앱의 검색창은 `RoomDashboard` 안에만 있어서, 방이 3개면 "커피 쿠폰 있었나?" 를
 * 확인하려고 방 3개를 각각 열어 각각 검색해야 한다. 매장 계산대 앞에서 할 수
 * 있는 동작이 아니다.
 *
 * 클라이언트가 collectionGroup 을 직접 쓸 수 없는 이유는 `expiringCoupons` 와
 * 같다. 쿠폰 문서에 멤버 식별자가 없고 규칙이 rooms/{roomId}/members/{uid} 존재로
 * 접근을 판정한다. 그래서 admin SDK 를 쓰는 서버가 방 목록을 먼저 읽고 그 범위에서만
 * 모은다.
 *
 * Firestore 는 부분 문자열 검색을 지원하지 않는다. 접두사 색인을 따로 만들지 않는
 * 이상 서버가 읽어서 메모리에서 거르는 수밖에 없다. 그 비용을 감당 가능하게
 * 만드는 장치가 세 가지다 — 최소 질의 길이, 방당 읽기 상한, 결과 상한.
 */

/** 이보다 짧은 질의는 받지 않는다. 한 글자로는 거의 모든 쿠폰이 걸려 읽기만 낭비된다. */
export const SEARCH_MIN_QUERY_LENGTH = 2;
/** 한 방에서 읽어올 최대 쿠폰 수. 방이 아무리 커도 읽기가 폭주하지 않게 묶는다. */
export const SEARCH_PER_ROOM_LIMIT = 300;
/** 돌려주는 최대 건수. 검색 결과를 끝까지 스크롤하는 사용은 이 앱의 사용 맥락이 아니다. */
export const SEARCH_RESULT_LIMIT = 50;

export type CouponSearchHit = {
  roomId: string;
  roomName: string;
  couponId: string;
  title: string;
  brand: string;
  expiresLocalDate: string;
  status: string;
};

export type CouponSearchResult = {
  query: string;
  coupons: CouponSearchHit[];
  /** 조회 대상이 된 방 수. 사용자가 결과 범위를 이해할 수 있어야 한다. */
  roomCount: number;
  /** 상한에 걸려 잘렸는지. 잘린 사실을 숨기면 "없다" 는 오해를 만든다. */
  truncated: boolean;
};

export type RawSearchCoupon = {
  roomId: string;
  roomName: string;
  couponId: string;
  title?: unknown;
  brand?: unknown;
  status?: unknown;
  visibility?: unknown;
  ownerUid?: unknown;
  expiresLocalDate?: unknown;
};

/**
 * 질의 문자열을 정규화한다.
 *
 * 공백을 접고 소문자로 낮춘다. 한글에는 대소문자가 없지만 브랜드명은 영문이 흔하다
 * ("starbucks" 로 "Starbucks" 를 찾을 수 있어야 한다).
 *
 * 길이가 모자라면 null 을 돌려준다. 예외가 아니라 null 인 이유는, 사용자가 검색창에
 * 한 글자를 입력한 상태가 오류가 아니기 때문이다. 화면은 그냥 아직 아무것도
 * 보여주지 않으면 된다.
 */
export function normalizeSearchQuery(raw: string | null): string | null {
  if (raw == null) return null;
  const normalized = raw.trim().replace(/\s+/g, " ").toLowerCase();
  if (normalized.length < SEARCH_MIN_QUERY_LENGTH) return null;
  return normalized;
}

/**
 * 제목·브랜드에 질의가 들어 있는지 본다.
 *
 * 앱 안 검색(`CouponListRules.matchesSearch`)과 같은 기준이다. 같은 단어로 검색했는데
 * 방 안에서와 방 밖에서 결과가 다르면 사용자는 어느 쪽을 믿어야 할지 모른다.
 */
export function matchesCouponQuery(coupon: RawSearchCoupon, normalizedQuery: string): boolean {
  const title = typeof coupon.title === "string" ? coupon.title.toLowerCase() : "";
  const brand = typeof coupon.brand === "string" ? coupon.brand.toLowerCase() : "";
  return title.includes(normalizedQuery) || brand.includes(normalizedQuery);
}

/**
 * 서버가 읽어온 쿠폰을 검색 결과로 거른다.
 *
 * 제외 조건은 두 가지다.
 *  - 휴지통 상태(deleted/purging): 사용자에게는 이미 지운 쿠폰이다
 *  - 남이 등록한 비공개 쿠폰: visibility=private 은 등록자만 볼 수 있다
 *
 * `used`/`expired` 는 **남긴다.** 만료 임박 요약과 달리 검색은 "지금 쓸 것" 만
 * 찾는 도구가 아니다. "그 쿠폰 썼던가?" 를 확인하려는 질의가 실제로 많고, 그때
 * 결과가 비면 사용자는 등록 자체를 안 했다고 오해한다.
 */
export function selectSearchResults(
  raw: RawSearchCoupon[],
  uid: string,
  normalizedQuery: string,
  limit: number = SEARCH_RESULT_LIMIT
): { coupons: CouponSearchHit[]; truncated: boolean } {
  const picked: CouponSearchHit[] = [];
  for (const item of raw) {
    const status = typeof item.status === "string" ? item.status : "active";
    if (status === "deleted" || status === "purging") continue;
    if (item.visibility === "private" && item.ownerUid !== uid) continue;
    if (!matchesCouponQuery(item, normalizedQuery)) continue;

    const expires = typeof item.expiresLocalDate === "string" ? item.expiresLocalDate : "";
    picked.push({
      roomId: item.roomId,
      roomName: item.roomName,
      couponId: item.couponId,
      title:
        typeof item.title === "string" && item.title.trim() !== "" ? item.title : "이름 없는 쿠폰",
      brand: typeof item.brand === "string" ? item.brand : "",
      expiresLocalDate: expires,
      status
    });
  }

  // 아직 쓸 수 있는 것을 먼저, 그 안에서는 만료가 급한 순으로. 끝난 쿠폰은 뒤로
  // 밀되 지우지는 않는다. 같은 조건이면 제목순으로 안정 정렬해 결과가 요청마다
  // 흔들리지 않게 한다.
  picked.sort((a, b) => {
    const rankA = searchRank(a.status);
    const rankB = searchRank(b.status);
    if (rankA !== rankB) return rankA - rankB;
    if (a.expiresLocalDate !== b.expiresLocalDate) {
      return a.expiresLocalDate < b.expiresLocalDate ? -1 : 1;
    }
    return a.title.localeCompare(b.title, "ko");
  });

  return { coupons: picked.slice(0, limit), truncated: picked.length > limit };
}

function searchRank(status: string): number {
  if (status === "used" || status === "expired") return 1;
  return 0;
}
