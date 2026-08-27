package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.SystemClock
import android.util.LruCache
import com.giftcondoctor.app.core.bitmapSampleSize
import com.giftcondoctor.app.core.fitImageDimensions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.math.roundToInt

data class CouponImageLoadMetricsSnapshot(
    val cacheHits: Long,
    val cacheMisses: Long,
    val coalescedHits: Long,
    val compressedCacheHits: Long,
    val fetchOperations: Long,
    val downloadedBytes: Long,
    val decodedBitmaps: Long,
    val decodedBitmapBytes: Long,
    val totalDecodeMillis: Double,
    val decodeFailures: Long,
    val loadFailures: Long,
    val cacheEntries: Int,
    val cacheBytes: Int,
    val compressedCacheEntries: Int,
    val compressedCacheBytes: Int
) {
    val averageDecodeMillis: Double
        get() = if (decodedBitmaps == 0L) 0.0 else totalDecodeMillis / decodedBitmaps
}

object CouponImageLoader {
    private class LoadGate(
        val mutex: Mutex = Mutex(),
        var users: Int = 0
    )

    private class Metrics {
        val cacheHits = AtomicLong()
        val cacheMisses = AtomicLong()
        val coalescedHits = AtomicLong()
        val compressedCacheHits = AtomicLong()
        val fetchOperations = AtomicLong()
        val downloadedBytes = AtomicLong()
        val decodedBitmaps = AtomicLong()
        val decodedBitmapBytes = AtomicLong()
        val decodeNanos = AtomicLong()
        val decodeFailures = AtomicLong()
        val loadFailures = AtomicLong()

        fun reset() {
            cacheHits.set(0)
            cacheMisses.set(0)
            coalescedHits.set(0)
            compressedCacheHits.set(0)
            fetchOperations.set(0)
            downloadedBytes.set(0)
            decodedBitmaps.set(0)
            decodedBitmapBytes.set(0)
            decodeNanos.set(0)
            decodeFailures.set(0)
            loadFailures.set(0)
        }
    }

    private val repository by lazy { CouponRepository() }
    private val cache = object : LruCache<String, Bitmap>(bitmapCacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val compressedCache = object : LruCache<String, ByteArray>(compressedCacheSizeBytes()) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val loadGates = ConcurrentHashMap<String, LoadGate>()
    private val metrics = Metrics()

    suspend fun load(
        roomId: String,
        couponId: String,
        imageBlobPath: String,
        thumbnailBlobPath: String?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val sourceKey = "$uid/$roomId/$couponId/thumbnail:$imageBlobPath:$thumbnailBlobPath"
        loadCached(sourceKey, targetWidth, targetHeight) {
            if (thumbnailBlobPath != null) {
                repository.fetchImage(roomId, couponId, thumbnail = true)
            } else {
                runCatching {
                    repository.fetchImage(
                        roomId,
                        couponId,
                        thumbnail = true,
                        backfillThumbnail = true
                    )
                }.getOrElse {
                    repository.fetchImage(roomId, couponId)
                }
            }
        }
    }

    fun clear() {
        cache.evictAll()
        compressedCache.evictAll()
    }

    fun resetMetrics() {
        metrics.reset()
    }

    fun metricsSnapshot(): CouponImageLoadMetricsSnapshot = CouponImageLoadMetricsSnapshot(
        cacheHits = metrics.cacheHits.get(),
        cacheMisses = metrics.cacheMisses.get(),
        coalescedHits = metrics.coalescedHits.get(),
        compressedCacheHits = metrics.compressedCacheHits.get(),
        fetchOperations = metrics.fetchOperations.get(),
        downloadedBytes = metrics.downloadedBytes.get(),
        decodedBitmaps = metrics.decodedBitmaps.get(),
        decodedBitmapBytes = metrics.decodedBitmapBytes.get(),
        totalDecodeMillis = metrics.decodeNanos.get() / 1_000_000.0,
        decodeFailures = metrics.decodeFailures.get(),
        loadFailures = metrics.loadFailures.get(),
        cacheEntries = cache.snapshot().size,
        cacheBytes = cache.size(),
        compressedCacheEntries = compressedCache.snapshot().size,
        compressedCacheBytes = compressedCache.size()
    )

    /** Uses the production cache, lock and decode path without Firebase or HTTP. */
    internal suspend fun loadForInstrumentation(
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int,
        fetchBytes: suspend () -> ByteArray
    ): Bitmap? = withContext(Dispatchers.IO) {
        loadCached("instrumentation/$cacheKey", targetWidth, targetHeight, fetchBytes)
    }

    fun decodeSampledBitmap(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            ?.scaleDownToFit(targetWidth, targetHeight)
    }

    fun decodeSampledBitmap(
        streamProvider: () -> InputStream?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        streamProvider()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return streamProvider()?.use { BitmapFactory.decodeStream(it, null, options) }
            ?.scaleDownToFit(targetWidth, targetHeight)
    }

    fun decodeSampledBitmap(file: File, targetWidth: Int, targetHeight: Int): Bitmap? {
        require(targetWidth > 0 && targetHeight > 0) { "target dimensions must be positive" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = fitImageDimensions(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            maxWidth = targetWidth,
            maxHeight = targetHeight,
            maxPixels = targetWidth.toLong() * targetHeight
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.setTargetSize(target.width, target.height)
            }
        } else {
            decodeSampledBitmap(
                streamProvider = { file.inputStream() },
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )
        }
    }

    fun decodeScaledBitmap(
        streamProvider: () -> InputStream?,
        maxDimension: Int
    ): Bitmap? {
        require(maxDimension > 0) { "maxDimension must be positive" }
        val decoded = decodeSampledBitmap(streamProvider, maxDimension, maxDimension) ?: return null
        val largestDimension = maxOf(decoded.width, decoded.height)
        if (largestDimension <= maxDimension) return decoded

        val scale = maxDimension.toFloat() / largestDimension
        return Bitmap.createScaledBitmap(
            decoded,
            (decoded.width * scale).roundToInt().coerceAtLeast(1),
            (decoded.height * scale).roundToInt().coerceAtLeast(1),
            true
        ).also { scaled ->
            if (scaled !== decoded) decoded.recycle()
        }
    }

    fun decodeZoomBitmap(
        bytes: ByteArray,
        maxWidth: Int,
        maxHeight: Int,
        maxPixels: Long = 8_000_000L
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = fitImageDimensions(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxPixels = maxPixels
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, _, _ ->
                decoder.setTargetSize(target.width, target.height)
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = safeSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }

    fun decodeZoomBitmap(
        file: File,
        maxWidth: Int,
        maxHeight: Int,
        maxPixels: Long = 8_000_000L
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val target = fitImageDimensions(
            sourceWidth = bounds.outWidth,
            sourceHeight = bounds.outHeight,
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            maxPixels = maxPixels
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.setTargetSize(target.width, target.height)
            }
        } else {
            val options = BitmapFactory.Options().apply {
                inSampleSize = safeSampleSize(bounds.outWidth, bounds.outHeight, maxPixels)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, options) }
        }
    }

    private fun bitmapCacheSizeBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory()
        return minOf(maxMemory / 16, 24L * 1024 * 1024)
            .coerceAtLeast(4L * 1024 * 1024)
            .toInt()
    }

    private suspend fun loadCached(
        sourceKey: String,
        targetWidth: Int,
        targetHeight: Int,
        fetchBytes: suspend () -> ByteArray
    ): Bitmap? {
        val cacheKey = "$sourceKey@$targetWidth:$targetHeight"
        cache.get(cacheKey)?.let {
            metrics.cacheHits.incrementAndGet()
            return it
        }
        metrics.cacheMisses.incrementAndGet()

        val gate = acquireGate(sourceKey)
        return try {
            gate.mutex.withLock {
                cache.get(cacheKey)?.let {
                    metrics.coalescedHits.incrementAndGet()
                    return@withLock it
                }

                val bytes = compressedCache.get(sourceKey)?.also {
                    metrics.compressedCacheHits.incrementAndGet()
                } ?: try {
                    metrics.fetchOperations.incrementAndGet()
                    fetchBytes().also { fetched ->
                        metrics.downloadedBytes.addAndGet(fetched.size.toLong())
                        compressedCache.put(sourceKey, fetched)
                    }
                } catch (error: Throwable) {
                    metrics.loadFailures.incrementAndGet()
                    throw error
                }

                val decodeStartedAt = SystemClock.elapsedRealtimeNanos()
                val bitmap = decodeSampledBitmap(bytes, targetWidth, targetHeight)
                metrics.decodeNanos.addAndGet(SystemClock.elapsedRealtimeNanos() - decodeStartedAt)
                if (bitmap == null) {
                    compressedCache.remove(sourceKey)
                    metrics.decodeFailures.incrementAndGet()
                    return@withLock null
                }

                metrics.decodedBitmaps.incrementAndGet()
                metrics.decodedBitmapBytes.addAndGet(bitmap.byteCount.toLong())
                cache.put(cacheKey, bitmap)
                bitmap
            }
        } finally {
            releaseGate(sourceKey, gate)
        }
    }

    private fun compressedCacheSizeBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory()
        return minOf(maxMemory / 32, 8L * 1024 * 1024)
            .coerceAtLeast(2L * 1024 * 1024)
            .toInt()
    }

    private fun Bitmap.scaleDownToFit(targetWidth: Int, targetHeight: Int): Bitmap {
        if (width <= targetWidth && height <= targetHeight) return this
        val scale = min(targetWidth.toFloat() / width, targetHeight.toFloat() / height)
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (scaled !== this) recycle()
        return scaled
    }

    private fun acquireGate(cacheKey: String): LoadGate = loadGates.compute(cacheKey) { _, current ->
        (current ?: LoadGate()).also { it.users += 1 }
    } ?: error("Failed to acquire image load gate")

    private fun releaseGate(cacheKey: String, gate: LoadGate) {
        loadGates.compute(cacheKey) { _, current ->
            if (current !== gate) return@compute current
            gate.users -= 1
            if (gate.users == 0) null else gate
        }
    }

    private fun safeSampleSize(sourceWidth: Int, sourceHeight: Int, maxPixels: Long): Int {
        var sample = 1
        while ((sourceWidth / sample).coerceAtLeast(1).toLong() *
            (sourceHeight / sample).coerceAtLeast(1).toLong() > maxPixels
        ) {
            sample *= 2
        }
        return sample
    }
}
