package com.giftcondoctor.app.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import com.giftcondoctor.app.core.bitmapSampleSize
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CouponImageLoader {
    private val repository by lazy { CouponRepository() }
    private val cache = object : LruCache<String, Bitmap>(cacheSizeBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    suspend fun load(
        roomId: String,
        couponId: String,
        imageBlobPath: String,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
        val cacheKey = "$uid/$roomId/$couponId/$imageBlobPath@$targetWidth:$targetHeight"
        cache.get(cacheKey)?.let { return@withContext it }

        val bytes = repository.fetchImage(roomId, couponId)
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

    private fun cacheSizeBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory()
        return minOf(maxMemory / 16, 24L * 1024 * 1024)
            .coerceAtLeast(4L * 1024 * 1024)
            .toInt()
    }
}
