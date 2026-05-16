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
}
