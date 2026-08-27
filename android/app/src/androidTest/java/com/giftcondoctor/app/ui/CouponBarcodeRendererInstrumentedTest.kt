package com.giftcondoctor.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.core.detectCouponBarcode
import com.giftcondoctor.app.core.renderCouponBarcode
import com.giftcondoctor.app.data.CouponImageLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class CouponBarcodeRendererInstrumentedTest {
    @Test
    fun rendersSupportedQrAndLinearBarcodesAndRejectsUnknownFormats() {
        assertNotNull(renderCouponBarcode("giftcondoctor://coupon/1", "QR_CODE"))
        assertNotNull(renderCouponBarcode("123456789012", "CODE_128"))
        assertNotNull(renderCouponBarcode("590123412345", "EAN_13"))
        assertNull(renderCouponBarcode("not-a-number", "EAN_13"))
        assertNull(renderCouponBarcode("1234", "UNKNOWN"))
    }

    @Test
    fun regeneratedCode128RoundTripsThroughDetector() {
        val value = "123456789012"
        val bitmap = requireNotNull(renderCouponBarcode(value, "CODE_128"))
        assertEquals(value, detectCouponBarcode(bitmap)?.value)
    }

    @Test
    fun couponAnalysisBitmapIsBoundedToRequestedDimension() {
        val source = Bitmap.createBitmap(2_400, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
        }
        val bytes = ByteArrayOutputStream().use { output ->
            source.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        source.recycle()

        val scaled = requireNotNull(
            CouponImageLoader.decodeScaledBitmap(
                streamProvider = { ByteArrayInputStream(bytes) },
                maxDimension = 1_600
            )
        )
        try {
            assertEquals(1_600, scaled.width)
            assertEquals(800, scaled.height)
        } finally {
            scaled.recycle()
        }
    }
}
