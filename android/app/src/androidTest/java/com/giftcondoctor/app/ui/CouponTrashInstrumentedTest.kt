package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.data.model.DeletedCoupon
import com.giftcondoctor.app.ui.screens.CouponTrashContent
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CouponTrashInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsRetentionAndSupportsRestoreAndPermanentDeleteConfirmation() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val coupon = DeletedCoupon(
            couponId = "coupon-1",
            title = "아메리카노",
            brand = "스타벅스",
            expiresLocalDate = LocalDate.parse("2026-09-01"),
            deletedAt = now,
            purgeAt = now.plusSeconds(30L * 24 * 60 * 60)
        )
        var restored: String? = null
        var permanentlyDeleted: String? = null

        composeRule.setContent {
            GDTheme {
                CouponTrashContent(
                    coupons = UiState.Success(listOf(coupon)),
                    busyCouponId = null,
                    busyAction = null,
                    message = null,
                    onRetry = {},
                    onRestore = { restored = it },
                    onPermanentlyDelete = { permanentlyDeleted = it },
                    now = now
                )
            }
        }

        composeRule.onNodeWithText("삭제 후 30일 동안 보관해요").assertIsDisplayed()
        composeRule.onNodeWithText("영구 삭제까지 30일").assertIsDisplayed()
        composeRule.onNodeWithText("복원").performClick()
        composeRule.runOnIdle { assertEquals("coupon-1", restored) }

        composeRule.onNodeWithText("영구 삭제").performClick()
        composeRule.onNodeWithText("영구 삭제할까요?").assertIsDisplayed()
        composeRule.onAllNodesWithText("영구 삭제")[1].performClick()
        composeRule.runOnIdle { assertEquals("coupon-1", permanentlyDeleted) }
    }
}
