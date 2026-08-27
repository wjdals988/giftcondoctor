package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class CouponImageLoaderPerformanceInstrumentedTest {
    @After
    fun tearDown() {
        CouponImageLoader.clear()
        CouponImageLoader.resetMetrics()
    }

    @Test
    fun loads24ThumbnailsOnceAndServesSecondPassFromMemory() {
        runBlocking {
            val thumbnailCount = 24
            val payload = realisticJpegPayload()
            val runs = (1..5).map { run ->
                runThumbnailRound(run, thumbnailCount, payload)
            }
            Log.i(
                PERF_TAG,
                String.format(
                    Locale.US,
                    "summary runs=%d count=%d payloadBytes=%d downloadedBytesPerRun=%d " +
                        "medianMissMs=%.3f medianHitMs=%.3f medianAvgDecodeMs=%.3f " +
                        "bitmapCacheBytes=%d compressedCacheBytes=%d medianPssDeltaKb=%d",
                    runs.size,
                    thumbnailCount,
                    payload.size,
                    runs.first().downloadedBytes,
                    runs.map { it.missMillis }.medianDouble(),
                    runs.map { it.hitMillis }.medianDouble(),
                    runs.map { it.averageDecodeMillis }.medianDouble(),
                    runs.first().cacheBytes,
                    runs.first().compressedCacheBytes,
                    runs.map { it.pssDeltaKb }.medianLong()
                )
            )
        }
    }

    @Test
    fun coalesces24ConcurrentRequestsForTheSameThumbnail() {
        runBlocking {
            val payload = realisticJpegPayload()
            val fetchStarted = CompletableDeferred<Unit>()
            val allowFetchToFinish = CompletableDeferred<Unit>()
            val fetchCount = AtomicInteger()
            CouponImageLoader.clear()
            CouponImageLoader.resetMetrics()

            val loads = List(24) {
                async(Dispatchers.Default) {
                    CouponImageLoader.loadForInstrumentation(
                        cacheKey = "shared-coupon",
                        targetWidth = 192,
                        targetHeight = 128
                    ) {
                        fetchCount.incrementAndGet()
                        fetchStarted.complete(Unit)
                        allowFetchToFinish.await()
                        payload
                    }
                }
            }
            fetchStarted.await()
            withTimeout(5_000) {
                while (CouponImageLoader.metricsSnapshot().cacheMisses < 24) yield()
            }
            allowFetchToFinish.complete(Unit)
            loads.awaitAll().forEach(::assertNotNull)

            val snapshot = CouponImageLoader.metricsSnapshot()
            assertEquals(1, fetchCount.get())
            assertEquals(1L, snapshot.fetchOperations)
            assertEquals(1L, snapshot.decodedBitmaps)
            assertEquals(23L, snapshot.coalescedHits)
            assertEquals(0L, snapshot.loadFailures)
        }
    }

    @Test
    fun reusesCompressedThumbnailAcrossDifferentDisplaySizes() {
        runBlocking {
            val payload = realisticJpegPayload()
            CouponImageLoader.clear()
            CouponImageLoader.resetMetrics()

            val compactStartedAt = SystemClock.elapsedRealtimeNanos()
            assertNotNull(
                CouponImageLoader.loadForInstrumentation(
                    cacheKey = "responsive-coupon",
                    targetWidth = 56,
                    targetHeight = 56
                ) { payload }
            )
            val compactMillis = elapsedMillis(compactStartedAt)
            val expandedStartedAt = SystemClock.elapsedRealtimeNanos()
            assertNotNull(
                CouponImageLoader.loadForInstrumentation(
                    cacheKey = "responsive-coupon",
                    targetWidth = 512,
                    targetHeight = 360
                ) { error("표시 크기만 바뀌면 압축 bytes를 다시 받으면 안 됩니다.") }
            )
            val expandedMillis = elapsedMillis(expandedStartedAt)

            val snapshot = CouponImageLoader.metricsSnapshot()
            assertEquals(1L, snapshot.fetchOperations)
            assertEquals(1L, snapshot.compressedCacheHits)
            assertEquals(2L, snapshot.decodedBitmaps)
            assertEquals(2, snapshot.cacheEntries)
            assertEquals(1, snapshot.compressedCacheEntries)
            assertEquals(payload.size, snapshot.compressedCacheBytes)
            Log.i(
                PERF_TAG,
                String.format(
                    Locale.US,
                    "responsive payloadBytes=%d compactMs=%.3f expandedFromCompressedMs=%.3f " +
                        "fetchOperations=%d compressedCacheHits=%d",
                    payload.size,
                    compactMillis,
                    expandedMillis,
                    snapshot.fetchOperations,
                    snapshot.compressedCacheHits
                )
            )
        }
    }

    private suspend fun runThumbnailRound(
        run: Int,
        thumbnailCount: Int,
        payload: ByteArray
    ): PerformanceRound {
        CouponImageLoader.clear()
        CouponImageLoader.resetMetrics()
        Runtime.getRuntime().gc()
        SystemClock.sleep(100)

        val pssBeforeKb = Debug.getPss()
        val missStartedAt = SystemClock.elapsedRealtimeNanos()
        repeat(thumbnailCount) { index ->
            assertNotNull(
                CouponImageLoader.loadForInstrumentation(
                    cacheKey = "run-$run-coupon-$index",
                    targetWidth = 192,
                    targetHeight = 128
                ) { payload.copyOf() }
            )
        }
        val missMillis = elapsedMillis(missStartedAt)
        val afterMiss = CouponImageLoader.metricsSnapshot()
        val pssAfterMissKb = Debug.getPss()

        val hitStartedAt = SystemClock.elapsedRealtimeNanos()
        repeat(thumbnailCount) { index ->
            assertNotNull(
                CouponImageLoader.loadForInstrumentation(
                    cacheKey = "run-$run-coupon-$index",
                    targetWidth = 192,
                    targetHeight = 128
                ) { error("cache hit에서 byte provider가 호출되면 안 됩니다.") }
            )
        }
        val hitMillis = elapsedMillis(hitStartedAt)
        val afterHit = CouponImageLoader.metricsSnapshot()

        assertEquals(thumbnailCount.toLong(), afterMiss.cacheMisses)
        assertEquals(thumbnailCount.toLong(), afterMiss.fetchOperations)
        assertEquals(thumbnailCount.toLong(), afterMiss.decodedBitmaps)
        assertEquals((payload.size * thumbnailCount).toLong(), afterMiss.downloadedBytes)
        assertEquals(thumbnailCount, afterMiss.cacheEntries)
        assertEquals(192 * 108 * 4 * thumbnailCount, afterMiss.cacheBytes)
        assertEquals(thumbnailCount, afterMiss.compressedCacheEntries)
        assertEquals(payload.size * thumbnailCount, afterMiss.compressedCacheBytes)
        assertEquals(0L, afterMiss.decodeFailures)
        assertEquals(0L, afterMiss.loadFailures)
        assertEquals(thumbnailCount.toLong(), afterHit.cacheHits)
        assertEquals(afterMiss.fetchOperations, afterHit.fetchOperations)
        assertEquals(afterMiss.downloadedBytes, afterHit.downloadedBytes)
        assertEquals(afterMiss.decodedBitmaps, afterHit.decodedBitmaps)
        assertTrue("두 번째 24개 조회는 첫 디코드보다 빨라야 합니다.", hitMillis < missMillis)
        assertTrue("썸네일 캐시는 상한 24MiB를 넘으면 안 됩니다.", afterHit.cacheBytes <= 24 * 1024 * 1024)
        assertTrue(
            "압축 썸네일 캐시는 상한 8MiB를 넘으면 안 됩니다.",
            afterHit.compressedCacheBytes <= 8 * 1024 * 1024
        )

        return PerformanceRound(
            missMillis = missMillis,
            hitMillis = hitMillis,
            averageDecodeMillis = afterHit.averageDecodeMillis,
            downloadedBytes = afterHit.downloadedBytes,
            cacheBytes = afterHit.cacheBytes,
            compressedCacheBytes = afterHit.compressedCacheBytes,
            pssDeltaKb = pssAfterMissKb - pssBeforeKb
        ).also { result ->
            Log.i(
                PERF_TAG,
                String.format(
                    Locale.US,
                    "run=%d count=%d missMs=%.3f hitMs=%.3f avgDecodeMs=%.3f " +
                        "downloadedBytes=%d bitmapCacheBytes=%d compressedCacheBytes=%d pssDeltaKb=%d",
                    run,
                    thumbnailCount,
                    result.missMillis,
                    result.hitMillis,
                    result.averageDecodeMillis,
                    result.downloadedBytes,
                    result.cacheBytes,
                    result.compressedCacheBytes,
                    result.pssDeltaKb
                )
            )
        }
    }

    private fun realisticJpegPayload(): ByteArray {
        val width = 1280
        val height = 720
        val pixels = IntArray(width * height) { offset ->
            val x = offset % width
            val y = offset / width
            val blockNoise = ((x / 13) * 37 + (y / 11) * 19) and 0xff
            Color.rgb(
                (x * 255 / width + blockNoise / 3) and 0xff,
                (y * 255 / height + blockNoise / 2) and 0xff,
                ((x + y) / 8 + blockNoise) and 0xff
            )
        }
        val bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 88, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private fun elapsedMillis(startedAtNanos: Long): Double =
        (SystemClock.elapsedRealtimeNanos() - startedAtNanos) / 1_000_000.0

    private fun List<Double>.medianDouble(): Double = sorted()[size / 2]

    private fun List<Long>.medianLong(): Long = sorted()[size / 2]

    private data class PerformanceRound(
        val missMillis: Double,
        val hitMillis: Double,
        val averageDecodeMillis: Double,
        val downloadedBytes: Long,
        val cacheBytes: Int,
        val compressedCacheBytes: Int,
        val pssDeltaKb: Long
    )

    private companion object {
        const val PERF_TAG = "CouponImagePerf"
    }
}
