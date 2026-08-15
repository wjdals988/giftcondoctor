package com.giftcondoctor.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.ui.screens.CouponImageProcessingStatus
import com.giftcondoctor.app.ui.screens.CouponUploadProgress
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.CouponUploadStage
import com.giftcondoctor.app.ui.viewmodel.CouponUploadState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CouponUploadProgressInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uploadingShowsProgressAndCancelAction() {
        var cancelled = false
        composeRule.setContent {
            GDTheme {
                CouponUploadProgress(
                    uploadState = CouponUploadState(CouponUploadStage.Uploading, 42),
                    onCancel = { cancelled = true }
                )
            }
        }

        composeRule.onNodeWithText("업로드 42%").assertExists()
        composeRule.onNodeWithText("업로드 취소").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun preparingShowsOptimizationStatusAndCancelAction() {
        var cancelled = false
        composeRule.setContent {
            GDTheme {
                CouponUploadProgress(
                    uploadState = CouponUploadState(CouponUploadStage.Preparing),
                    onCancel = { cancelled = true }
                )
            }
        }

        composeRule.onNodeWithText("빠른 업로드를 위해 이미지를 최적화하는 중이에요").assertExists()
        composeRule.onNodeWithText("업로드 취소").performClick()
        composeRule.runOnIdle { assertTrue(cancelled) }
    }

    @Test
    fun optimizedUploadShowsBeforeAndAfterSize() {
        composeRule.setContent {
            GDTheme {
                CouponUploadProgress(
                    uploadState = CouponUploadState(
                        stage = CouponUploadStage.Uploading,
                        percent = 21,
                        originalBytes = 4L * 1024L * 1024L,
                        uploadBytes = 1L * 1024L * 1024L
                    ),
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("이미지 4.0MB → 1.0MB · 업로드 21%").assertExists()
    }

    @Test
    fun cancellingHidesRepeatedCancelAction() {
        composeRule.setContent {
            GDTheme {
                CouponUploadProgress(
                    uploadState = CouponUploadState(CouponUploadStage.Cancelling),
                    onCancel = {}
                )
            }
        }

        composeRule.onNodeWithText("업로드를 중단하고 임시 파일을 정리하는 중이에요").assertExists()
        composeRule.onAllNodesWithText("업로드 취소").assertCountEquals(0)
    }

    @Test
    fun analysisResultCanBeReviewedWhileUploadPreparationContinues() {
        var analysisBusy by mutableStateOf(true)
        var preparationBusy by mutableStateOf(true)
        composeRule.setContent {
            GDTheme {
                CouponImageProcessingStatus(analysisBusy, preparationBusy)
            }
        }

        composeRule.onNodeWithText("쿠폰 정보를 찾고 빠른 업로드를 준비하는 중이에요").assertExists()
        composeRule.runOnUiThread { analysisBusy = false }
        composeRule.onNodeWithText("자동 입력을 먼저 확인하는 동안 빠른 업로드를 준비해요").assertExists()
        composeRule.runOnUiThread { preparationBusy = false }
        composeRule.onNodeWithTag("coupon-image-processing").assertDoesNotExist()
    }
}
