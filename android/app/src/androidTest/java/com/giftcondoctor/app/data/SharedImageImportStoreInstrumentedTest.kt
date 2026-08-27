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
}
