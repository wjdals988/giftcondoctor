package com.giftcondoctor.app.core

import com.google.zxing.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CouponBarcodeTest {
    @Test
    fun mapsDetectedFormatsToStableStorageAndRendererValues() {
        assertEquals("CODE_128", couponBarcodeFormat(BarcodeFormat.CODE_128))
        assertEquals("QR_CODE", couponBarcodeFormat(BarcodeFormat.QR_CODE))
        assertEquals(BarcodeFormat.CODE_128, zxingBarcodeFormat("CODE_128"))
        assertEquals(BarcodeFormat.QR_CODE, zxingBarcodeFormat("QR_CODE"))
        assertNull(couponBarcodeFormat(BarcodeFormat.MAXICODE))
        assertNull(zxingBarcodeFormat("UNKNOWN"))
    }

    @Test
    fun masksLongBarcodeValuesButKeepsShortValuesReadable() {
        assertEquals("12345678", barcodeValuePreview("12345678"))
        assertEquals("•••• 7890", barcodeValuePreview("8801234567890"))
    }

    @Test
    fun validatesLinearBarcodeLengthsAndNumericRetailFormats() {
        assertNull(couponBarcodeValidationError("123456789012", "CODE_128"))
        assertEquals(
            "1차원 바코드 값은 80자 이하여야 합니다.",
            couponBarcodeValidationError("1".repeat(81), "CODE_128")
        )
        assertNull(couponBarcodeValidationError("590123412345", "EAN_13"))
        assertEquals(
            "EAN-13은 숫자 12~13자리여야 합니다.",
            couponBarcodeValidationError("not-a-number", "EAN_13")
        )
    }

    @Test
    fun boundsTwoDimensionalBarcodeValues() {
        assertNull(couponBarcodeValidationError("a".repeat(1_024), "QR_CODE"))
        assertEquals(
            "2차원 바코드 값은 1,024자 이하여야 합니다.",
            couponBarcodeValidationError("a".repeat(1_025), "QR_CODE")
        )
    }

}
