/**
 * 만료일이 지난 쿠폰을 expired 상태로 전이시키는 규칙.
 *
 * 지금까지 cron 에는 이 단계가 없었다. 알림 발송, 휴지통 정리, Blob 정리,
 * 알림 이력 정리는 있었지만 active 로 남아 있는 쿠폰을 expired 로 바꾸는 곳이
 * 어디에도 없었다.
 *
 * 그 결과 클라이언트가 같은 결함을 세 곳에서 우회하고 있다.
 *  - expiryUrgency: status 가 active 여도 날짜가 지났으면 Ended 로 판정
 *  - couponDdayLabel: 같은 fallback 을 중복 구현
 *  - selectExpiringCoupons: 서버 응답에서도 날짜로 한 번 더 필터
 *
 * 방어 코드를 세 곳에 두는 것은 근원을 고칠 때가 됐다는 신호다. 이 모듈이 그
 * 근원을 담당한다. 클라이언트 방어는 그대로 둔다 — 배치가 하루 한 번 도는
 * 이상 그 사이의 공백은 남고, 방어가 있어야 그 공백에서도 화면이 옳다.
 */

/** 한 번의 실행에서 전이시킬 최대 건수. 다른 정리 작업과 같은 방식으로 상한을 둔다. */
export const EXPIRE_BATCH_SIZE = 200;

/** 전이 대상이 될 수 있는 상태. 이미 끝났거나 삭제된 쿠폰은 건드리지 않는다. */
const TRANSITIONABLE = new Set(["active", "reserved"]);

export type ExpiryCandidate = {
  status?: unknown;
  expiresLocalDate?: unknown;
};

/**
 * 이 쿠폰을 expired 로 바꿔야 하는지 판정한다.
 *
 * @param today 서울 기준 오늘 날짜(YYYY-MM-DD)
 *
 * 만료일 당일은 아직 쓸 수 있으므로 전이시키지 않는다. 알림도 "오늘 만료" 를
 * 보내는 날이고, 사용자는 그날 매장에서 쿠폰을 쓴다. 하루가 완전히 지난
 * 다음부터가 만료다.
 *
 * used 는 제외한다. 이미 사용한 쿠폰을 만료로 덮으면 사용 완료 기록이 사라지고,
 * 5분 내 실행 취소 경로와도 충돌한다.
 *
 * deleted/purging 도 제외한다. 휴지통 상태를 만료로 덮으면 복구 시 상태가
 * 어긋난다.
 */
export function shouldExpireCoupon(candidate: ExpiryCandidate, today: string): boolean {
  const status = typeof candidate.status === "string" ? candidate.status : "active";
  if (!TRANSITIONABLE.has(status)) return false;

  const expires = typeof candidate.expiresLocalDate === "string" ? candidate.expiresLocalDate : null;
  if (!expires || !/^\d{4}-\d{2}-\d{2}$/.test(expires)) return false;

  // 문자열 비교로 충분하다. YYYY-MM-DD 는 사전순과 시간순이 일치한다.
  return expires < today;
}

/**
 * 쿼리로 가져온 후보들 중 실제 전이 대상만 고른다.
 *
 * Firestore 쿼리는 expiresLocalDate < today 로 좁힐 수 있지만 status 까지 한
 * 쿼리로 거르려면 복합 인덱스가 필요하다. 이미 status+expiresLocalDate
 * collection group 인덱스가 있으므로 그것을 쓰되, 방어적으로 여기서 한 번 더
 * 판정한다. 인덱스가 바뀌어도 잘못된 문서를 덮어쓰지 않는다.
 */
export function selectCouponsToExpire<T extends ExpiryCandidate>(
  candidates: T[],
  today: string
): T[] {
  return candidates.filter((candidate) => shouldExpireCoupon(candidate, today));
}
