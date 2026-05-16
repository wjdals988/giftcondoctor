package com.giftcondoctor.app.core

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class NotificationMode(val wire: String, val label: String, val days: List<Int>) {
    Minimal("minimal", "최소", listOf(3, 0)),
    Basic("basic", "기본", listOf(7, 3, 1, 0)),
    Careful("careful", "꼼꼼", listOf(7, 5, 3, 2, 1, 0));

    companion object {
        fun fromWire(value: String?): NotificationMode =
            entries.firstOrNull { it.wire == value } ?: Basic
    }
}

fun seoulToday(clock: Clock = Clock.systemUTC()): LocalDate =
    LocalDate.now(clock.withZone(ZoneId.of(AppConstants.SEOUL_TIME_ZONE)))

fun daysBeforeExpiry(today: LocalDate, expiresLocalDate: LocalDate): Int =
    ChronoUnit.DAYS.between(today, expiresLocalDate).toInt()

fun statusLabel(status: String): String = when (status) {
    "active" -> "사용 가능"
    "reserved" -> "예약됨"
    "used" -> "사용 완료"
    "expired" -> "만료됨"
    else -> "알 수 없음"
}
