package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CouponImageFilePerformanceInstrumentedTest {
    @After
    fun tearDown() {
        CouponImageFileStore.clearTracked()
    }

    @Test
    fun decodesLargeDetailImageFromPrivateCacheFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = CouponImageFileStore.create(context)
        writeLargeJpeg(destination)
        val source = CouponImageFileStore.complete(destination)
        Runtime.getRuntime().gc()
        SystemClock.sleep(100)

        val pssBeforeKb = Debug.getPss()
        val previewStartedAt = SystemClock.elapsedRealtimeNanos()
        val preview = CouponImageLoader.decodeSampledBitmap(source.file, 1_080, 480)
        val previewMillis = elapsedMillis(previewStartedAt)
        assertNotNull(preview)

        val zoomStartedAt = SystemClock.elapsedRealtimeNanos()
        val zoom = CouponImageLoader.decodeZoomBitmap(
            file = source.file,
            maxWidth = 2_160,
            maxHeight = 3_840
        )
        val zoomMillis = elapsedMillis(zoomStartedAt)
        assertNotNull(zoom)
        val pssAfterKb = Debug.getPss()

        checkNotNull(preview)
        checkNotNull(zoom)
        assertTrue(source.byteCount in 1_000_000L..10L * 1024L * 1024L)
        assertEquals(720, preview.width)
        assertEquals(480, preview.height)
        assertTrue(zoom.width.toLong() * zoom.height <= 8_000_000L)
        Log.i(
            PERF_TAG,
            String.format(
                Locale.US,
                "sourceBytes=%d previewMs=%.3f zoomMs=%.3f previewBitmapBytes=%d " +
                    "zoomBitmapBytes=%d pssDeltaKb=%d",
                source.byteCount,
                previewMillis,
                zoomMillis,
                preview.byteCount,
                zoom.byteCount,
                pssAfterKb - pssBeforeKb
            )
        )

        preview.recycle()
        zoom.recycle()
        CouponImageFileStore.delete(source)
        assertFalse(destination.exists())
    }

    private fun writeLargeJpeg(destination: java.io.File) {
        val width = 3_000
        val height = 2_000
        val pixels = IntArray(width * height) { offset ->
            val x = offset % width
            val y = offset / width
            val noise = ((x * 73) xor (y * 151) xor (offset * 17)) and 0xff
            Color.rgb(
                (x * 255 / width + noise) and 0xff,
                (y * 255 / height + noise / 2) and 0xff,
                ((x + y) / 7 + noise) and 0xff
            )
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        destination.outputStream().buffered(64 * 1024).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
        }
        bitmap.recycle()
    }

    private fun elapsedMillis(startedAt: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0

    private companion object {
        const val PERF_TAG = "CouponImageFilePerf"
    }
}
