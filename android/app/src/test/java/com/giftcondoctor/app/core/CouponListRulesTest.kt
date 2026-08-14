package com.giftcondoctor.app.core

import com.giftcondoctor.app.data.model.Coupon
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CouponListRulesTest {
    private val today = LocalDate.parse("2026-08-15")

    @Test
    fun allCouponsPutActionableFirstByNearestExpiry() {
        val coupons = listOf(
            coupon("used", "사용함", "카페", "used", "2026-08-14"),
            coupon("later", "나중", "마트", "active", "2026-08-20"),
            coupon("expired", "지남", "편의점", "active", "2026-08-10"),
            coupon("soon", "임박", "카페", "reserved", "2026-08-16")
        )

        val result = filterAndSortCoupons(coupons, "", CouponListFilter.ALL, today)

        assertEquals(listOf("soon", "later", "expired", "used"), result.map { it.id })
    }

    @Test
    fun expiredFilterIncludesPastActiveCoupon() {
        val coupons = listOf(
            coupon("past", "지난 쿠폰", "A", "active", "2026-08-14"),
            coupon("future", "남은 쿠폰", "B", "active", "2026-08-16")
        )

        val result = filterAndSortCoupons(coupons, "", CouponListFilter.EXPIRED, today)

        assertEquals(listOf("past"), result.map { it.id })
    }

    @Test
    fun searchMatchesTrimmedTitleAndBrandIgnoringCase() {
        val coupons = listOf(
            coupon("title", "STARBUCKS 아메리카노", "커피", "active", "2026-08-16"),
            coupon("brand", "케이크", "Starbucks", "active", "2026-08-17"),
            coupon("other", "치킨", "BHC", "active", "2026-08-18")
        )

        val result = filterAndSortCoupons(coupons, "  starbucks ", CouponListFilter.ALL, today)

        assertEquals(listOf("title", "brand"), result.map { it.id })
    }

    @Test
    fun usedStatusWinsOverPastExpiry() {
        val coupons = listOf(coupon("used", "사용함", "카페", "used", "2026-08-01"))

        val used = filterAndSortCoupons(coupons, "", CouponListFilter.USED, today)
        val expired = filterAndSortCoupons(coupons, "", CouponListFilter.EXPIRED, today)

        assertEquals(listOf("used"), used.map { it.id })
        assertEquals(emptyList<Coupon>(), expired)
    }

    private fun coupon(
        id: String,
        title: String,
        brand: String,
        status: String,
        expires: String
    ): Coupon = Coupon(
        id = id,
        roomId = "room",
        title = title,
        brand = brand,
        ownerUid = "owner",
        imageBlobPath = "",
        thumbnailBlobPath = null,
        imageWidth = null,
        imageHeight = null,
        expiresLocalDate = LocalDate.parse(expires),
        timezone = AppConstants.SEOUL_TIME_ZONE,
        status = status,
        reservedByUid = null,
        usedByUid = null,
        visibility = "room",
        notifyTarget = "allMembers"
    )
}
