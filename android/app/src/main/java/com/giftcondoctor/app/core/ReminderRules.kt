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

/**
 * 만료 긴급도 4계층. 배지 색·아이콘·텍스트를 함께 바꿔 색만으로 구분하지 않도록 한다.
 * 서버 status 가 used/expired 면 클라이언트 D-day 계산보다 우선한다. 서버 만료 상태
 * 갱신 배치가 지연되어 status 가 active 로 남아 있어도 계산된 days 로 Ended 를 판정한다.
 */
enum class ExpiryUrgency { Ended, Critical, Soon, Relaxed }

fun expiryUrgency(status: String, today: LocalDate, expiresLocalDate: LocalDate): ExpiryUrgency {
    if (status == "used" || status == "expired") return ExpiryUrgency.Ended
    val days = daysBeforeExpiry(today, expiresLocalDate)
    return when {
        days < 0 -> ExpiryUrgency.Ended
        days <= 1 -> ExpiryUrgency.Critical
        days <= 3 -> ExpiryUrgency.Soon
        else -> ExpiryUrgency.Relaxed
    }
}

fun couponDdayLabel(status: String, today: LocalDate, expiresLocalDate: LocalDate): String {
    if (status == "used") return "사용 완료"
    if (status == "expired") return "만료"
    val days = daysBeforeExpiry(today, expiresLocalDate)
    return when {
        days < 0 -> "만료"
        days == 0 -> "오늘 만료"
        days == 1 -> "내일 만료"
        else -> "D-$days"
    }
}

/** 사용자에게 보이는 만료일 문구. 타임존 식별자 같은 내부 값을 노출하지 않는다. */
fun expiryDateLabel(expiresLocalDate: LocalDate): String =
    "${expiresLocalDate.year}년 ${expiresLocalDate.monthValue}월 ${expiresLocalDate.dayOfMonth}일까지"
