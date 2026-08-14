package com.giftcondoctor.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.data.CouponImageFile
import com.giftcondoctor.app.data.CouponImageFileStore
import com.giftcondoctor.app.ui.screens.CouponImageDialog
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.CouponOriginalImageState
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CouponImageDialogInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @After
    fun tearDown() {
        CouponImageFileStore.clearTracked()
    }

    @Test
    fun supportsDoubleTapAndCloseAfterHighResolutionDecode() {
        val bitmap = Bitmap.createBitmap(600, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val imageFile = createImageFile(bitmap)
        var dismissed = false
        var visible by mutableStateOf(true)

        composeRule.setContent {
            GDTheme {
                if (visible) {
                    CouponImageDialog(
                        previewBitmap = bitmap.asImageBitmap(),
                        imageState = CouponOriginalImageState.Ready(imageFile),
                        onRetry = {},
                        onDismiss = {
                            dismissed = true
                            visible = false
                        }
                    )
                }
            }
        }

        composeRule.onNodeWithTag("zoomed-coupon-image")
            .assertExists()
            .performTouchInput { doubleClick() }
        composeRule.onNodeWithText("2.0×").assertExists()
        composeRule.onNodeWithContentDescription("확대").performClick()
        composeRule.onNodeWithText("3.0×").assertExists()
        composeRule.onNodeWithText("원본 맞춤").performClick()
        composeRule.onNodeWithText("1.0×").assertExists()
        composeRule.onNodeWithText("밝기 최적화됨 · 두 손가락 확대 · 두 번 탭").assertExists()
        composeRule.onNodeWithContentDescription("닫기").performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    @Test
    fun opensWithPreviewWhileOriginalLoadsThenUpgradesInPlace() {
        val bitmap = Bitmap.createBitmap(600, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        val imageFile = createImageFile(bitmap)
        var imageState by mutableStateOf<CouponOriginalImageState>(CouponOriginalImageState.Idle)

        composeRule.setContent {
            GDTheme {
                CouponImageDialog(
                    previewBitmap = bitmap.asImageBitmap(),
                    imageState = imageState,
                    onRetry = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("zoomed-coupon-image").assertExists()
        composeRule.onNodeWithText("미리보기를 먼저 표시했어요 · 선명한 원본을 불러오는 중이에요")
            .assertExists()
        composeRule.runOnIdle { imageState = CouponOriginalImageState.Loading }
        composeRule.onNodeWithText("미리보기를 먼저 표시했어요 · 선명한 원본을 불러오는 중이에요")
            .assertExists()
        composeRule.runOnIdle { imageState = CouponOriginalImageState.Ready(imageFile) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("밝기 최적화됨 · 두 손가락 확대 · 두 번 탭")
                .fetchSemanticsNodes(atLeastOneRootRequired = false)
                .isNotEmpty()
        }
    }

    @Test
    fun keepsPreviewAndOffersRetryWhenOriginalFails() {
        val bitmap = Bitmap.createBitmap(600, 1_200, Bitmap.Config.ARGB_8888).apply {
            eraseColor(android.graphics.Color.WHITE)
        }
        var retried = false

        composeRule.setContent {
            GDTheme {
                CouponImageDialog(
                    previewBitmap = bitmap.asImageBitmap(),
                    imageState = CouponOriginalImageState.Error("원본 요청 실패"),
                    onRetry = { retried = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("zoomed-coupon-image").assertExists()
        composeRule.onNodeWithText("원본을 불러오지 못해 미리보기로 확대 중이에요").assertExists()
        composeRule.onNodeWithText("원본 다시 불러오기").performClick()
        composeRule.runOnIdle { assertTrue(retried) }
    }

    private fun createImageFile(bitmap: Bitmap): CouponImageFile {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val destination = CouponImageFileStore.create(context)
        destination.outputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        return CouponImageFileStore.complete(destination)
    }
}
