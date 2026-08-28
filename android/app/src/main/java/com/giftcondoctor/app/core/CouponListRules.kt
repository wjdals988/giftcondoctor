package com.giftcondoctor.app.core

import com.giftcondoctor.app.data.model.Coupon
import java.time.LocalDate

enum class CouponListFilter {
    ALL,
    AVAILABLE,
    RESERVED,
    USED,
    EXPIRED
}

/**
 * @param favoriteCouponIds 즐겨찾기한 쿠폰 ID. 아직 쓸 수 있는 즐겨찾기만 맨 위로
 *   올린다. 이미 사용했거나 만료된 쿠폰을 즐겨찾기했다는 이유로 목록 첫 줄에
 *   두면, 가장 좋은 자리를 쓸 수 없는 쿠폰이 차지한다.
 */
fun filterAndSortCoupons(
    coupons: List<Coupon>,
    query: String,
    filter: CouponListFilter,
    today: LocalDate,
    favoriteCouponIds: Set<String> = emptySet()
): List<Coupon> {
    val normalizedQuery = query.trim()
    return coupons
        .asSequence()
        .filter { coupon -> coupon.matchesSearch(normalizedQuery) }
        .filter { coupon -> filter == CouponListFilter.ALL || coupon.listFilter(today) == filter }
        .sortedWith(
            compareBy<Coupon> { it.favoriteRank(today, favoriteCouponIds) }
                .thenBy { it.sortGroup(today) }
                .thenBy { it.sortDate(today) }
                .thenBy { it.title.lowercase() }
        )
        .toList()
}

/**
 * 즐겨찾기 우선순위. 0 이 위로 온다.
 *
 * 즐겨찾기 안에서는 기존 정렬(만료 임박순)을 그대로 쓴다. 즐겨찾기를 여러 개 두면
 * 그 안에서도 급한 것이 먼저여야 한다.
 */
private fun Coupon.favoriteRank(today: LocalDate, favoriteCouponIds: Set<String>): Int {
    if (id !in favoriteCouponIds) return 1
    return when (listFilter(today)) {
        CouponListFilter.AVAILABLE, CouponListFilter.RESERVED -> 0
        else -> 1
    }
}

private fun Coupon.matchesSearch(query: String): Boolean =
    query.isEmpty() || title.contains(query, ignoreCase = true) || brand.contains(query, ignoreCase = true)

private fun Coupon.listFilter(today: LocalDate): CouponListFilter = when {
    status == "used" -> CouponListFilter.USED
    status == "expired" || expiresLocalDate.isBefore(today) -> CouponListFilter.EXPIRED
    status == "reserved" -> CouponListFilter.RESERVED
    else -> CouponListFilter.AVAILABLE
}

private fun Coupon.sortGroup(today: LocalDate): Int = when (listFilter(today)) {
    CouponListFilter.AVAILABLE, CouponListFilter.RESERVED -> 0
    CouponListFilter.EXPIRED -> 1
    CouponListFilter.USED -> 2
    CouponListFilter.ALL -> error("ALL은 쿠폰 상태로 사용할 수 없습니다.")
}

private fun Coupon.sortDate(today: LocalDate): Long = when (listFilter(today)) {
    CouponListFilter.AVAILABLE, CouponListFilter.RESERVED -> expiresLocalDate.toEpochDay()
    CouponListFilter.EXPIRED, CouponListFilter.USED -> -expiresLocalDate.toEpochDay()
    CouponListFilter.ALL -> error("ALL은 쿠폰 상태로 사용할 수 없습니다.")
}
