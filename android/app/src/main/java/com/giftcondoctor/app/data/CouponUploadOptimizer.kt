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
import com.giftcondoctor.app.core.AppConstants
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
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

        fun original(file: File, contentType: String, fileName: String) = PreparedCouponUpload(
            contentType = contentType,
            fileName = fileName,
            contentLength = file.length(),
            preparation = CouponUploadPreparation(file.length(), file.length(), optimized = false),
            openSource = { file.inputStream() },
            temporaryFile = file
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
    private const val MIN_JPEG_QUALITY = 80
    private const val JPEG_QUALITY_STEP = 4
    private const val MAX_RESIZE_ATTEMPTS = 3
    private const val RESIZE_HEADROOM = 0.92
    private val abandonedFilesPurged = AtomicBoolean(false)

    private data class UploadSource(
        val uri: Uri,
        val bytes: Long,
        val original: PreparedCouponUpload
    )

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
        val requiresTranscode = contentType !in SERVER_SUPPORTED_CONTENT_TYPES
        val source = resolveUploadSource(context, uri, contentType, fileName, sourceBytes)
        val bounds = imageBounds(context, source.uri)
            ?: return@withContext source.originalOrThrow(requiresTranscode)
        requireSupportedPixelCount(bounds, source.original)
        val plan = couponUploadOptimizationPlan(
            sourceWidth = bounds.first,
            sourceHeight = bounds.second,
            sourceBytes = source.bytes,
            requiresTranscode = requiresTranscode
        )
        if (!plan.shouldOptimize) return@withContext source.original

        optimizeUpload(context, source, fileName, requiresTranscode, plan.targetWidth, plan.targetHeight)
    }

    private suspend fun resolveUploadSource(
        context: Context,
        uri: Uri,
        contentType: String,
        fileName: String,
        sourceBytes: Long?
    ): UploadSource {
        if (sourceBytes != null) {
            return UploadSource(
                uri = uri,
                bytes = sourceBytes,
                original = PreparedCouponUpload.original(context, uri, contentType, fileName, sourceBytes)
            )
        }
        val copiedSource = copyUnknownSource(context, uri)
        return UploadSource(
            uri = Uri.fromFile(copiedSource),
            bytes = copiedSource.length(),
            original = PreparedCouponUpload.original(copiedSource, contentType, fileName)
        )
    }

    private fun UploadSource.originalOrThrow(requiresTranscode: Boolean): PreparedCouponUpload {
        if (!requiresTranscode && isServerSafe()) return original
        original.close()
        throw IOException("이미지 형식을 확인하지 못해 안전하게 업로드할 수 없습니다.")
    }

    private fun requireSupportedPixelCount(bounds: Pair<Int, Int>, original: PreparedCouponUpload) {
        if (bounds.first.toLong() * bounds.second.toLong() <= AppConstants.MAX_IMAGE_PIXELS) return
        original.close()
        throw IOException("이미지 해상도가 너무 큽니다. 4천만 화소 이하 이미지를 선택해 주세요.")
    }

    private suspend fun optimizeUpload(
        context: Context,
        source: UploadSource,
        fileName: String,
        requiresTranscode: Boolean,
        targetWidth: Int,
        targetHeight: Int
    ): PreparedCouponUpload {
        val output = try {
            createOptimizedFile(context, source.uri, targetWidth, targetHeight)
        } catch (error: Exception) {
            return recoverOptimizationFailure(source, requiresTranscode, error)
        }
        try {
            coroutineContext.ensureActive()
            if (!shouldUseOptimizedCouponUpload(source.bytes, output.length(), requiresTranscode)) {
                delete(output)
                return source.original
            }
            source.original.close()
            return PreparedCouponUpload.optimized(output, source.bytes, fileName)
        } catch (error: Exception) {
            delete(output)
            return recoverOptimizationFailure(source, requiresTranscode, error)
        }
    }

    private suspend fun createOptimizedFile(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int
    ): File {
        coroutineContext.ensureActive()
        val output = File.createTempFile("coupon-upload-", ".jpg", uploadDirectory(context))
        try {
            val decoded = decodeForUpload(context, uri, targetWidth, targetHeight)
                ?: throw IOException("이미지를 안전한 업로드 크기로 변환하지 못했습니다.")
            encodeBitmapWithinServerBudget(decoded, output)
            return output
        } catch (error: Exception) {
            delete(output)
            throw error
        }
    }

    private fun recoverOptimizationFailure(
        source: UploadSource,
        requiresTranscode: Boolean,
        error: Exception
    ): PreparedCouponUpload {
        if (error !is kotlinx.coroutines.CancellationException && !requiresTranscode && source.isServerSafe()) {
            return source.original
        }
        source.original.close()
        if (error is kotlinx.coroutines.CancellationException) throw error
        throw IOException("이미지를 안전한 업로드 크기로 준비하지 못했습니다.", error)
    }

    private fun UploadSource.isServerSafe(): Boolean =
        bytes <= AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES

    private suspend fun copyUnknownSource(context: Context, uri: Uri): File {
        val destination = File.createTempFile("coupon-source-", ".tmp", uploadDirectory(context))
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IOException("이미지를 열 수 없습니다.")
            input.use { source ->
                destination.outputStream().buffered(64 * 1024).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = source.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > AppConstants.MAX_IMAGE_BYTES) {
                            throw IOException("이미지는 최대 10MB까지 업로드할 수 있습니다.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
            return destination
        } catch (error: Exception) {
            delete(destination)
            throw error
        }
    }

    private suspend fun encodeBitmapWithinServerBudget(decoded: Bitmap, output: File) {
        val opaque = decoded.withWhiteBackgroundIfNeeded()
        var candidate = opaque
        try {
            encodeWithinServerBudget(output, candidate)
            repeat(MAX_RESIZE_ATTEMPTS) {
                if (output.length() <= AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES) return
                coroutineContext.ensureActive()
                candidate = resizeForServerBudget(candidate, opaque, output.length())
                encodeWithinServerBudget(output, candidate)
            }
            if (output.length() > AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES) {
                throw IOException("이미지를 서버 전송 한도에 맞게 줄이지 못했습니다.")
            }
        } finally {
            if (candidate !== opaque) candidate.recycle()
            if (opaque !== decoded) opaque.recycle()
            decoded.recycle()
        }
    }

    private fun resizeForServerBudget(bitmap: Bitmap, original: Bitmap, encodedBytes: Long): Bitmap {
        val scale = sqrt(
            AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES.toDouble() * RESIZE_HEADROOM /
                encodedBytes.toDouble()
        ).coerceIn(0.5, 0.9)
        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true
        )
        if (bitmap !== original) bitmap.recycle()
        return resized
    }

    private fun encodeWithinServerBudget(output: File, bitmap: Bitmap) {
        var quality = JPEG_QUALITY
        while (true) {
            output.outputStream().buffered(64 * 1024).use { stream ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                    throw IOException("이미지 최적화에 실패했습니다.")
                }
            }
            if (
                output.length() <= AppConstants.MAX_SERVER_UPLOAD_IMAGE_BYTES ||
                quality == MIN_JPEG_QUALITY
            ) return
            quality = (quality - JPEG_QUALITY_STEP).coerceAtLeast(MIN_JPEG_QUALITY)
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
