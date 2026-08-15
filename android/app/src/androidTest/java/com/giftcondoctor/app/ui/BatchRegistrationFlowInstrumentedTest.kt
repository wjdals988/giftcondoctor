package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.ui.screens.AddCouponScreen
import com.giftcondoctor.app.ui.screens.CouponUploadExitDialog
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.CouponUploadStage
import com.giftcondoctor.app.ui.viewmodel.CouponUploadState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BatchRegistrationFlowInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun skipCurrentImageRequiresConfirmation() {
        var skipped = 0
        renderBatchScreen(onSkip = { skipped += 1 })

        composeRule.onNodeWithText("현재 이미지 포함 3장 남음").assertIsDisplayed()
        composeRule.onNodeWithTag("skip-batch-image").performClick()
        composeRule.onNodeWithText("현재 이미지를 제외할까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-skip-batch-image").performClick()

        composeRule.runOnIdle { assertEquals(1, skipped) }
    }

    @Test
    fun topBackRequiresConfirmationBeforeCancellingRemainingBatch() {
        var exits = 0
        renderBatchScreen(onBack = { exits += 1 })

        composeRule.onNodeWithContentDescription("뒤로").performClick()
        composeRule.onNodeWithText("일괄 등록을 그만둘까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-cancel-batch").performClick()

        composeRule.runOnIdle { assertEquals(1, exits) }
    }

    @Test
    fun uploadingCanBeCancelledBeforeExit() {
        var cancellations = 0
        composeRule.setContent {
            GDTheme {
                CouponUploadExitDialog(
                    uploadState = CouponUploadState(CouponUploadStage.Uploading, 42),
                    onDismiss = {},
                    onCancelAndExit = { cancellations += 1 }
                )
            }
        }

        composeRule.onNodeWithText("업로드를 취소하고 나갈까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-cancel-upload-and-exit").performClick()
        composeRule.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test
    fun savingCannotBeCancelledMidCommit() {
        composeRule.setContent {
            GDTheme {
                CouponUploadExitDialog(
                    uploadState = CouponUploadState(CouponUploadStage.Saving),
                    onDismiss = {},
                    onCancelAndExit = {}
                )
            }
        }

        composeRule.onNodeWithText("안전하게 마무리하는 중이에요").assertIsDisplayed()
        composeRule.onNodeWithText("쿠폰 정보 저장은 중간에 취소할 수 없어요. 저장이 끝난 뒤 이동해 주세요.")
            .assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-cancel-upload-and-exit").assertDoesNotExist()
        composeRule.onNodeWithTag("acknowledge-upload-exit").assertIsDisplayed()
    }

    private fun renderBatchScreen(
        onSkip: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeRule.setContent {
            GDTheme {
                AddCouponScreen(
                    roomId = "test-room",
                    batchPosition = 1,
                    batchTotal = 3,
                    batchRemaining = 3,
                    onImagesSelected = {},
                    onSkipCurrent = onSkip,
                    onBack = onBack,
                    onAdded = {}
                )
            }
        }
    }
}
