package com.giftcondoctor.app.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class PreparedCouponUploadOwnershipTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun borrowedUploadSurvivesSuccessUntilCallerFinishes() = runTest {
        val file = temporaryFolder.newFile("borrowed-success.jpg").apply { writeBytes(byteArrayOf(9, 10)) }
        val upload = PreparedCouponUpload.optimized(file, file.length(), file.name)

        val result = withPreparedCouponUpload(upload, prepare = { error("사용하면 안 됩니다.") }) { "ok" }

        assertTrue(result == "ok")
        assertTrue(file.exists())
        upload.close()
        assertFalse(file.exists())
    }

    @Test
    fun borrowedUploadSurvivesFailureForImmediateRetry() = runTest {
        val payload = byteArrayOf(1, 2, 3, 4)
        val file = temporaryFolder.newFile("borrowed.jpg").apply { writeBytes(payload) }
        val upload = PreparedCouponUpload.optimized(file, payload.size.toLong(), file.name)

        val result = runCatching {
            withPreparedCouponUpload(upload, prepare = { error("사용하면 안 됩니다.") }) {
                throw IOException("전송 실패")
            }
        }

        assertTrue(result.exceptionOrNull() is IOException)
        assertTrue(file.exists())
        assertArrayEquals(payload, upload.openStream()?.use { it.readBytes() })
        upload.close()
        assertFalse(file.exists())
    }

    @Test
    fun internallyPreparedUploadIsClosedAfterSuccess() = runTest {
        val file = temporaryFolder.newFile("owned-success.jpg").apply { writeBytes(byteArrayOf(5, 6)) }
        val upload = PreparedCouponUpload.optimized(file, file.length(), file.name)

        val result = withPreparedCouponUpload(null, prepare = { upload }) { "ok" }

        assertTrue(result == "ok")
        assertFalse(file.exists())
    }

    @Test
    fun internallyPreparedUploadIsClosedAfterFailure() = runTest {
        val file = temporaryFolder.newFile("owned-failure.jpg").apply { writeBytes(byteArrayOf(7, 8)) }
        val upload = PreparedCouponUpload.optimized(file, file.length(), file.name)

        runCatching {
            withPreparedCouponUpload(null, prepare = { upload }) { throw IOException("전송 실패") }
        }

        assertFalse(file.exists())
    }
}
