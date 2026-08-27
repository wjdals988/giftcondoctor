package com.giftcondoctor.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedImageImportTest {
    @Test
    fun `이미지 content URI 공유만 허용한다`() {
        assertTrue(acceptsSharedImageIntent("android.intent.action.SEND", "image/png", listOf("content")))
        assertTrue(acceptsSharedImageIntent("android.intent.action.SEND", "IMAGE/JPEG", listOf("CONTENT")))
        assertTrue(
            acceptsSharedImageIntent(
                "android.intent.action.SEND_MULTIPLE",
                "image/*",
                List(AppConstants.MAX_SHARED_IMAGE_COUNT) { "content" }
            )
        )
    }

    @Test
    fun `파일 URI와 이미지가 아닌 공유를 거부한다`() {
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND", "image/png", listOf("file")))
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND", "text/plain", listOf("content")))
        assertFalse(acceptsSharedImageIntent(null, null, emptyList()))
    }

    @Test
    fun `빈 목록과 10장을 초과한 공유를 거부한다`() {
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND_MULTIPLE", "image/png", emptyList()))
        assertFalse(
            acceptsSharedImageIntent(
                "android.intent.action.SEND_MULTIPLE",
                "image/png",
                List(AppConstants.MAX_SHARED_IMAGE_COUNT + 1) { "content" }
            )
        )
    }
}
