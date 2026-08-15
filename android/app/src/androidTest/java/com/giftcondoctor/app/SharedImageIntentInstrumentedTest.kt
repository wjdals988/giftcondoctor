package com.giftcondoctor.app

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.data.SharedImageImportStore
import java.io.File
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedImageIntentInstrumentedTest {
    @Test
    fun actionSendCopiesImageOnceAndKeepsItAcrossActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val sourceUri = resolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "giftcondoctor-intent-test.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
        ) ?: error("테스트 이미지를 만들 수 없습니다.")
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
        )
        resolver.openOutputStream(sourceUri)?.use { it.write(imageBytes) }

        val importDirectory = File(context.cacheDir, "shared-coupon-imports")
        importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, Uri.fromFile(it)) }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, sourceUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val firstImport = waitForImportedFile(importDirectory)
                assertEquals("png", firstImport.extension)
                assertTrue(firstImport.length() > 0L)

                scenario.recreate()
                val filesAfterRecreation = waitForImportedFiles(importDirectory)
                assertEquals(1, filesAfterRecreation.size)
                assertEquals(firstImport.name, filesAfterRecreation.single().name)
            }
        } finally {
            importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, Uri.fromFile(it)) }
            resolver.delete(sourceUri, null, null)
        }
    }

    @Test
    fun actionSendMultipleCopiesEveryImageAndKeepsBatchAcrossActivityRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val imageBytes = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9Y9ZQmcAAAAASUVORK5CYII="
        )
        val sourceUris = (1..3).map { index ->
            resolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "giftcondoctor-multi-$index.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
            )?.also { uri -> resolver.openOutputStream(uri)?.use { it.write(imageBytes) } }
                ?: error("테스트 이미지 $index 을 만들 수 없습니다.")
        }
        val importDirectory = File(context.cacheDir, "shared-coupon-imports")
        importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, Uri.fromFile(it)) }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/png"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(sourceUris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val firstImports = waitForImportedFiles(importDirectory, expectedCount = 3)
                assertEquals(3, firstImports.size)
                assertTrue(firstImports.all { it.extension == "png" && it.length() > 0L })

                scenario.recreate()
                val filesAfterRecreation = waitForImportedFiles(importDirectory, expectedCount = 3)
                assertEquals(firstImports.map(File::getName).sorted(), filesAfterRecreation.map(File::getName).sorted())
            }
        } finally {
            importDirectory.listFiles()?.forEach { SharedImageImportStore.delete(context, Uri.fromFile(it)) }
            sourceUris.forEach { resolver.delete(it, null, null) }
        }
    }

    private fun waitForImportedFile(directory: File): File = waitForImportedFiles(directory).single()

    private fun waitForImportedFiles(directory: File, expectedCount: Int = 1): List<File> {
        repeat(100) {
            val files = directory.listFiles()?.filter(File::isFile).orEmpty()
            if (files.size == expectedCount && files.none { it.extension == "source" }) return files
            Thread.sleep(50)
        }
        error("공유 이미지 $expectedCount 장이 5초 안에 앱 캐시로 복사되지 않았습니다.")
    }
}
