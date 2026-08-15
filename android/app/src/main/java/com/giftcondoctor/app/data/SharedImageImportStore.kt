package com.giftcondoctor.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.giftcondoctor.app.core.AppConstants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext

object SharedImageImportStore {
    private const val DIRECTORY_NAME = "shared-coupon-imports"
    internal const val ABANDONED_FILE_AGE_MILLIS = 24L * 60L * 60L * 1_000L
    private val abandonedFilesPurged = AtomicBoolean(false)

    fun purgeAbandonedOnce(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        if (!abandonedFilesPurged.compareAndSet(false, true)) return
        importDirectory(context).listFiles()
            ?.filter { shouldPurgeSharedImport(it.lastModified(), nowMillis) }
            ?.forEach(::erase)
    }

    suspend fun import(
        context: Context,
        sourceUri: Uri,
        declaredType: String? = null
    ): Uri = withContext(Dispatchers.IO) {
        require(sourceUri.scheme.equals("content", ignoreCase = true)) { "공유한 이미지 주소를 열 수 없습니다." }
        purgeAbandonedOnce(context)

        val providerType = context.contentResolver.getType(sourceUri)?.lowercase()
        val candidateType = providerType ?: declaredType?.lowercase()
        if (candidateType?.startsWith("image/") != true) {
            throw IOException("이미지 파일만 쿠폰으로 등록할 수 있습니다.")
        }

        context.contentResolver.openAssetFileDescriptor(sourceUri, "r")?.use { descriptor ->
            if (descriptor.length > AppConstants.MAX_IMAGE_BYTES) {
                throw IOException("공유 이미지는 최대 10MB까지 등록할 수 있습니다.")
            }
        }

        val destination = File.createTempFile("shared-coupon-", ".source", importDirectory(context))
        var completedFile: File? = null
        try {
            val input = context.contentResolver.openInputStream(sourceUri)
                ?: throw IOException("공유한 이미지를 열 수 없습니다.")
            input.use { source ->
                destination.outputStream().buffered(64 * 1024).use { output ->
                    copyWithinLimit(
                        readBytes = { buffer -> source.read(buffer) },
                        writeBytes = { buffer, count ->
                            coroutineContext.ensureActive()
                            output.write(buffer, 0, count)
                        }
                    )
                }
            }
            if (destination.length() == 0L) throw IOException("공유한 이미지가 비어 있습니다.")
            completedFile = validateAndNameImage(context, destination)
            Uri.fromFile(completedFile)
        } catch (error: Exception) {
            erase(destination)
            completedFile?.let(::erase)
            throw error
        }
    }

    suspend fun importAll(
        context: Context,
        sourceUris: List<Uri>,
        declaredType: String? = null,
        onProgress: (completed: Int, total: Int) -> Unit = { _, _ -> }
    ): List<Uri> {
        require(sourceUris.size in 1..AppConstants.MAX_SHARED_IMAGE_COUNT) {
            "공유 이미지는 한 번에 최대 ${AppConstants.MAX_SHARED_IMAGE_COUNT}장까지 등록할 수 있습니다."
        }
        val importedUris = mutableListOf<Uri>()
        var importedBytes = 0L
        onProgress(0, sourceUris.size)
        try {
            sourceUris.forEachIndexed { index, sourceUri ->
                val importedUri = import(context, sourceUri, declaredType)
                importedUris += importedUri
                importedBytes += importedUri.path?.let(::File)?.length() ?: 0L
                if (exceedsSharedImageTotalLimit(importedBytes)) {
                    throw IOException("공유 이미지 전체 크기는 최대 50MB까지 등록할 수 있습니다.")
                }
                onProgress(index + 1, sourceUris.size)
            }
            return importedUris
        } catch (error: Exception) {
            importedUris.forEach { delete(context, it) }
            throw error
        }
    }

    fun delete(context: Context, uri: Uri?) {
        val ownedUri = uri ?: return
        if (!ownedUri.scheme.equals("file", ignoreCase = true)) return
        val file = ownedUri.path?.let(::File) ?: return
        val directory = importDirectory(context)
        val isOwned = runCatching { file.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
        if (isOwned) erase(file)
    }

    fun restoreOwned(context: Context, value: String?): Uri? {
        val uri = value?.let(Uri::parse)
            ?.takeIf { it.scheme.equals("file", ignoreCase = true) }
            ?: return null
        val file = uri.path?.let(::File) ?: return null
        val directory = importDirectory(context)
        val isOwned = runCatching { file.canonicalFile.parentFile == directory.canonicalFile }.getOrDefault(false)
        return uri.takeIf { isOwned && file.isFile && file.length() in 1..AppConstants.MAX_IMAGE_BYTES.toLong() }
    }

    fun restoreOwned(context: Context, values: List<String>?): List<Uri> =
        values.orEmpty().mapNotNull { restoreOwned(context, it) }

    internal fun copyWithinLimit(
        readBytes: (ByteArray) -> Int,
        writeBytes: (ByteArray, Int) -> Unit,
        maxBytes: Int = AppConstants.MAX_IMAGE_BYTES
    ): Long {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        var consecutiveEmptyReads = 0
        while (true) {
            val read = readBytes(buffer)
            if (read < 0) break
            if (read == 0) {
                consecutiveEmptyReads += 1
                if (consecutiveEmptyReads >= 8) throw IOException("공유 이미지 읽기가 중단되었습니다.")
                continue
            }
            consecutiveEmptyReads = 0
            total += read
            if (total > maxBytes) throw IOException("공유 이미지는 최대 10MB까지 등록할 수 있습니다.")
            writeBytes(buffer, read)
        }
        return total
    }

    private fun importDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    private fun validateAndNameImage(context: Context, source: File): File {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.absolutePath, options)
        val detectedType = options.outMimeType?.lowercase()
        if (
            detectedType?.startsWith("image/") != true ||
            options.outWidth <= 0 ||
            options.outHeight <= 0
        ) {
            throw IOException("공유한 파일에서 유효한 이미지를 찾지 못했습니다.")
        }
        if (options.outWidth.toLong() * options.outHeight.toLong() > AppConstants.MAX_IMAGE_PIXELS) {
            throw IOException("이미지 해상도가 너무 큽니다. 4천만 화소 이하 이미지를 공유해 주세요.")
        }

        val namedFile = File.createTempFile(
            "shared-coupon-",
            sharedImageExtension(detectedType),
            importDirectory(context)
        )
        return try {
            if (!namedFile.delete()) throw IOException("공유 이미지 파일을 준비하지 못했습니다.")
            if (!source.renameTo(namedFile)) {
                source.copyTo(namedFile, overwrite = true)
                erase(source)
            }
            namedFile
        } catch (error: Exception) {
            erase(namedFile)
            throw error
        }
    }

    private fun sharedImageExtension(contentType: String): String = when (contentType) {
        "image/jpeg" -> ".jpg"
        "image/png" -> ".png"
        "image/webp" -> ".webp"
        "image/gif" -> ".gif"
        "image/heic", "image/heif" -> ".heic"
        "image/avif" -> ".avif"
        "image/bmp" -> ".bmp"
        "image/x-icon", "image/vnd.microsoft.icon" -> ".ico"
        "image/vnd.wap.wbmp" -> ".wbmp"
        else -> ".image"
    }

    private fun erase(file: File) {
        if (file.delete() || !file.exists()) return
        runCatching { file.outputStream().use { } }
        file.delete()
    }
}

internal fun shouldPurgeSharedImport(lastModifiedMillis: Long, nowMillis: Long): Boolean =
    lastModifiedMillis <= 0L || nowMillis - lastModifiedMillis > SharedImageImportStore.ABANDONED_FILE_AGE_MILLIS

internal fun exceedsSharedImageTotalLimit(totalBytes: Long): Boolean =
    totalBytes > AppConstants.MAX_SHARED_IMAGE_TOTAL_BYTES
