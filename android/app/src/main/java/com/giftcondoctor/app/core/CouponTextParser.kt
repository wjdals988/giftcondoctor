package com.giftcondoctor.app.core

import java.time.LocalDate

data class CouponTextSuggestion(
    val title: String? = null,
    val brand: String? = null,
    val expiresLocalDate: LocalDate? = null
)

private val brandCandidates = listOf(
    "스타벅스",
    "투썸플레이스",
    "이디야",
    "메가MGC커피",
    "메가커피",
    "컴포즈커피",
    "빽다방",
    "커피빈",
    "할리스",
    "파스쿠찌",
    "배스킨라빈스",
    "베스킨라빈스",
    "던킨",
    "파리바게뜨",
    "파리바게트",
    "뚜레쥬르",
    "올리브영",
    "GS25",
    "CU",
    "세븐일레븐",
    "이마트24",
    "BHC",
    "BBQ",
    "교촌치킨",
    "굽네치킨",
    "맘스터치",
    "버거킹",
    "맥도날드",
    "롯데리아",
    "네이버페이",
    "카카오",
    "요기요",
    "배달의민족"
)

private val couponKeywords = listOf(
    "쿠폰",
    "교환권",
    "상품권",
    "금액권",
    "기프티콘",
    "아메리카노",
    "라떼",
    "커피",
    "케이크",
    "세트",
    "치킨",
    "버거",
    "음료"
)

private val datePattern = Regex("""(?<!\d)(20\d{2}|\d{2})\s*[.\-/년]\s*(\d{1,2})\s*[.\-/월]\s*(\d{1,2})\s*(?:일)?(?!\d)""")
private val noisyPattern = Regex("""(?i)(barcode|pin|order|주문|발행일|구매일|결제일|바코드|쿠폰번호|인증번호|유효기간|만료|사용기간|까지|주의|환불|취소|문의|http|www|\d{6,})""")
private val expiryLabels = listOf("유효기간", "사용기간", "만료일", "만료")
private val nonExpiryDateLabels = listOf("주문일", "발행일", "구매일", "결제일", "승인일")
private val dateRangeSeparator = Regex("""(?:~|〜|–|—|부터)""")

fun parseCouponText(text: String, today: LocalDate = LocalDate.now(ZoneIdProvider.seoul)): CouponTextSuggestion {
    val normalizedLines = text
        .lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.length >= 2 }
        .toList()

    val brand = detectBrand(normalizedLines)
    val expires = detectExpiry(text, today)
    val title = detectTitle(normalizedLines, brand)

    return CouponTextSuggestion(
        title = title,
        brand = brand,
        expiresLocalDate = expires
    )
}

private fun detectBrand(lines: List<String>): String? {
    val joined = lines.joinToString(" ").uppercase()
    return brandCandidates.firstOrNull { joined.contains(it.uppercase()) }
}

/**
 * 숫자와 구분자로만 이루어진 줄인지 판정한다.
 *
 * noisyPattern 의 `\d{6,}` 는 연속된 6자리 이상만 걸러낸다. 그런데 OCR 이 읽어낸
 * 바코드·카드 번호는 "9313 3685 7353 5282" 처럼 공백이나 하이픈으로 끊겨 있어서
 * 각 그룹이 4자리에 그치고, 이 규칙을 그대로 통과했다. 2026-08-28 실기기 확인에서
 * 쿠폰 제목이 바코드 번호로 저장된 항목이 실제로 있었다.
 *
 * 잘못된 제목은 빈 제목보다 나쁘다. 목록에서 그 쿠폰이 무엇인지 알 방법이 없고,
 * 사용자는 제목이 채워져 있으니 고칠 생각도 하지 않는다. 이런 줄은 후보에서
 * 제외해 사용자가 직접 입력하게 둔다.
 *
 * 구분자를 걷어낸 숫자가 8자리 미만이면 제외하지 않는다. "1000" 이나 "5000원"
 * 같은 금액·수량 표기까지 버리면 정상 제목을 놓칠 수 있다.
 */
internal fun String.isDigitsOnlyIdentifier(): Boolean {
    val digits = count { it.isDigit() }
    if (digits < 8) return false
    return all { it.isDigit() || it.isWhitespace() || it == '-' || it == '.' }
}

private fun detectTitle(lines: List<String>, brand: String?): String? {
    val candidates = lines
        .map { it.cleanTitleCandidate() }
        .filter { line ->
            line.length in 3..40 &&
                line != brand &&
                !noisyPattern.containsMatchIn(line) &&
                !datePattern.containsMatchIn(line) &&
                !line.isDigitsOnlyIdentifier() &&
                line.count { it.isLetterOrDigit() } >= 3
        }

    return candidates.firstOrNull { line -> couponKeywords.any { line.contains(it, ignoreCase = true) } }
        ?: candidates.maxByOrNull { it.length }
}

private fun detectExpiry(text: String, today: LocalDate): LocalDate? {
    val candidates = datePattern.findAll(text)
        .mapNotNull { match ->
            val yearText = match.groupValues[1]
            val year = if (yearText.length == 2) 2000 + yearText.toInt() else yearText.toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            val date = runCatching { LocalDate.of(year, month, day) }.getOrNull() ?: return@mapNotNull null
            ExpiryDateCandidate(date, match.range)
        }
        .filter { it.date >= today.minusDays(1) }
        .toList()

    return candidates
        .mapIndexed { index, candidate ->
            candidate.date to expiryLabelScore(text, candidates, index)
        }
        .sortedWith(compareByDescending<Pair<LocalDate, Int>> { it.second }.thenBy { it.first })
        .firstOrNull()
        ?.first
}

private data class ExpiryDateCandidate(val date: LocalDate, val range: IntRange)

private fun expiryLabelScore(
    text: String,
    candidates: List<ExpiryDateCandidate>,
    index: Int
): Int {
    val candidate = candidates[index]
    val lineStart = text.lastIndexOf('\n', candidate.range.first - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', candidate.range.last + 1).let { if (it < 0) text.length else it }
    val sameLineBefore = text.substring(lineStart, candidate.range.first).takeLast(32)
    val sameLineAfter = text.substring(candidate.range.last + 1, lineEnd).take(16)
    var score = nearestDateLabelScore(sameLineBefore)

    if (score == 0) {
        val previousLine = text.substring(0, lineStart).lineSequence().lastOrNull().orEmpty().takeLast(32)
        if (expiryLabels.any(previousLine::contains)) score += 2
    }
    if (sameLineAfter.contains("까지")) score += 4

    candidates.getOrNull(index - 1)?.let { previous ->
        val between = text.substring(previous.range.last + 1, candidate.range.first)
        if (dateRangeSeparator.containsMatchIn(between)) score += 3
    }
    candidates.getOrNull(index + 1)?.let { next ->
        val between = text.substring(candidate.range.last + 1, next.range.first)
        if (dateRangeSeparator.containsMatchIn(between)) score -= 2
    }
    return score
}

private fun nearestDateLabelScore(textBeforeDate: String): Int {
    val labels = expiryLabels.map { it to 5 } + nonExpiryDateLabels.map { it to -6 }
    return labels
        .mapNotNull { (label, score) ->
            textBeforeDate.lastIndexOf(label).takeIf { it >= 0 }?.let { index -> Triple(index, label.length, score) }
        }
        .maxWithOrNull(compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second })
        ?.third
        ?: 0
}

private fun String.cleanTitleCandidate(): String =
    replace(Regex("""^[\[\(<{【]\s*|[\]\)>}】]\s*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
