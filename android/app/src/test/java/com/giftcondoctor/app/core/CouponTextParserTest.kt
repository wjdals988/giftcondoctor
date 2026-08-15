package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CouponTextParserTest {
    @Test
    fun parsesBrandTitleAndKoreanExpiryDate() {
        val suggestion = parseCouponText(
            text = """
                스타벅스
                아이스 카페 아메리카노 Tall 교환권
                유효기간 2026년 06월 30일 까지
                쿠폰번호 123456789012
            """.trimIndent(),
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals("스타벅스", suggestion.brand)
        assertEquals("아이스 카페 아메리카노 Tall 교환권", suggestion.title)
        assertEquals(LocalDate.of(2026, 6, 30), suggestion.expiresLocalDate)
    }

    @Test
    fun parsesTwoDigitYearExpiryDate() {
        val suggestion = parseCouponText(
            text = """
                BHC
                뿌링클+콜라 1.25L
                사용기간: 26.12.31
            """.trimIndent(),
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals("BHC", suggestion.brand)
        assertEquals("뿌링클+콜라 1.25L", suggestion.title)
        assertEquals(LocalDate.of(2026, 12, 31), suggestion.expiresLocalDate)
    }

    @Test
    fun prefersLabelledExpiryOverEarlierOrderDate() {
        val suggestion = parseCouponText(
            text = """
                주문일 2026.05.17
                스타벅스 아메리카노
                유효기간 2026.06.30 까지
            """.trimIndent(),
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals(LocalDate.of(2026, 6, 30), suggestion.expiresLocalDate)
    }

    @Test
    fun compactLineDoesNotApplyFutureExpiryLabelToOrderDate() {
        val suggestion = parseCouponText(
            text = "주문일 2026.05.17 유효기간 2026.06.30",
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals(LocalDate.of(2026, 6, 30), suggestion.expiresLocalDate)
    }

    @Test
    fun issueDateIsPenalizedWhenExpiryIsOnNextLine() {
        val suggestion = parseCouponText(
            text = """
                발행일 2026-05-20
                만료일
                2026-08-31
            """.trimIndent(),
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals(LocalDate.of(2026, 8, 31), suggestion.expiresLocalDate)
    }

    @Test
    fun usagePeriodChoosesRangeEnd() {
        val suggestion = parseCouponText(
            text = "사용기간 2026.05.01 ~ 2026.12.31",
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals(LocalDate.of(2026, 12, 31), suggestion.expiresLocalDate)
    }

    @Test
    fun invalidAndPastDatesDoNotOverrideFutureExpiry() {
        val suggestion = parseCouponText(
            text = "구매일 2026.02.30 / 결제일 2026.05.01 / 만료일 2026.09.15",
            today = LocalDate.of(2026, 5, 17)
        )

        assertEquals(LocalDate.of(2026, 9, 15), suggestion.expiresLocalDate)
    }
}
