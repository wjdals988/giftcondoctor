package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.LruCache
import com.giftcondoctor.app.core.bitmapSampleSize
import com.giftcondoctor.app.core.fitImageDimensions
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object CouponImageLoader {
    private val repository by lazy { CouponRepository() }
    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(
        roomId: String,
        couponId: String,
        imageBlobPath: String,
        thumbnailBlobPath: String?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val requestedPath = thumbnailBlobPath ?: imageBlobPath
        val cacheKey = "$uid/$roomId/$couponId/$requestedPath@$targetWidth:$targetHeight"
        cache.get(cacheKey)?.let { return@withContext it }

        val bytes = repository.fetchImage(roomId, couponId, thumbnail = thumbnailBlobPath != null)
        decodeSampledBitmap(bytes, targetWidth, targetHeight)?.also { bitmap ->
            cache.put(cacheKey, bitmap)
        }
    }

    fun clear() {
        cache.evictAll()
    }

    fun decodeSampledBitmap(bytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    fun decodeSampledBitmap(
        streamProvider: () -> InputStream?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        streamProvider()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return streamProvider()?.use { BitmapFactory.decodeStream(it, null, options) }
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

    private fun cacheSizeBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory()
        return minOf(maxMemory / 16, 24L * 1024 * 1024)
            .coerceAtLeast(4L * 1024 * 1024)
            .toInt()
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
