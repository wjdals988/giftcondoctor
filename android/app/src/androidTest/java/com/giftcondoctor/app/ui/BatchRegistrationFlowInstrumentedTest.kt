package com.giftcondoctor.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.core.CouponDuplicateCandidate
import com.giftcondoctor.app.core.CouponDuplicateReason
import com.giftcondoctor.app.ui.screens.AddCouponScreen
import com.giftcondoctor.app.ui.screens.CouponUploadExitDialog
import com.giftcondoctor.app.ui.screens.PossibleDuplicateCouponDialog
import com.giftcondoctor.app.ui.screens.NextCouponPrefetchStatus
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.CouponUploadStage
import com.giftcondoctor.app.ui.viewmodel.CouponUploadState
import com.giftcondoctor.app.ui.viewmodel.NextCouponPrefetchStage
import com.giftcondoctor.app.ui.viewmodel.NextCouponPrefetchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

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
    fun singleRegistrationBackRequiresConfirmationBeforeDiscardingDraft() {
        var exits = 0
        val imageFile = createCouponImageFile()
        try {
            composeRule.setContent {
                GDTheme {
                    AddCouponScreen(
                        roomId = "test-room",
                        initialImageUri = Uri.fromFile(imageFile),
                        onImagesSelected = {},
                        onSkipCurrent = {},
                        onBack = { exits += 1 },
                        onAdded = {}
                    )
                }
            }

            composeRule.onNodeWithContentDescription("뒤로").performClick()
            composeRule.onNodeWithText("등록을 그만둘까요?").assertIsDisplayed()
            composeRule.onNodeWithText("선택한 이미지와 입력한 쿠폰 정보가 저장되지 않습니다.")
                .assertIsDisplayed()
            composeRule.onNodeWithTag("keep-editing-coupon-draft").performClick()
            composeRule.runOnIdle { assertEquals(0, exits) }

            composeRule.onNodeWithContentDescription("뒤로").performClick()
            composeRule.onNodeWithTag("confirm-discard-coupon-draft").performClick()
            composeRule.runOnIdle { assertEquals(1, exits) }
        } finally {
            imageFile.delete()
        }
    }

    @Test
    fun requiredTitleEnablesSubmitOnlyAfterValidInput() {
        val imageFile = createCouponImageFile()
        try {
            composeRule.setContent {
                GDTheme {
                    AddCouponScreen(
                        roomId = "test-room",
                        initialImageUri = Uri.fromFile(imageFile),
                        onImagesSelected = {},
                        onSkipCurrent = {},
                        onBack = {},
                        onAdded = {}
                    )
                }
            }

            composeRule.waitUntil(timeoutMillis = 10_000) {
                composeRule.onAllNodesWithText("필수 항목입니다 · 0/100")
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithTag("coupon-registration-submit").assertIsNotEnabled()
            composeRule.onNodeWithTag("coupon-registration-title").performTextInput("아메리카노")
            composeRule.onNodeWithTag("coupon-registration-submit").assertIsEnabled()
        } finally {
            imageFile.delete()
        }
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

    @Test
    fun possibleDuplicateCanBeReviewedOrRegisteredAnyway() {
        var continued = 0
        composeRule.setContent {
            GDTheme {
                PossibleDuplicateCouponDialog(
                    candidates = listOf(
                        CouponDuplicateCandidate(
                            couponId = "coupon-1",
                            title = "아메리카노",
                            brand = "스타벅스",
                            expiresLocalDate = LocalDate.parse("2026-12-31"),
                            visibility = "private",
                            reason = CouponDuplicateReason.ExactBarcode
                        )
                    ),
                    onReview = {},
                    onContinue = { continued += 1 }
                )
            }
        }

        composeRule.onNodeWithText("이미 등록된 쿠폰일 수 있어요").assertIsDisplayed()
        composeRule.onNodeWithText("• 아메리카노 · 스타벅스 · 2026-12-31 · 바코드 일치 · 나만 보기")
            .assertIsDisplayed()
        composeRule.onNodeWithText("방에 공개된 쿠폰과 내가 등록한 비공개 쿠폰만 확인합니다.").assertIsDisplayed()
        composeRule.onNodeWithTag("continue-duplicate-coupon").performClick()
        composeRule.runOnIdle { assertEquals(1, continued) }
    }

    @Test
    fun duplicateCheckCanBeCancelledBeforeExit() {
        var cancellations = 0
        composeRule.setContent {
            GDTheme {
                CouponUploadExitDialog(
                    uploadState = CouponUploadState(CouponUploadStage.CheckingDuplicates),
                    onDismiss = {},
                    onCancelAndExit = { cancellations += 1 }
                )
            }
        }

        composeRule.onNodeWithText("확인을 취소하고 나갈까요?").assertIsDisplayed()
        composeRule.onNodeWithTag("confirm-cancel-upload-and-exit").performClick()
        composeRule.runOnIdle { assertEquals(1, cancellations) }
    }

    @Test
    fun nextImagePrefetchShowsCompactProgressAndReadyFeedback() {
        val state = mutableStateOf(
            NextCouponPrefetchState(
                source = "content://coupon/2",
                stage = NextCouponPrefetchStage.Processing
            )
        )
        composeRule.setContent {
            GDTheme {
                NextCouponPrefetchStatus(state.value)
            }
        }
        composeRule.onNodeWithText("다음 쿠폰을 미리 읽는 중").assertIsDisplayed()

        composeRule.runOnIdle {
            state.value = NextCouponPrefetchState(
                source = "content://coupon/2",
                stage = NextCouponPrefetchStage.Ready,
                analysisReady = true,
                uploadReady = true
            )
        }
        composeRule.onNodeWithText("다음 쿠폰 자동 입력·빠른 업로드 준비 완료")
            .assertIsDisplayed()
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

    private fun createCouponImageFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = File.createTempFile("coupon-draft-", ".png", context.cacheDir)
        Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            imageFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            bitmap.recycle()
        }
        return imageFile
    }
}
