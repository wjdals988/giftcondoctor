package com.giftcondoctor.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedImageImportTest {
    @Test
    fun `이미지 content URI 공유만 허용한다`() {
        assertTrue(acceptsSharedImageIntent("android.intent.action.SEND", "image/png", "content"))
        assertTrue(acceptsSharedImageIntent("android.intent.action.SEND", "IMAGE/JPEG", "CONTENT"))
    }

    @Test
    fun `파일 URI와 이미지가 아닌 공유를 거부한다`() {
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND", "image/png", "file"))
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND", "text/plain", "content"))
        assertFalse(acceptsSharedImageIntent("android.intent.action.SEND_MULTIPLE", "image/png", "content"))
        assertFalse(acceptsSharedImageIntent(null, null, null))
    }
}
