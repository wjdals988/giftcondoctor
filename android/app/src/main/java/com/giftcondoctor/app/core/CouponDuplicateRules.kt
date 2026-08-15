package com.giftcondoctor.app.core

import com.giftcondoctor.app.data.model.Coupon
import java.time.LocalDate

private const val DEFAULT_DUPLICATE_RESULT_LIMIT = 3

enum class CouponDuplicateReason {
    ExactBarcode,
    SameDetails
}

data class CouponDuplicateCandidate(
    val couponId: String,
    val title: String,
    val brand: String,
    val expiresLocalDate: LocalDate,
    val visibility: String,
    val reason: CouponDuplicateReason
)

data class CouponDuplicateInput(
    val title: String,
    val brand: String,
    val expiresLocalDate: LocalDate,
    val barcodeValue: String?
)

/**
 * Finds likely duplicates among coupons that the signed-in member is already allowed to read.
 * Results are warnings only because titles and OCR output can legitimately overlap.
 */
fun findPossibleCouponDuplicates(
    input: CouponDuplicateInput,
    coupons: List<Coupon>,
    limit: Int = DEFAULT_DUPLICATE_RESULT_LIMIT
): List<CouponDuplicateCandidate> {
    require(limit > 0) { "중복 후보 개수는 1개 이상이어야 합니다." }
    return coupons
        .asSequence()
        .mapNotNull { coupon -> coupon.toDuplicateCandidate(input) }
        .sortedBy { it.reason.ordinal }
        .take(limit)
        .toList()
}

private fun Coupon.toDuplicateCandidate(input: CouponDuplicateInput): CouponDuplicateCandidate? {
    if (expiresLocalDate != input.expiresLocalDate) return null
    val reason = duplicateReason(input) ?: return null
    return CouponDuplicateCandidate(
        couponId = id,
        title = title,
        brand = brand,
        expiresLocalDate = expiresLocalDate,
        visibility = visibility,
        reason = reason
    )
}

private fun Coupon.duplicateReason(input: CouponDuplicateInput): CouponDuplicateReason? {
    if (sameBarcode(input.barcodeValue, barcodeValue)) return CouponDuplicateReason.ExactBarcode
    val normalizedInputTitle = normalizeCouponText(input.title)
    if (normalizedInputTitle.isEmpty() || normalizeCouponText(title) != normalizedInputTitle) return null
    if (!brandsAreCompatible(input.brand, brand)) return null
    return CouponDuplicateReason.SameDetails
}

private fun sameBarcode(first: String?, second: String?): Boolean {
    val normalizedFirst = first?.filterNot(Char::isWhitespace).orEmpty()
    val normalizedSecond = second?.filterNot(Char::isWhitespace).orEmpty()
    return normalizedFirst.isNotEmpty() && normalizedFirst == normalizedSecond
}

private fun brandsAreCompatible(first: String, second: String): Boolean {
    val normalizedFirst = normalizeCouponText(first)
    val normalizedSecond = normalizeCouponText(second)
    return normalizedFirst.isEmpty() || normalizedSecond.isEmpty() || normalizedFirst == normalizedSecond
}

private fun normalizeCouponText(value: String): String =
    value.lowercase().filter(Char::isLetterOrDigit)
