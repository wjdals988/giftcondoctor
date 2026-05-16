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
private val noisyPattern = Regex("""(?i)(barcode|pin|order|주문|바코드|쿠폰번호|인증번호|유효기간|만료|사용기간|까지|주의|환불|취소|문의|http|www|\d{6,})""")

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

private fun detectTitle(lines: List<String>, brand: String?): String? {
    val candidates = lines
        .map { it.cleanTitleCandidate() }
        .filter { line ->
            line.length in 3..40 &&
                line != brand &&
                !noisyPattern.containsMatchIn(line) &&
                !datePattern.containsMatchIn(line) &&
                line.count { it.isLetterOrDigit() } >= 3
        }

    return candidates.firstOrNull { line -> couponKeywords.any { line.contains(it, ignoreCase = true) } }
        ?: candidates.maxByOrNull { it.length }
}

private fun detectExpiry(text: String, today: LocalDate): LocalDate? {
    return datePattern.findAll(text)
        .mapNotNull { match ->
            val yearText = match.groupValues[1]
            val year = if (yearText.length == 2) 2000 + yearText.toInt() else yearText.toInt()
            val month = match.groupValues[2].toInt()
            val day = match.groupValues[3].toInt()
            runCatching { LocalDate.of(year, month, day) }.getOrNull()
        }
        .filter { it >= today.minusDays(1) }
        .minOrNull()
}

private fun String.cleanTitleCandidate(): String =
    replace(Regex("""^[\[\(<{【]\s*|[\]\)>}】]\s*$"""), "")
        .replace(Regex("""\s+"""), " ")
        .trim()
