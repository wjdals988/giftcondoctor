package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ReminderRulesTest {
    @Test
    fun notificationModeDaysMatchProductPolicy() {
        assertEquals(listOf(3, 0), NotificationMode.Minimal.days)
        assertEquals(listOf(7, 3, 1, 0), NotificationMode.Basic.days)
        assertEquals(listOf(7, 5, 3, 2, 1, 0), NotificationMode.Careful.days)
    }

    @Test
    fun seoulTodayUsesAsiaSeoulDate() {
        val clock = Clock.fixed(Instant.parse("2026-05-15T15:01:00Z"), ZoneOffset.UTC)
        assertEquals(LocalDate.parse("2026-05-16"), seoulToday(clock))
    }

    @Test
    fun daysBeforeExpiryUsesLocalDateDifference() {
        assertEquals(
            7,
            daysBeforeExpiry(LocalDate.parse("2026-05-16"), LocalDate.parse("2026-05-23"))
        )
        assertEquals(
            0,
            daysBeforeExpiry(LocalDate.parse("2026-05-16"), LocalDate.parse("2026-05-16"))
        )
    }

    @Test
    fun statusLabelsAreKorean() {
        assertEquals("사용 가능", statusLabel("active"))
        assertEquals("예약됨", statusLabel("reserved"))
        assertEquals("사용 완료", statusLabel("used"))
        assertEquals("만료됨", statusLabel("expired"))
    }
}
