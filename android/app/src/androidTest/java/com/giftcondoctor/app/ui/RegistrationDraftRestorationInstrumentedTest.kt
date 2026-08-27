package com.giftcondoctor.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.giftcondoctor.app.core.CouponTextSuggestion
import com.giftcondoctor.app.core.DetectedCouponBarcode
import com.giftcondoctor.app.ui.screens.CouponRegistrationDraft
import com.giftcondoctor.app.ui.screens.rememberCouponRegistrationDraft
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RegistrationDraftRestorationInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun manualDraftAndEditPrioritySurviveSavedStateRestoration() {
        val restorationTester = StateRestorationTester(composeRule)
        var currentDraft: CouponRegistrationDraft? = null
        restorationTester.setContent {
            GDTheme {
                val draft = rememberCouponRegistrationDraft(
                    imageSource = "content://coupon/1",
                    defaultExpiry = "2026-08-22"
                )
                currentDraft = draft
                Text("${draft.title}|${draft.brand}|${draft.expiresLocalDate}|${draft.barcodeValue}")
            }
        }

        composeRule.runOnIdle {
            currentDraft?.updateTitle("직접 입력 이름")
            currentDraft?.updateBrand("직접 입력 브랜드")
            currentDraft?.updateExpiry("2027-01-01")
            currentDraft?.startManualBarcodeEntry()
            currentDraft?.updateBarcodeValue("MANUAL-123")
            currentDraft?.updateBarcodeFormat("QR_CODE")
        }
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.runOnIdle {
            currentDraft?.applySuggestion(
                CouponTextSuggestion(
                    title = "늦은 OCR 이름",
                    brand = "늦은 OCR 브랜드",
                    expiresLocalDate = LocalDate.parse("2026-09-01")
                )
            )
            currentDraft?.applyDetectedBarcode(DetectedCouponBarcode("AUTO-999", "CODE_128"))
        }

        composeRule.onNodeWithText("직접 입력 이름|직접 입력 브랜드|2027-01-01|MANUAL-123")
            .assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals("QR_CODE", currentDraft?.barcodeFormat)
        }
    }
}
