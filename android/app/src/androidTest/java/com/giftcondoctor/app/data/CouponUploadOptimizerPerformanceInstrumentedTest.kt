package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.detectCouponBarcode
import com.giftcondoctor.app.core.renderCouponBarcode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class CouponUploadOptimizerPerformanceInstrumentedTest {
    @Test
    fun unknownLengthSmallImageIsCopiedWithoutLossyRecompression() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = File(context.cacheDir, "upload-optimizer-unknown-small.jpg")
            val bitmap = Bitmap.createBitmap(640, 320, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.WHITE)
            }
            source.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output))
            }
            bitmap.recycle()
            val sourceLength = source.length()

            val prepared = CouponUploadOptimizer.prepare(
                context = context,
                uri = Uri.fromFile(source),
                contentType = "image/jpeg",
                fileName = source.name,
                sourceBytes = null
            )
            source.delete()
            try {
                assertFalse(prepared.preparation.optimized)
                assertEquals(sourceLength, prepared.contentLength)
                assertEquals(sourceLength, prepared.openStream()?.use { it.readBytes().size.toLong() })
            } finally {
                prepared.close()
            }
            assertTrue(runCatching { prepared.openStream()?.close() }.isFailure)
        }
    }

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

    @Test
    fun noisyBarcodeImageFitsFunctionPayloadBudgetAndRemainsScannable() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val source = File(context.cacheDir, "upload-optimizer-noisy-barcode.jpg")
            val q92Baseline = File(context.cacheDir, "upload-optimizer-noisy-barcode-q92.jpg")
            val barcodeValue = "123456789012"
            try {
                writeNoisyBarcodeJpeg(source, barcodeValue)
                assertTrue(source.length() > AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES)
                assertTrue(source.length() <= AppConstants.MAX_IMAGE_BYTES)
                val sourceBitmap = requireNotNull(BitmapFactory.decodeFile(source.absolutePath))
                try {
                    q92Baseline.outputStream().buffered(64 * 1024).use { output ->
                        check(sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
                    }
                } finally {
                    sourceBitmap.recycle()
                }
                val q92Bytes = q92Baseline.length()
                assertTrue(q92Bytes > AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES)

                val startedAt = SystemClock.elapsedRealtimeNanos()
                val prepared = CouponUploadOptimizer.prepare(
                    context = context,
                    uri = Uri.fromFile(source),
                    contentType = "image/jpeg",
                    fileName = source.name,
                    sourceBytes = source.length()
                )
                val prepareMillis = elapsedMillis(startedAt)
                try {
                    val uploadBytes = checkNotNull(prepared.contentLength)
                    val preparedBitmap = requireNotNull(
                        prepared.openStream()?.use { BitmapFactory.decodeStream(it) }
                    )
                    try {
                        assertTrue(prepared.preparation.optimized)
                        assertTrue(uploadBytes <= AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES)
                        assertTrue(maxOf(preparedBitmap.width, preparedBitmap.height) >= 1_600)
                        assertEquals(barcodeValue, detectCouponBarcode(preparedBitmap)?.value)
                        Log.i(
                            PERF_TAG,
                            String.format(
                                Locale.US,
                                "adaptiveSourceBytes=%d q92Bytes=%d uploadBytes=%d budgetBytes=%d " +
                                    "prepareMs=%.3f output=%dx%d",
                                source.length(),
                                q92Bytes,
                                uploadBytes,
                                AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES,
                                prepareMillis,
                                preparedBitmap.width,
                                preparedBitmap.height
                            )
                        )
                    } finally {
                        preparedBitmap.recycle()
                    }
                } finally {
                    prepared.close()
                }
            } finally {
                q92Baseline.delete()
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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        destination.outputStream().buffered(64 * 1024).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()
    }

    private fun writeNoisyBarcodeJpeg(destination: File, barcodeValue: String) {
        val width = 2_560
        val height = 2_560
        var seed = 0x13579bdf
        val pixels = IntArray(width * height) {
            seed = seed * 1_103_515_245 + 12_345
            Color.rgb(seed ushr 16 and 0xff, seed ushr 8 and 0xff, seed and 0xff)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        val barcode = requireNotNull(renderCouponBarcode(barcodeValue, "CODE_128"))
        Canvas(bitmap).apply {
            drawRect(480f, 940f, 2_080f, 1_620f, Paint().apply { color = Color.WHITE })
            drawBitmap(
                barcode,
                null,
                Rect(580, 1_040, 1_980, 1_520),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        barcode.recycle()
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
