package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.ui.screens.AddCouponScreen
import com.giftcondoctor.app.ui.theme.GDTheme
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
