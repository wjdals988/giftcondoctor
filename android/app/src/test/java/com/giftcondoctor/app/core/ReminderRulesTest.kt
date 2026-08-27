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

    @Test
    fun expiryUrgencyPrioritizesServerTerminalStatus() {
        val today = LocalDate.parse("2026-08-28")
        val farFuture = LocalDate.parse("2026-12-31")
        // 서버가 종료 상태로 판정하면 남은 날짜가 많아도 종료로 본다.
        assertEquals(ExpiryUrgency.Ended, expiryUrgency("used", today, farFuture))
        assertEquals(ExpiryUrgency.Ended, expiryUrgency("expired", today, farFuture))
    }

    @Test
    fun expiryUrgencyFallsBackToComputedDaysWhenStatusIsStale() {
        val today = LocalDate.parse("2026-08-28")
        // 서버 만료 상태 갱신 배치가 지연되어 active 로 남아 있어도 지난 날짜는 종료다.
        assertEquals(ExpiryUrgency.Ended, expiryUrgency("active", today, LocalDate.parse("2026-08-27")))
        assertEquals(ExpiryUrgency.Critical, expiryUrgency("active", today, LocalDate.parse("2026-08-28")))
        assertEquals(ExpiryUrgency.Critical, expiryUrgency("active", today, LocalDate.parse("2026-08-29")))
        assertEquals(ExpiryUrgency.Soon, expiryUrgency("active", today, LocalDate.parse("2026-08-30")))
        assertEquals(ExpiryUrgency.Soon, expiryUrgency("active", today, LocalDate.parse("2026-08-31")))
        assertEquals(ExpiryUrgency.Relaxed, expiryUrgency("active", today, LocalDate.parse("2026-09-01")))
    }

    @Test
    fun couponDdayLabelUsesKoreanWording() {
        val today = LocalDate.parse("2026-08-28")
        assertEquals("사용 완료", couponDdayLabel("used", today, LocalDate.parse("2026-12-31")))
        assertEquals("만료", couponDdayLabel("expired", today, LocalDate.parse("2026-12-31")))
        assertEquals("만료", couponDdayLabel("active", today, LocalDate.parse("2026-08-27")))
        assertEquals("오늘 만료", couponDdayLabel("active", today, LocalDate.parse("2026-08-28")))
        assertEquals("내일 만료", couponDdayLabel("active", today, LocalDate.parse("2026-08-29")))
        assertEquals("D-7", couponDdayLabel("active", today, LocalDate.parse("2026-09-04")))
    }

    @Test
    fun expiryDateLabelHidesInternalIdentifiers() {
        val label = expiryDateLabel(LocalDate.parse("2026-09-01"))
        assertEquals("2026년 9월 1일까지", label)
        // 타임존 식별자가 사용자 문구에 새어 나오지 않아야 한다.
        assertEquals(false, label.contains("Asia"))
        assertEquals(false, label.contains("/"))
    }
}
