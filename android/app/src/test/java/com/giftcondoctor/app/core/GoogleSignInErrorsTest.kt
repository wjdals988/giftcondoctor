package com.giftcondoctor.app.core

import com.google.android.gms.common.api.CommonStatusCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Google 로그인 실패 안내 문구 검증.
 *
 * 이 매핑의 존재 이유는 하나다. 이전 구현은 결과 코드가 RESULT_OK 가 아니면 무조건
 * "취소되었습니다" 를 보여줬는데, GoogleSignIn 은 설정 오류와 네트워크 실패에서도
 * RESULT_CANCELED 를 돌려준다. 따라서 취소가 아닌 원인이 취소로 둔갑하지 않는지가
 * 가장 중요한 계약이다.
 */
class GoogleSignInErrorsTest {
    @Test
    fun onlyRealCancellationIsDescribedAsCancellation() {
        assertTrue(GoogleSignInErrors.message(GoogleSignInErrors.SIGN_IN_CANCELLED).contains("취소"))

        // 아래는 모두 사용자가 취소한 것이 아니다. 취소로 안내되면 안 된다.
        listOf(
            CommonStatusCodes.DEVELOPER_ERROR,
            CommonStatusCodes.NETWORK_ERROR,
            CommonStatusCodes.API_NOT_CONNECTED,
            CommonStatusCodes.SERVICE_DISABLED,
            CommonStatusCodes.INVALID_ACCOUNT,
            GoogleSignInErrors.SIGN_IN_FAILED
        ).forEach { code ->
            assertFalse("code=$code 가 취소로 안내됨", GoogleSignInErrors.message(code).contains("취소"))
        }
        assertFalse(GoogleSignInErrors.message(null).contains("취소"))
    }

    @Test
    fun developerErrorPointsAtSigningCertificateRegistration() {
        val message = GoogleSignInErrors.message(CommonStatusCodes.DEVELOPER_ERROR)
        assertTrue(message.contains("서명 인증서"))
        assertTrue(message.contains("Firebase"))
    }

    @Test
    fun networkErrorAsksToCheckConnection() {
        assertTrue(GoogleSignInErrors.message(CommonStatusCodes.NETWORK_ERROR).contains("네트워크"))
    }

    @Test
    fun unknownCodeStillReportsTheNumberForSupport() {
        val message = GoogleSignInErrors.message(9999)
        assertTrue("문의를 위해 코드가 남아야 한다", message.contains("9999"))
    }

    @Test
    fun developerErrorIsNotUserActionable() {
        // 재시도해도 소용없는 원인이므로 재시도 유도 대신 이메일 로그인을 안내해야 한다.
        assertFalse(GoogleSignInErrors.isUserActionable(CommonStatusCodes.DEVELOPER_ERROR))
        assertTrue(GoogleSignInErrors.isUserActionable(CommonStatusCodes.NETWORK_ERROR))
        assertTrue(GoogleSignInErrors.isUserActionable(GoogleSignInErrors.SIGN_IN_CANCELLED))
        assertTrue(GoogleSignInErrors.isUserActionable(null))
    }

    @Test
    fun fallbackToEmailLoginIsOfferedWhenRetryIsUnlikelyToHelp() {
        assertTrue(GoogleSignInErrors.message(GoogleSignInErrors.SIGN_IN_FAILED).contains("이메일"))
        assertTrue(GoogleSignInErrors.message(null).contains("이메일"))
    }
}
