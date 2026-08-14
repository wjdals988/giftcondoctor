package com.giftcondoctor.app.core

import com.google.zxing.BarcodeFormat

data class DetectedCouponBarcode(
    val value: String,
    val format: String
)

fun couponBarcodeFormat(format: BarcodeFormat): String? = when (format) {
    BarcodeFormat.CODE_128 -> "CODE_128"
    BarcodeFormat.CODE_39 -> "CODE_39"
    BarcodeFormat.CODE_93 -> "CODE_93"
    BarcodeFormat.CODABAR -> "CODABAR"
    BarcodeFormat.EAN_13 -> "EAN_13"
    BarcodeFormat.EAN_8 -> "EAN_8"
    BarcodeFormat.ITF -> "ITF"
    BarcodeFormat.UPC_A -> "UPC_A"
    BarcodeFormat.UPC_E -> "UPC_E"
    BarcodeFormat.QR_CODE -> "QR_CODE"
    BarcodeFormat.PDF_417 -> "PDF_417"
    BarcodeFormat.AZTEC -> "AZTEC"
    BarcodeFormat.DATA_MATRIX -> "DATA_MATRIX"
    else -> null
}

fun zxingBarcodeFormat(format: String): BarcodeFormat? = when (format) {
    "CODE_128" -> BarcodeFormat.CODE_128
    "CODE_39" -> BarcodeFormat.CODE_39
    "CODE_93" -> BarcodeFormat.CODE_93
    "CODABAR" -> BarcodeFormat.CODABAR
    "EAN_13" -> BarcodeFormat.EAN_13
    "EAN_8" -> BarcodeFormat.EAN_8
    "ITF" -> BarcodeFormat.ITF
    "UPC_A" -> BarcodeFormat.UPC_A
    "UPC_E" -> BarcodeFormat.UPC_E
    "QR_CODE" -> BarcodeFormat.QR_CODE
    "PDF_417" -> BarcodeFormat.PDF_417
    "AZTEC" -> BarcodeFormat.AZTEC
    "DATA_MATRIX" -> BarcodeFormat.DATA_MATRIX
    else -> null
}

fun barcodeValuePreview(value: String): String {
    val trimmed = value.trim()
    if (trimmed.length <= 8) return trimmed
    return "•••• ${trimmed.takeLast(4)}"
}

fun couponBarcodeValidationError(value: String, format: String): String? {
    val normalized = value.trim()
    if (normalized.isEmpty()) return "바코드 값을 입력해 주세요."
    if (zxingBarcodeFormat(format) == null) return "지원하지 않는 바코드 형식입니다."
    return when (format) {
        "EAN_13" -> if (normalized.matches(Regex("^[0-9]{12,13}$"))) null else "EAN-13은 숫자 12~13자리여야 합니다."
        "EAN_8" -> if (normalized.matches(Regex("^[0-9]{7,8}$"))) null else "EAN-8은 숫자 7~8자리여야 합니다."
        "UPC_A" -> if (normalized.matches(Regex("^[0-9]{11,12}$"))) null else "UPC-A는 숫자 11~12자리여야 합니다."
        "UPC_E" -> if (normalized.matches(Regex("^[0-9]{7,8}$"))) null else "UPC-E는 숫자 7~8자리여야 합니다."
        "CODE_128", "CODE_39", "CODE_93", "CODABAR", "ITF" ->
            if (normalized.length <= 80) null else "1차원 바코드 값은 80자 이하여야 합니다."
        "QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX" ->
            if (normalized.length <= 1_024) null else "2차원 바코드 값은 1,024자 이하여야 합니다."
        else -> "지원하지 않는 바코드 형식입니다."
    }
}
