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
enum class ExpiryUrgency { Ended, Critical, Soon, Relaxed, Distant }

/**
 * 만료 배지를 붙이지 않는 경계.
 *
 * 이 앱의 알림 사다리는 최대 D-7 에서 시작한다(NotificationMode.Careful). 그보다
 * 4배 이상 떨어진 시점의 남은 일수는 사용자가 행동을 바꿀 근거가 되지 못한다.
 * 실기기 확인에서 "D-170" 배지가 D-1 과 같은 시각적 슬롯을 차지하고 있었는데,
 * 배지의 존재 이유가 임박 신호이므로 이런 표시는 신호를 희석한다.
 */
const val EXPIRY_BADGE_MAX_DAYS = 30

fun expiryUrgency(status: String, today: LocalDate, expiresLocalDate: LocalDate): ExpiryUrgency {
    if (status == "used" || status == "expired") return ExpiryUrgency.Ended
    val days = daysBeforeExpiry(today, expiresLocalDate)
    return when {
        days < 0 -> ExpiryUrgency.Ended
        days <= 1 -> ExpiryUrgency.Critical
        days <= 3 -> ExpiryUrgency.Soon
        days <= EXPIRY_BADGE_MAX_DAYS -> ExpiryUrgency.Relaxed
        else -> ExpiryUrgency.Distant
    }
}

/**
 * 배지를 렌더링할지 여부.
 *
 * Distant 는 배지를 그리지 않는다. 만료일은 보조 문구에 이미 있으므로 정보가
 * 사라지지 않고, 비워진 가로 공간만큼 제목·보조 문구가 넓게 쓰인다.
 */
fun shouldShowExpiryBadge(urgency: ExpiryUrgency): Boolean = urgency != ExpiryUrgency.Distant

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

/**
 * 만료 배지가 이미 상태를 말하고 있는지 여부.
 *
 * couponDdayLabel 은 종료 상태(used/expired)에서 "사용 완료" / "만료" 를 그대로
 * 반환한다. 이때 본문에 statusLabel 을 또 넣으면 같은 값이 한 행에 두 번 나온다.
 * 진행 중(active/reserved)일 때는 배지가 "D-7" 처럼 남은 기간만 말하므로
 * 상태 문구가 여전히 정보를 더한다.
 */
fun expiryBadgeAlreadyStatesStatus(urgency: ExpiryUrgency): Boolean =
    urgency == ExpiryUrgency.Ended

/**
 * 목록 행 보조 문구. 배지와 중복되지 않는 정보만 남긴다.
 */
fun couponListSupportingText(
    brand: String,
    status: String,
    urgency: ExpiryUrgency,
    expiresLocalDate: LocalDate
): String {
    val parts = mutableListOf(
        brand.ifBlank { "브랜드 없음" },
        expiryDateLabel(expiresLocalDate)
    )
    if (!expiryBadgeAlreadyStatesStatus(urgency)) parts += statusLabel(status)
    return parts.joinToString(" · ")
}

/**
 * 사용 완료 실행 취소가 가능한 창의 길이.
 *
 * Firestore 보안 규칙 `memberCanUndoMarkUsed` 가
 * `request.time <= usedAt + duration.value(5, "m")` 로 강제한다
 * (`firebase/firestore.rules:139-149`). 화면은 그 규칙과 같은 값을 써야 한다.
 * 화면이 더 길게 잡으면 눌러도 규칙에 막혀 실패하는 죽은 버튼이 되고, 더 짧게
 * 잡으면 아직 되돌릴 수 있는데 기회를 감춘다.
 */
const val UNDO_MARK_USED_WINDOW_MINUTES = 5L

/**
 * 지금 이 쿠폰의 사용 완료를 되돌릴 수 있는지 판정한다.
 *
 * 규칙과 같은 세 조건을 본다. 상태가 `used`, 처리자가 본인, 그리고 5분 창 안.
 */
fun canUndoMarkUsed(
    status: String,
    usedByUid: String?,
    currentUid: String?,
    usedAtEpochMillis: Long?,
    nowEpochMillis: Long
): Boolean {
    if (status != "used") return false
    if (currentUid == null || usedByUid != currentUid) return false
    if (usedAtEpochMillis == null) return false
    return nowEpochMillis <= usedAtEpochMillis + UNDO_MARK_USED_WINDOW_MINUTES * 60_000
}

/**
 * 실행 취소 창에 남은 시간 문구.
 *
 * 창이 닫히는 중이라는 사실을 알아야 사용자가 판단할 수 있다. 30초 미만은 초
 * 단위로 세지 않는다. 계산대 앞에서 숫자가 빠르게 줄어드는 것을 보면 조급해질
 * 뿐이고, 어차피 곧 사라진다.
 */
fun undoMarkUsedRemainingLabel(usedAtEpochMillis: Long, nowEpochMillis: Long): String {
    val deadline = usedAtEpochMillis + UNDO_MARK_USED_WINDOW_MINUTES * 60_000
    val remainingMillis = deadline - nowEpochMillis
    if (remainingMillis <= 0) return "곧 마감"
    val remainingMinutes = remainingMillis / 60_000
    return if (remainingMinutes >= 1) "${remainingMinutes}분 남음" else "1분 미만 남음"
}
