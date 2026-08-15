package com.giftcondoctor.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.giftcondoctor.app.core.couponUploadOptimizationPlan
import com.giftcondoctor.app.core.fitImageDimensions
import com.giftcondoctor.app.core.shouldUseOptimizedCouponUpload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

data class CouponUploadPreparation(
    val originalBytes: Long?,
    val uploadBytes: Long?,
    val optimized: Boolean
)

class PreparedCouponUpload private constructor(
    val contentType: String,
    val fileName: String,
    val contentLength: Long?,
    val preparation: CouponUploadPreparation,
    private val openSource: () -> InputStream?,
    private val temporaryFile: File?
) : Closeable {
    fun openStream(): InputStream? = openSource()

    override fun close() {
        temporaryFile?.let(CouponUploadOptimizer::delete)
    }

    companion object {
        fun original(
            context: Context,
            uri: Uri,
            contentType: String,
            fileName: String,
            sourceBytes: Long?
        ) = PreparedCouponUpload(
            contentType = contentType,
            fileName = fileName,
            contentLength = sourceBytes,
            preparation = CouponUploadPreparation(sourceBytes, sourceBytes, optimized = false),
            openSource = { context.contentResolver.openInputStream(uri) },
            temporaryFile = null
        )

        fun optimized(file: File, sourceBytes: Long?, fileName: String) = PreparedCouponUpload(
            contentType = "image/jpeg",
            fileName = "${fileName.substringBeforeLast('.', fileName).ifBlank { "coupon-image" }}.jpg",
            contentLength = file.length(),
            preparation = CouponUploadPreparation(sourceBytes, file.length(), optimized = true),
            openSource = { file.inputStream() },
            temporaryFile = file
        )
    }
}

/**
 * Uses a caller-owned upload without closing it so the caller can retry.
 * An upload created by [prepare] is owned here and always closed after [block].
 */
internal suspend fun <T> withPreparedCouponUpload(
    borrowedUpload: PreparedCouponUpload?,
    prepare: suspend () -> PreparedCouponUpload,
    block: suspend (PreparedCouponUpload) -> T
): T {
    val upload = borrowedUpload ?: prepare()
    return try {
        block(upload)
    } finally {
        if (borrowedUpload == null) upload.close()
    }
}

object CouponUploadOptimizer {
    private const val DIRECTORY_NAME = "coupon-upload-prepared"
    private const val JPEG_QUALITY = 92
    private const val MAX_INPUT_PIXELS = 40_000_000L
    private val abandonedFilesPurged = AtomicBoolean(false)

    fun purgeAbandonedOnce(context: Context) {
        if (!abandonedFilesPurged.compareAndSet(false, true)) return
        uploadDirectory(context).listFiles()?.forEach(::delete)
    }

    internal suspend fun prepare(
        context: Context,
        uri: Uri,
        contentType: String,
        fileName: String,
        sourceBytes: Long?
    ): PreparedCouponUpload = withContext(Dispatchers.IO) {
        purgeAbandonedOnce(context)
        val original = PreparedCouponUpload.original(context, uri, contentType, fileName, sourceBytes)
        val bounds = imageBounds(context, uri) ?: return@withContext original
        val sourcePixels = bounds.first.toLong() * bounds.second.toLong()
        if (sourcePixels > MAX_INPUT_PIXELS) {
            throw IOException("이미지 해상도가 너무 큽니다. 4천만 화소 이하 이미지를 선택해 주세요.")
        }
        val requiresTranscode = contentType !in SERVER_SUPPORTED_CONTENT_TYPES
        val plan = couponUploadOptimizationPlan(
            sourceWidth = bounds.first,
            sourceHeight = bounds.second,
            sourceBytes = sourceBytes,
            requiresTranscode = requiresTranscode
        )
        if (!plan.shouldOptimize) return@withContext original

        coroutineContext.ensureActive()
        val output = File.createTempFile("coupon-upload-", ".jpg", uploadDirectory(context))
        try {
            val decoded = decodeForUpload(context, uri, plan.targetWidth, plan.targetHeight)
                ?: return@withContext original.also { delete(output) }
            val opaque = decoded.withWhiteBackgroundIfNeeded()
            try {
                output.outputStream().buffered(64 * 1024).use { stream ->
                    if (!opaque.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                        throw IOException("이미지 최적화에 실패했습니다.")
                    }
                }
            } finally {
                if (opaque !== decoded) opaque.recycle()
                decoded.recycle()
            }
            coroutineContext.ensureActive()
            if (!shouldUseOptimizedCouponUpload(sourceBytes, output.length(), requiresTranscode)) {
                delete(output)
                return@withContext original
            }
            PreparedCouponUpload.optimized(output, sourceBytes, fileName)
        } catch (error: Exception) {
            delete(output)
            if (error is kotlinx.coroutines.CancellationException) throw error
            original
        }
    }

    internal fun delete(file: File) {
        if (file.delete() || !file.exists()) return
        runCatching { file.outputStream().use { } }
        file.delete()
    }

    private fun imageBounds(context: Context, uri: Uri): Pair<Int, Int>? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        return if (options.outWidth > 0 && options.outHeight > 0) {
            options.outWidth to options.outHeight
        } else {
            null
        }
    }

    private fun decodeForUpload(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val target = fitImageDimensions(
                    sourceWidth = info.size.width,
                    sourceHeight = info.size.height,
                    maxWidth = targetWidth,
                    maxHeight = targetHeight,
                    maxPixels = targetWidth.toLong() * targetHeight
                )
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
                decoder.setTargetSize(target.width, target.height)
            }
        }

        val decoded = CouponImageLoader.decodeSampledBitmap(
            streamProvider = { context.contentResolver.openInputStream(uri) },
            targetWidth = targetWidth,
            targetHeight = targetHeight
        ) ?: return null
        return decoded.applyExifOrientation(context, uri)
    }

    private fun Bitmap.applyExifOrientation(context: Context, uri: Uri): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return this
        }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true).also { rotated ->
            if (rotated !== this) recycle()
        }
    }

    private fun Bitmap.withWhiteBackgroundIfNeeded(): Bitmap {
        if (!hasAlpha()) return this
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { opaque ->
            Canvas(opaque).apply {
                drawColor(Color.WHITE)
                drawBitmap(this@withWhiteBackgroundIfNeeded, 0f, 0f, null)
            }
        }
    }

    private fun uploadDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    private val SERVER_SUPPORTED_CONTENT_TYPES = setOf("image/jpeg", "image/png", "image/webp")
}
