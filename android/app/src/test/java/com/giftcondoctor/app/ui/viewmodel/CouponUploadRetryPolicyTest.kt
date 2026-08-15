package com.giftcondoctor.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class CouponUploadRetryPolicyTest {
    @Test
    fun duplicateCheckCancellationKeepsEnteredData() {
        assertEquals(
            "중복 쿠폰 확인을 취소했습니다. 입력한 정보와 준비한 이미지는 그대로 유지했어요.",
            couponRegistrationCancellationMessage(
                cancelledStage = CouponUploadStage.CheckingDuplicates,
                hasPreparedUpload = true
            )
        )
    }

    @Test
    fun cancellationExplainsThatPreparedUploadCanBeRetried() {
        assertEquals(
            "이미지 업로드를 취소했습니다. 준비한 이미지는 유지해 바로 다시 시도할 수 있어요.",
            uploadCancellationMessage(hasPreparedUpload = true)
        )
    }

    @Test
    fun cancellationDoesNotPromiseRetryWhenPreparationWasNotRetained() {
        assertEquals(
            "이미지 업로드를 취소했습니다. 전송된 임시 파일이 있으면 자동 정리합니다.",
            uploadCancellationMessage(hasPreparedUpload = false)
        )
    }
}
