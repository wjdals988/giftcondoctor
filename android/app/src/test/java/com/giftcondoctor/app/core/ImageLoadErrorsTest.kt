package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageLoadErrorsTest {

    /** 2026-08-28 비행기 모드 실측에서 화면에 그대로 노출된 문장. */
    private val observedFirebaseMessage =
        "A network error (such as timeout, interrupted connection or unreachable host) has occurred."

    @Test
    fun detectsTheMessageActuallyObservedOnDevice() {
        assertTrue(ImageLoadErrors.isNetworkFailure(observedFirebaseMessage))
    }

    @Test
    fun detectsOtherCommonNetworkPhrasings() {
        assertTrue(ImageLoadErrors.isNetworkFailure("Unable to resolve host \"firebasestorage.googleapis.com\""))
        assertTrue(ImageLoadErrors.isNetworkFailure("failed to connect to /10.0.2.2"))
        assertTrue(ImageLoadErrors.isNetworkFailure("Read timed out"))
    }

    @Test
    fun doesNotTreatEveryFailureAsNetwork() {
        // 권한 실패를 네트워크로 안내하면 사용자는 연결을 고치려다 시간을 버린다.
        assertFalse(ImageLoadErrors.isNetworkFailure("User does not have permission to access this object."))
        assertFalse(ImageLoadErrors.isNetworkFailure(null))
        assertFalse(ImageLoadErrors.isNetworkFailure(""))
    }

    @Test
    fun tellsTheUserTheBarcodeStillWorksOffline() {
        // 이 문장이 이 변경의 존재 이유다. 원본 이미지 실패는 결제를 막지 않는다.
        val message = ImageLoadErrors.message(observedFirebaseMessage, hasBarcode = true)
        assertEquals(
            "지금은 네트워크가 없어 원본 이미지를 불러올 수 없어요. 바코드는 그대로 사용할 수 있습니다.",
            message
        )
    }

    @Test
    fun doesNotPromiseTheBarcodeWhenThereIsNone() {
        val message = ImageLoadErrors.message(observedFirebaseMessage, hasBarcode = false)
        assertEquals(
            "지금은 네트워크가 없어 원본 이미지를 불러올 수 없어요. 연결된 뒤 다시 시도해 주세요.",
            message
        )
    }

    @Test
    fun neverLeaksTheEnglishOriginal() {
        // 어떤 입력이 와도 영어 원문이 그대로 나가면 안 된다.
        val inputs = listOf(observedFirebaseMessage, "Object does not exist at location.", null, "")
        for (hasBarcode in listOf(true, false)) {
            for (input in inputs) {
                val message = ImageLoadErrors.message(input, hasBarcode)
                assertFalse("영어 원문이 새어 나갔다: $message", message.contains("error has occurred"))
                assertTrue("한국어 안내가 아니다: $message", message.endsWith("."))
            }
        }
    }
}
