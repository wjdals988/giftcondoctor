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

    @Test
    fun favoritesComeFirstAmongUsableCoupons() {
        val today = LocalDate.parse("2026-08-29")
        val coupons = listOf(
            coupon("soon", "곧 만료", "A", "active", "2026-08-30"),
            coupon("fav", "즐겨찾기", "B", "active", "2026-12-31"),
            coupon("later", "나중", "C", "active", "2026-10-01")
        )

        val sorted = filterAndSortCoupons(coupons, "", CouponListFilter.ALL, today, setOf("fav"))

        // 만료가 훨씬 늦어도 즐겨찾기가 먼저다. 자주 쓰는 쿠폰을 매번 찾아
        // 내려가지 않는 것이 이 기능의 목적이다.
        assertEquals(listOf("fav", "soon", "later"), sorted.map { it.id })
    }

    @Test
    fun favoritesKeepExpirySortAmongThemselves() {
        val today = LocalDate.parse("2026-08-29")
        val coupons = listOf(
            coupon("favLate", "즐겨1", "A", "active", "2026-12-31"),
            coupon("favSoon", "즐겨2", "B", "active", "2026-09-01"),
            coupon("plain", "일반", "C", "active", "2026-08-30")
        )

        val sorted = filterAndSortCoupons(
            coupons,
            "",
            CouponListFilter.ALL,
            today,
            setOf("favLate", "favSoon")
        )

        assertEquals(listOf("favSoon", "favLate", "plain"), sorted.map { it.id })
    }

    @Test
    fun usedOrExpiredFavoritesAreNotPinned() {
        // 쓸 수 없는 쿠폰이 가장 좋은 자리를 차지하면 안 된다.
        val today = LocalDate.parse("2026-08-29")
        val coupons = listOf(
            coupon("favUsed", "쓴 즐겨찾기", "A", "used", "2026-12-31"),
            coupon("favExpired", "만료 즐겨찾기", "B", "active", "2026-01-01"),
            coupon("plain", "일반", "C", "active", "2026-09-30")
        )

        val sorted = filterAndSortCoupons(
            coupons,
            "",
            CouponListFilter.ALL,
            today,
            setOf("favUsed", "favExpired")
        )

        assertEquals("plain", sorted.first().id)
    }

    @Test
    fun favoriteDocIdIsDerivedFromTheReference() {
        // 임의 ID 를 쓰면 같은 쿠폰이 여러 문서로 중복되고 해제할 때 무엇을
        // 지울지 알 수 없다. 규칙도 같은 형식을 강제한다.
        assertEquals("room-1__coupon-1", favoriteDocId("room-1", "coupon-1"))
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
