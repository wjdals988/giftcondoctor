package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotEquals

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

    @Test
    fun barcodeNumberSplitBySpacesIsNotUsedAsTitle() {
        // 2026-08-28 실기기에서 실제로 관측된 형태다. noisyPattern 의 \d{6,} 는
        // 연속 6자리만 걸러내므로 공백으로 끊긴 4자리 그룹은 통과했다.
        val text = """
            9313 3685 7353 5282
            GS25
            2027.02.14 까지
        """.trimIndent()

        val suggestion = parseCouponText(text, LocalDate.parse("2026-08-28"))

        assertNotEquals("9313 3685 7353 5282", suggestion.title)
    }

    @Test
    fun digitsOnlyIdentifierDetectionCoversCommonSeparators() {
        // 공백·하이픈·점으로 끊긴 번호를 모두 식별자로 본다.
        assertTrue("9313 3685 7353 5282".isDigitsOnlyIdentifier())
        assertTrue("1234-5678-9012".isDigitsOnlyIdentifier())
        assertTrue("9313368573535282".isDigitsOnlyIdentifier())
        assertTrue("123.456.789.012".isDigitsOnlyIdentifier())
    }

    @Test
    fun shortNumbersAndRealTitlesAreNotTreatedAsIdentifiers() {
        // 금액·수량 표기까지 버리면 정상 제목을 놓친다. 숫자 8자리 미만은 남긴다.
        assertFalse("5000".isDigitsOnlyIdentifier())
        assertFalse("1000".isDigitsOnlyIdentifier())
        assertFalse("2 4 6".isDigitsOnlyIdentifier())
        // 글자가 하나라도 섞이면 식별자가 아니다. 제목은 대개 글자를 포함한다.
        assertFalse("카페 아메리카노 T".isDigitsOnlyIdentifier())
        assertFalse("아메리카노 2잔 12345678".isDigitsOnlyIdentifier())
        assertFalse("5000원권".isDigitsOnlyIdentifier())
    }

    @Test
    fun eightDigitsWithoutLettersIsTreatedAsIdentifierEvenWhenSplit() {
        // 경계값이다. 글자 없이 숫자만 8자리를 넘기면 제목으로 쓸 만한 문자열이
        // 아니라고 본다. "1000 2000" 같은 줄은 실제 제목이라기보다 금액 나열이거나
        // OCR 이 잘못 묶은 조각일 가능성이 높다.
        assertTrue("1000 2000".isDigitsOnlyIdentifier())
        assertFalse("100 200".isDigitsOnlyIdentifier())
    }

    @Test
    fun realTitleStillWinsWhenBarcodeLineIsPresent() {
        val text = """
            9313 3685 7353 5282
            카페 아메리카노 T
            투썸플레이스
            2027.02.14 까지
        """.trimIndent()

        val suggestion = parseCouponText(text, LocalDate.parse("2026-08-28"))

        assertEquals("카페 아메리카노 T", suggestion.title)
    }
}
