package com.giftcondoctor.app.core

/**
 * 최근 연 쿠폰 목록. 앱 아이콘 길게 누르기 바로가기의 원본이다.
 *
 * 매장 계산대에서 바코드까지 가는 경로가 네 단계다.
 * 앱 실행 → 방 목록 → 방 선택 → 쿠폰 선택 → 바코드. 가장 급한 순간의 경로가
 * 가장 길고, 방이 여럿이면 어느 방에 넣었는지도 기억해야 한다.
 *
 * 딥링크(`giftcondoctor://rooms/{roomId}/coupons/{couponId}`)가 푸시 알림용으로
 * 이미 뚫려 있으므로, 최근 연 쿠폰을 동적 바로가기로 노출하면 그 경로가 한 번의
 * 길게 누르기 + 탭으로 줄어든다. 새 화면을 만들 필요가 없다.
 */

/** 시스템이 표시하는 동적 바로가기 수는 기기마다 다르지만 대개 4~5개다. 3개면 충분하다. */
const val MAX_RECENT_COUPON_SHORTCUTS = 3

data class RecentCoupon(
    val roomId: String,
    val couponId: String,
    val title: String
)

/**
 * 새로 연 쿠폰을 목록 맨 앞에 놓고 상한까지 자른다.
 *
 * 같은 쿠폰을 다시 열면 중복을 만들지 않고 앞으로 올린다. 매장에서 반복해 쓰는
 * 쿠폰이 뒤로 밀리면 바로가기의 의미가 없다.
 */
fun withRecentCoupon(
    current: List<RecentCoupon>,
    opened: RecentCoupon,
    limit: Int = MAX_RECENT_COUPON_SHORTCUTS
): List<RecentCoupon> {
    if (opened.roomId.isBlank() || opened.couponId.isBlank()) return current
    val deduped = current.filterNot { it.roomId == opened.roomId && it.couponId == opened.couponId }
    return (listOf(opened) + deduped).take(limit)
}

/**
 * 삭제되거나 접근할 수 없게 된 쿠폰을 목록에서 뺀다.
 *
 * 바로가기를 눌렀는데 "쿠폰을 찾을 수 없습니다" 가 뜨면 사용자는 앱이 고장났다고
 * 느낀다. 방을 나가거나 쿠폰을 지운 경우가 여기 해당한다.
 */
fun withoutRecentCoupon(
    current: List<RecentCoupon>,
    roomId: String,
    couponId: String
): List<RecentCoupon> = current.filterNot { it.roomId == roomId && it.couponId == couponId }

/** 바로가기 라벨. 비어 있으면 시스템이 빈 항목을 그리므로 대체 문구를 넣는다. */
fun recentCouponShortcutLabel(title: String): String =
    title.trim().ifEmpty { "이름 없는 쿠폰" }
