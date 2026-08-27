package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
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

    @Test
    fun prefetchesTheNextTrashPageNearTheEnd() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val coupons = (1..20).map { index ->
            DeletedCoupon(
                couponId = "coupon-$index",
                title = "삭제 쿠폰 $index",
                brand = "브랜드",
                expiresLocalDate = LocalDate.parse("2026-09-01"),
                deletedAt = now.minusSeconds(index.toLong()),
                purgeAt = now.plusSeconds(30L * 24 * 60 * 60)
            )
        }
        var loadMoreCalls = 0

        composeRule.setContent {
            GDTheme {
                CouponTrashContent(
                    coupons = UiState.Success(coupons),
                    busyCouponId = null,
                    busyAction = null,
                    message = null,
                    hasMore = true,
                    onRetry = {},
                    onRestore = {},
                    onPermanentlyDelete = {},
                    onLoadMore = { loadMoreCalls += 1 },
                    now = now
                )
            }
        }

        composeRule.onNodeWithTag("trash-list").performScrollToNode(hasText("다음 삭제 쿠폰 불러오기"))
        composeRule.waitUntil(timeoutMillis = 3_000) { loadMoreCalls > 0 }
        composeRule.onNodeWithText("다음 삭제 쿠폰 불러오기").assertExists()
    }

    @Test
    fun pagingErrorStopsAutoRetryAndOffersManualRetry() {
        val now = Instant.parse("2026-08-15T00:00:00Z")
        val coupon = DeletedCoupon(
            couponId = "coupon-error",
            title = "오류 테스트 쿠폰",
            brand = "브랜드",
            expiresLocalDate = null,
            deletedAt = now,
            purgeAt = now.plusSeconds(30L * 24 * 60 * 60)
        )
        var loadMoreCalls = 0
        var retryCalls = 0

        composeRule.setContent {
            GDTheme {
                CouponTrashContent(
                    coupons = UiState.Success(listOf(coupon)),
                    busyCouponId = null,
                    busyAction = null,
                    message = null,
                    hasMore = true,
                    pagingError = "네트워크 오류",
                    onRetry = { retryCalls += 1 },
                    onRestore = {},
                    onPermanentlyDelete = {},
                    onLoadMore = { loadMoreCalls += 1 },
                    now = now
                )
            }
        }

        composeRule.onNodeWithText("목록을 더 불러오지 못했어요").assertExists()
        composeRule.runOnIdle { assertEquals(0, loadMoreCalls) }
        composeRule.onNodeWithText("다시 시도").performClick()
        composeRule.runOnIdle { assertEquals(1, retryCalls) }
    }

    @Test
    fun emptyCurrentPageStillLoadsTheRemainingTrash() {
        var loadMoreCalls = 0

        composeRule.setContent {
            GDTheme {
                CouponTrashContent(
                    coupons = UiState.Success(emptyList()),
                    busyCouponId = null,
                    busyAction = null,
                    message = null,
                    hasMore = true,
                    onRetry = {},
                    onRestore = {},
                    onPermanentlyDelete = {},
                    onLoadMore = { loadMoreCalls += 1 }
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 3_000) { loadMoreCalls > 0 }
        composeRule.onNodeWithText("복구함이 비어 있어요").assertDoesNotExist()
        composeRule.onNodeWithText("다음 삭제 쿠폰 불러오기").assertExists()
    }
}
