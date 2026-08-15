package com.giftcondoctor.app.core

import com.giftcondoctor.app.data.model.Coupon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CouponDuplicateRulesTest {
    private val expiry = LocalDate.parse("2026-12-31")

    @Test
    fun `공백과 기호가 달라도 같은 이름과 만료일을 후보로 찾는다`() {
        val result = findPossibleCouponDuplicates(
            input = input(title = "아메리카노 Tall", brand = "스타 벅스"),
            coupons = listOf(coupon(title = "아메리카노-TALL", brand = "스타벅스"))
        )

        assertEquals(CouponDuplicateReason.SameDetails, result.single().reason)
    }

    @Test
    fun `바코드가 같으면 이름이 달라도 가장 강한 후보로 찾는다`() {
        val exactBarcode = coupon(id = "barcode", title = "다른 상품", barcode = "880 1234")
        val sameDetails = coupon(id = "details", title = "아메리카노")
        val result = findPossibleCouponDuplicates(
            input = input(title = "아메리카노", barcode = "8801234"),
            coupons = listOf(sameDetails, exactBarcode)
        )

        assertEquals(listOf("barcode", "details"), result.map { it.couponId })
        assertEquals(CouponDuplicateReason.ExactBarcode, result.first().reason)
    }

    @Test
    fun `만료일이 다르면 같은 이름과 바코드도 후보에서 제외한다`() {
        val result = findPossibleCouponDuplicates(
            input = input(title = "아메리카노", barcode = "8801234"),
            coupons = listOf(coupon(expires = expiry.plusDays(1), barcode = "8801234"))
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `브랜드가 비어 있으면 같은 이름을 경고하되 다른 브랜드는 제외한다`() {
        val blankBrand = coupon(id = "blank", brand = "")
        val otherBrand = coupon(id = "other", brand = "투썸플레이스")
        val result = findPossibleCouponDuplicates(
            input = input(title = "아메리카노", brand = "스타벅스"),
            coupons = listOf(blankBrand, otherBrand)
        )

        assertEquals(listOf("blank"), result.map { it.couponId })
    }

    @Test
    fun `후보 결과는 지정 개수까지만 반환한다`() {
        val result = findPossibleCouponDuplicates(
            input = input(title = "아메리카노"),
            coupons = (1..5).map { coupon(id = "coupon-$it") },
            limit = 3
        )

        assertEquals(3, result.size)
    }

    @Test
    fun `문자나 숫자가 없는 이름끼리는 같은 쿠폰으로 단정하지 않는다`() {
        val result = findPossibleCouponDuplicates(
            input = input(title = "---", brand = ""),
            coupons = listOf(coupon(title = "...", brand = ""))
        )

        assertTrue(result.isEmpty())
    }

    private fun input(
        title: String,
        brand: String = "스타벅스",
        barcode: String? = null
    ) = CouponDuplicateInput(title, brand, expiry, barcode)

    private fun coupon(
        id: String = "coupon-1",
        title: String = "아메리카노",
        brand: String = "스타벅스",
        expires: LocalDate = expiry,
        barcode: String? = null
    ) = Coupon(
        id = id,
        roomId = "room-1",
        title = title,
        brand = brand,
        ownerUid = "member-1",
        imageBlobPath = "rooms/room-1/coupons/$id/image.jpg",
        thumbnailBlobPath = null,
        imageWidth = null,
        imageHeight = null,
        expiresLocalDate = expires,
        timezone = AppConstants.SEOUL_TIME_ZONE,
        status = "active",
        reservedByUid = null,
        usedByUid = null,
        visibility = "room",
        notifyTarget = "allMembers",
        barcodeValue = barcode
    )
}
