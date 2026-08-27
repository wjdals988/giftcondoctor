package com.giftcondoctor.app.data

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class CouponImageFile internal constructor(
    val file: File,
    val byteCount: Long
)

object CouponImageFileStore {
    private const val DIRECTORY_NAME = "coupon-detail-images"
    private val abandonedFilesPurged = AtomicBoolean(false)
    private val trackedFiles = ConcurrentHashMap.newKeySet<File>()

    fun purgeAbandonedOnce(context: Context) {
        if (!abandonedFilesPurged.compareAndSet(false, true)) return
        imageDirectory(context).listFiles()?.forEach(::eraseFile)
    }

    fun create(context: Context): File {
        purgeAbandonedOnce(context)
        val destination = File.createTempFile("coupon-", ".image", imageDirectory(context))
        trackedFiles += destination
        return destination
    }

    fun complete(file: File): CouponImageFile {
        require(file in trackedFiles && file.isFile && file.length() > 0L) {
            "완료되지 않은 쿠폰 이미지 파일입니다."
        }
        return CouponImageFile(file, file.length())
    }

    fun delete(image: CouponImageFile?) {
        image?.let { delete(it.file) }
    }

    fun delete(file: File?) {
        if (file == null) return
        trackedFiles.remove(file)
        eraseFile(file)
    }

    fun clearTracked() {
        trackedFiles.toList().forEach(::delete)
    }

    private fun imageDirectory(context: Context): File =
        File(context.cacheDir, DIRECTORY_NAME).apply { mkdirs() }

    private fun eraseFile(file: File) {
        if (file.delete() || !file.exists()) return
        runCatching { file.outputStream().use { } }
        file.delete()
    }
}
