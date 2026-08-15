package com.giftcondoctor.app.data

import java.io.ByteArrayOutputStream
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedImageImportStoreTest {
    @Test
    fun `상한 이내 공유 이미지를 그대로 복사한다`() {
        val source = ByteArray(131_073) { (it % 251).toByte() }
        var offset = 0
        val output = ByteArrayOutputStream()

        val copied = SharedImageImportStore.copyWithinLimit(
            readBytes = { buffer ->
                if (offset >= source.size) {
                    -1
                } else {
                    val count = minOf(buffer.size, source.size - offset)
                    source.copyInto(buffer, endIndex = offset + count, startIndex = offset)
                    offset += count
                    count
                }
            },
            writeBytes = { buffer, count -> output.write(buffer, 0, count) },
            maxBytes = source.size
        )

        assertEquals(source.size.toLong(), copied)
        assertArrayEquals(source, output.toByteArray())
    }

    @Test(expected = IOException::class)
    fun `상한을 한 바이트라도 넘으면 복사를 중단한다`() {
        val source = ByteArray(101)
        var emitted = false

        SharedImageImportStore.copyWithinLimit(
            readBytes = { buffer ->
                if (emitted) -1 else source.copyInto(buffer).let { emitted = true; source.size }
            },
            writeBytes = { _, _ -> },
            maxBytes = 100
        )
    }

    @Test(expected = IOException::class)
    fun `빈 읽기가 반복되면 무한 대기 대신 중단한다`() {
        SharedImageImportStore.copyWithinLimit(
            readBytes = { 0 },
            writeBytes = { _, _ -> },
            maxBytes = 100
        )
    }

    @Test
    fun `공유 임시 파일은 화면 복원을 위해 24시간 보존한다`() {
        val now = 2 * SharedImageImportStore.ABANDONED_FILE_AGE_MILLIS

        org.junit.Assert.assertFalse(
            shouldPurgeSharedImport(now - SharedImageImportStore.ABANDONED_FILE_AGE_MILLIS, now)
        )
        org.junit.Assert.assertTrue(
            shouldPurgeSharedImport(now - SharedImageImportStore.ABANDONED_FILE_AGE_MILLIS - 1, now)
        )
        org.junit.Assert.assertTrue(shouldPurgeSharedImport(0, now))
    }

    @Test
    fun `다중 공유 전체 크기는 50MB까지 허용한다`() {
        org.junit.Assert.assertFalse(
            exceedsSharedImageTotalLimit(com.giftcondoctor.app.core.AppConstants.MAX_SHARED_IMAGE_TOTAL_BYTES)
        )
        org.junit.Assert.assertTrue(
            exceedsSharedImageTotalLimit(
                com.giftcondoctor.app.core.AppConstants.MAX_SHARED_IMAGE_TOTAL_BYTES + 1L
            )
        )
    }
}
