package com.giftcondoctor.app.ui

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.ui.screens.CouponAddedFeedbackEffect
import com.giftcondoctor.app.ui.screens.CouponUsedFeedbackEffect
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CouponAddedFeedbackInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmsRegistrationAndStartsAnotherCouponFromTheAction() {
        var consumed = 0
        var addAnother = 0

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            GDTheme {
                CouponAddedFeedbackEffect(
                    showAddedFeedback = true,
                    couponId = "coupon-1",
                    snackbarHostState = snackbarHostState,
                    onConsumed = { consumed += 1 },
                    onAddAnother = { addAnother += 1 }
                )
                SnackbarHost(snackbarHostState)
            }
        }

        composeRule.onNodeWithText("쿠폰을 등록했어요. 상세 정보를 확인해 주세요.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("하나 더 등록").performClick()
        composeRule.runOnIdle {
            assertEquals(1, consumed)
            assertEquals(1, addAnother)
        }
    }

    @Test
    fun offersUndoAfterMarkingCouponUsed() {
        var undoCount = 0

        composeRule.setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            GDTheme {
                CouponUsedFeedbackEffect(
                    feedbackVersion = 1,
                    couponId = "coupon-1",
                    snackbarHostState = snackbarHostState,
                    onUndo = { undoCount += 1 }
                )
                SnackbarHost(snackbarHostState)
            }
        }

        composeRule.onNodeWithText("사용 완료로 변경했어요.").assertIsDisplayed()
        composeRule.onNodeWithText("실행 취소").performClick()
        composeRule.runOnIdle { assertEquals(1, undoCount) }
    }
}
