/**
 * 방을 가로지르는 "만료 임박 쿠폰" 조회.
 *
 * 앱에는 방 무관 전체 쿠폰 화면이 없어서, 방이 여러 개면 지금 써야 할 쿠폰을 찾으려고
 * 방을 순회해야 한다. 반면 서버는 이미 cron 에서 collectionGroup 으로 전체 만료 쿠폰을
 * 계산해 푸시를 보낸다. 즉 "당신의 만료 임박 쿠폰" 이라는 개념은 서버에 이미 있고
 * 앱에 그 화면만 없었다.
 *
 * 클라이언트가 직접 collectionGroup 을 쓸 수는 없다. 쿠폰 문서에 멤버 식별자가 없고
 * Firestore 규칙이 rooms/{roomId}/members/{uid} 존재 여부로 접근을 판정하기 때문이다.
 * 그래서 admin SDK 를 쓰는 서버가 사용자의 방 목록을 먼저 읽고 그 범위에서만 모은다.
 */

export const EXPIRING_SOON_DEFAULT_DAYS = 7;
export const EXPIRING_SOON_MAX_DAYS = 60;
/** 한 번에 돌려주는 최대 건수. 요약 화면이므로 전체를 내려줄 이유가 없다. */
export const EXPIRING_SOON_LIMIT = 50;

export type ExpiringCoupon = {
  roomId: string;
  roomName: string;
  couponId: string;
  title: string;
  brand: string;
  expiresLocalDate: string;
  daysLeft: number;
};

export type ExpiringCouponsResult = {
  days: number;
  coupons: ExpiringCoupon[];
  /** 조회 대상이 된 방 수. 사용자가 결과 범위를 이해할 수 있어야 한다. */
  roomCount: number;
  /** limit 에 걸려 잘렸는지. 잘린 사실을 숨기면 "전부 봤다" 는 오해를 만든다. */
  truncated: boolean;
};

export function parseExpiringDays(raw: string | null): number {
  if (raw == null || raw.trim() === "") return EXPIRING_SOON_DEFAULT_DAYS;
  const parsed = Number(raw);
  if (!Number.isInteger(parsed) || parsed < 0) {
    throw new Error("days는 0 이상의 정수여야 합니다.");
  }
  return Math.min(parsed, EXPIRING_SOON_MAX_DAYS);
}

/** `YYYY-MM-DD` 를 UTC 기준 일수로 바꾼다. 시각 성분이 없으므로 시간대 영향이 없다. */
function toEpochDay(date: string): number | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(date);
  if (!match) return null;
  const ms = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]));
  if (Number.isNaN(ms)) return null;
  return Math.floor(ms / 86_400_000);
}

export function daysBetween(today: string, target: string): number | null {
  const a = toEpochDay(today);
  const b = toEpochDay(target);
  if (a == null || b == null) return null;
  return b - a;
}

export type RawCoupon = {
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
 * 서버가 읽어온 쿠폰을 요약 항목으로 거른다.
 *
 * 제외 조건은 세 가지다.
 *  - 종료 상태(used/expired): 이미 끝난 쿠폰은 "지금 써야 할 것" 이 아니다
 *  - 이미 지난 만료일: 서버 상태 갱신 배치가 아직 없어 status 가 active 로 남아
 *    있을 수 있으므로 날짜로도 한 번 더 거른다
 *  - 남이 등록한 비공개 쿠폰: visibility=private 은 등록자만 볼 수 있다.
 *    방 멤버라는 이유로 남의 비공개 쿠폰을 요약에 넣으면 안 된다
 */
export function selectExpiringCoupons(
  raw: RawCoupon[],
  uid: string,
  today: string,
  days: number,
  limit: number = EXPIRING_SOON_LIMIT
): { coupons: ExpiringCoupon[]; truncated: boolean } {
  const picked: ExpiringCoupon[] = [];
  for (const item of raw) {
    const status = typeof item.status === "string" ? item.status : "active";
    if (status === "used" || status === "expired" || status === "deleted" || status === "purging") {
      continue;
    }
    if (item.visibility === "private" && item.ownerUid !== uid) continue;

    const expires = typeof item.expiresLocalDate === "string" ? item.expiresLocalDate : null;
    if (!expires) continue;
    const daysLeft = daysBetween(today, expires);
    if (daysLeft == null || daysLeft < 0 || daysLeft > days) continue;

    picked.push({
      roomId: item.roomId,
      roomName: item.roomName,
      couponId: item.couponId,
      title: typeof item.title === "string" && item.title.trim() !== "" ? item.title : "이름 없는 쿠폰",
      brand: typeof item.brand === "string" ? item.brand : "",
      expiresLocalDate: expires,
      daysLeft
    });
  }

  // 급한 것부터. 같은 날이면 이름순으로 안정 정렬해 결과가 요청마다 흔들리지 않게 한다.
  picked.sort((a, b) => a.daysLeft - b.daysLeft || a.title.localeCompare(b.title, "ko"));

  return {
    coupons: picked.slice(0, limit),
    truncated: picked.length > limit
  };
}
