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

fun filterAndSortCoupons(
    coupons: List<Coupon>,
    query: String,
    filter: CouponListFilter,
    today: LocalDate
): List<Coupon> {
    val normalizedQuery = query.trim()
    return coupons
        .asSequence()
        .filter { coupon -> coupon.matchesSearch(normalizedQuery) }
        .filter { coupon -> filter == CouponListFilter.ALL || coupon.listFilter(today) == filter }
        .sortedWith(
            compareBy<Coupon> { it.sortGroup(today) }
                .thenBy { it.sortDate(today) }
                .thenBy { it.title.lowercase() }
        )
        .toList()
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
