package com.giftcondoctor.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRegistrationBackActionTest {
    @Test
    fun `업로드 중에는 화면을 이탈하지 않는다`() {
        assertEquals(
            BatchRegistrationBackAction.ConfirmBusyExit,
            batchRegistrationBackAction(
                busy = true,
                batchRemaining = 5,
                hasUnsavedDraft = true
            )
        )
    }

    @Test
    fun `두 장 이상 남으면 일괄 등록 취소를 확인한다`() {
        assertEquals(
            BatchRegistrationBackAction.ConfirmCancel,
            batchRegistrationBackAction(
                busy = false,
                batchRemaining = 2,
                hasUnsavedDraft = true
            )
        )
    }

    @Test
    fun `마지막 이미지에 미저장 초안이 있으면 이탈을 확인한다`() {
        assertEquals(
            BatchRegistrationBackAction.ConfirmDiscardDraft,
            batchRegistrationBackAction(
                busy = false,
                batchRemaining = 1,
                hasUnsavedDraft = true
            )
        )
    }

    @Test
    fun `단일 등록에 미저장 초안이 있으면 이탈을 확인한다`() {
        assertEquals(
            BatchRegistrationBackAction.ConfirmDiscardDraft,
            batchRegistrationBackAction(
                busy = false,
                batchRemaining = 0,
                hasUnsavedDraft = true
            )
        )
    }

    @Test
    fun `선택한 이미지가 없으면 바로 나간다`() {
        assertEquals(
            BatchRegistrationBackAction.Exit,
            batchRegistrationBackAction(
                busy = false,
                batchRemaining = 0,
                hasUnsavedDraft = false
            )
        )
    }
}
