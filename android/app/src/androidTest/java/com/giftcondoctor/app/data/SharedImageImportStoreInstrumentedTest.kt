package com.giftcondoctor.app.data

import android.content.ContentValues
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedImageImportStoreInstrumentedTest {
    @Test
    fun importsGrantedGalleryImageIntoOwnedBoundedCache() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val sourceBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
        )
        val sourceUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "giftcondoctor-share-test.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
        ) ?: error("테스트 이미지를 만들 수 없습니다.")

        var importedUri: android.net.Uri? = null
        try {
            resolver.openOutputStream(sourceUri)?.use { it.write(sourceBytes) }
                ?: error("테스트 이미지를 쓸 수 없습니다.")

            importedUri = SharedImageImportStore.import(context, sourceUri)
            val importedFile = File(requireNotNull(importedUri.path))

            assertEquals("file", importedUri.scheme)
            assertEquals("png", importedFile.extension)
            assertArrayEquals(sourceBytes, importedFile.readBytes())
            assertEquals(importedUri, SharedImageImportStore.restoreOwned(context, importedUri.toString()))
            assertNull(SharedImageImportStore.restoreOwned(context, "file:///tmp/not-owned.png"))
        } finally {
            SharedImageImportStore.delete(context, importedUri)
            resolver.delete(sourceUri, null, null)
        }
    }

    @Test
    fun importsMultipleImagesInOrderAndReportsProgress() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val sourceBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
        )
        val sourceUris = (1..3).map { index ->
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "giftcondoctor-store-$index.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
            )?.also { uri -> resolver.openOutputStream(uri)?.use { it.write(sourceBytes) } }
                ?: error("테스트 이미지 $index 을 만들 수 없습니다.")
        }
        val progress = mutableListOf<Pair<Int, Int>>()
        var importedUris = emptyList<android.net.Uri>()

        try {
            importedUris = SharedImageImportStore.importAll(context, sourceUris) { completed, total ->
                progress += completed to total
            }

            assertEquals(3, importedUris.size)
            assertEquals(listOf(0 to 3, 1 to 3, 2 to 3, 3 to 3), progress)
            assertTrue(importedUris.all { File(requireNotNull(it.path)).isFile })
        } finally {
            importedUris.forEach { SharedImageImportStore.delete(context, it) }
            sourceUris.forEach { resolver.delete(it, null, null) }
        }
    }

    @Test
    fun invalidSecondItemCleansAlreadyImportedBatch() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
        )
        val validUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "giftcondoctor-valid.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
        ) ?: error("유효한 테스트 이미지를 만들 수 없습니다.")
        val invalidUri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "giftcondoctor-invalid.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            }
        ) ?: error("잘못된 테스트 파일을 만들 수 없습니다.")
        resolver.openOutputStream(validUri)?.use { it.write(imageBytes) }
        resolver.openOutputStream(invalidUri)?.use { it.write("not an image".encodeToByteArray()) }
        val importDirectory = File(context.cacheDir, "shared-coupon-imports")
        importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, android.net.Uri.fromFile(it)) }

        try {
            assertThrows(java.io.IOException::class.java) {
                runBlocking {
                    SharedImageImportStore.importAll(context, listOf(validUri, invalidUri), "image/*")
                }
            }
            assertTrue(importDirectory.listFiles()?.isEmpty() != false)
        } finally {
            importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, android.net.Uri.fromFile(it)) }
            resolver.delete(validUri, null, null)
            resolver.delete(invalidUri, null, null)
        }
    }
}
