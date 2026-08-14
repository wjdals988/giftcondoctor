package com.giftcondoctor.app.core

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlin.math.roundToInt

private val COUPON_BARCODE_FORMATS = listOf(
    BarcodeFormat.CODE_128,
    BarcodeFormat.CODE_39,
    BarcodeFormat.CODE_93,
    BarcodeFormat.CODABAR,
    BarcodeFormat.EAN_13,
    BarcodeFormat.EAN_8,
    BarcodeFormat.ITF,
    BarcodeFormat.UPC_A,
    BarcodeFormat.UPC_E,
    BarcodeFormat.QR_CODE,
    BarcodeFormat.PDF_417,
    BarcodeFormat.AZTEC,
    BarcodeFormat.DATA_MATRIX
)

fun detectCouponBarcode(bitmap: Bitmap, maxDimension: Int = 1_600): DetectedCouponBarcode? {
    require(maxDimension > 0) { "maxDimension must be positive" }
    val largestDimension = maxOf(bitmap.width, bitmap.height)
    val scaled = if (largestDimension > maxDimension) {
        val scale = maxDimension.toFloat() / largestDimension
        Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    val pixelCount = scaled.width.toLong() * scaled.height.toLong()
    if (pixelCount > Int.MAX_VALUE) {
        if (scaled !== bitmap) scaled.recycle()
        return null
    }
    val pixels = IntArray(pixelCount.toInt())
    scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
    val binary = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(scaled.width, scaled.height, pixels)))
    val reader = MultiFormatReader().apply {
        setHints(mapOf(
            DecodeHintType.POSSIBLE_FORMATS to COUPON_BARCODE_FORMATS,
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.ALSO_INVERTED to true
        ))
    }
    return runCatching {
        val result = reader.decodeWithState(binary)
        val value = result.text?.trim()?.takeIf { it.length in 1..2048 } ?: return@runCatching null
        val format = couponBarcodeFormat(result.barcodeFormat) ?: return@runCatching null
        DetectedCouponBarcode(value, format)
    }.getOrNull().also {
        reader.reset()
        if (scaled !== bitmap) scaled.recycle()
    }
}
