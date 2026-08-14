package com.giftcondoctor.app.core

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

fun renderCouponBarcode(value: String, format: String): Bitmap? {
    if (couponBarcodeValidationError(value, format) != null) return null
    val barcodeFormat = zxingBarcodeFormat(format) ?: return null
    val twoDimensional = format in setOf("QR_CODE", "PDF_417", "AZTEC", "DATA_MATRIX")
    val width = if (twoDimensional) 960 else 1_400
    val height = if (twoDimensional) 960 else 480
    return runCatching {
        val matrix = MultiFormatWriter().encode(
            value,
            barcodeFormat,
            width,
            height,
            mapOf(EncodeHintType.MARGIN to if (twoDimensional) 3 else 12)
        )
        val pixelCount = matrix.width.toLong() * matrix.height.toLong()
        if (pixelCount > 2_000_000L) return@runCatching null
        val pixels = IntArray(pixelCount.toInt())
        for (y in 0 until matrix.height) {
            val rowOffset = y * matrix.width
            for (x in 0 until matrix.width) {
                pixels[rowOffset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, matrix.width, 0, 0, matrix.width, matrix.height)
        }
    }.getOrNull()
}
