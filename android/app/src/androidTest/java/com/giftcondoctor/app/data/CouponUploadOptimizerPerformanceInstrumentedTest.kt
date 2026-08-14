package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CouponUploadOptimizerPerformanceInstrumentedTest {
    @Test
    fun largeImageIsPreparedAsSmallerBoundedJpeg() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = File(context.cacheDir, "upload-optimizer-source.jpg")
            writeLargeJpeg(source)
            Runtime.getRuntime().gc()
            SystemClock.sleep(100)

            val pssBeforeKb = Debug.getPss()
            val startedAt = SystemClock.elapsedRealtimeNanos()
            val prepared = CouponUploadOptimizer.prepare(
                context = context,
                uri = Uri.fromFile(source),
                contentType = "image/jpeg",
                fileName = source.name,
                sourceBytes = source.length()
            )
            val prepareMillis = elapsedMillis(startedAt)
            val pssAfterKb = Debug.getPss()
            try {
                val uploadBytes = checkNotNull(prepared.contentLength)
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                prepared.openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }

                assertTrue(prepared.preparation.optimized)
                assertEquals("image/jpeg", prepared.contentType)
                assertTrue(uploadBytes * 100L <= source.length() * 90L)
                assertTrue(bounds.outWidth <= 2_560)
                assertTrue(bounds.outHeight <= 2_560)
                Log.i(
                    PERF_TAG,
                    String.format(
                        Locale.US,
                        "sourceBytes=%d uploadBytes=%d savingPercent=%.1f prepareMs=%.3f pssDeltaKb=%d output=%dx%d",
                        source.length(),
                        uploadBytes,
                        100.0 * (source.length() - uploadBytes) / source.length(),
                        prepareMillis,
                        pssAfterKb - pssBeforeKb,
                        bounds.outWidth,
                        bounds.outHeight
                    )
                )
            } finally {
                prepared.close()
                assertTrue(runCatching { prepared.openStream()?.close() }.isFailure)
                source.delete()
            }
        }
    }

    private fun writeLargeJpeg(destination: File) {
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
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
    }

    private fun elapsedMillis(startedAt: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAt) / 1_000_000.0

    private companion object {
        const val PERF_TAG = "CouponUploadOptimizerPerf"
    }
}
