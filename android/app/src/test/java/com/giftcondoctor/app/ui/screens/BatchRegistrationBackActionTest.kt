package com.giftcondoctor.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class BatchRegistrationBackActionTest {
    @Test
    fun `업로드 중에는 화면을 이탈하지 않는다`() {
        assertEquals(
            BatchRegistrationBackAction.BlockWhileBusy,
            batchRegistrationBackAction(busy = true, batchRemaining = 5)
        )
    }

    @Test
    fun `두 장 이상 남으면 일괄 등록 취소를 확인한다`() {
        assertEquals(
            BatchRegistrationBackAction.ConfirmCancel,
            batchRegistrationBackAction(busy = false, batchRemaining = 2)
        )
    }

    @Test
    fun `마지막 이미지에서는 바로 나간다`() {
        assertEquals(
            BatchRegistrationBackAction.Exit,
            batchRegistrationBackAction(busy = false, batchRemaining = 1)
        )
    }

    @Test
    fun `일괄 등록이 아니면 바로 나간다`() {
        assertEquals(
            BatchRegistrationBackAction.Exit,
            batchRegistrationBackAction(busy = false, batchRemaining = 0)
        )
    }
}
