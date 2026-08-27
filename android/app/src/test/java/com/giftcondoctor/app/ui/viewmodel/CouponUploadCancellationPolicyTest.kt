package com.giftcondoctor.app.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponUploadCancellationPolicyTest {
    @Test
    fun `중복 확인과 이미지 준비 및 전송은 취소할 수 있다`() {
        assertTrue(canCancelCouponUpload(CouponUploadStage.CheckingDuplicates))
        assertTrue(canCancelCouponUpload(CouponUploadStage.Preparing))
        assertTrue(canCancelCouponUpload(CouponUploadStage.Uploading))
    }

    @Test
    fun `저장과 정리 단계는 중간 취소하지 않는다`() {
        assertFalse(canCancelCouponUpload(CouponUploadStage.Idle))
        assertFalse(canCancelCouponUpload(CouponUploadStage.Cancelling))
        assertFalse(canCancelCouponUpload(CouponUploadStage.Saving))
    }
}
