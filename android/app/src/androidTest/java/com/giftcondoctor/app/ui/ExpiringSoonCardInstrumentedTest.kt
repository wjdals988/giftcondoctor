package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.data.model.ExpiringCoupon
import com.giftcondoctor.app.data.model.ExpiringCoupons
import com.giftcondoctor.app.ui.screens.ExpiringSoonCard
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 방을 가로지르는 만료 임박 요약 카드.
 *
 * 이 카드의 존재 이유는 "방을 거치지 않고 지금 써야 할 쿠폰에 도달하는 것" 이다.
 * 따라서 항목을 탭했을 때 방이 아니라 쿠폰으로 가야 하고, 결과가 잘렸다면 그 사실을
 * 숨기지 않아야 한다.
 */
@RunWith(AndroidJUnit4::class)
class ExpiringSoonCardInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    private fun coupon(id: String, title: String, days: Int = 3) = ExpiringCoupon(
        roomId = "room-$id",
        roomName = "내 쿠폰",
        couponId = id,
        title = title,
        brand = "투썸",
        expiresLocalDate = "2026-12-31",
        daysLeft = days
    )

    @Test
    fun emptySummaryRendersNothing() {
        // "0개" 를 위해 화면 자리를 차지할 이유가 없다.
        composeRule.setContent {
            GDTheme {
                ExpiringSoonCard(
                    summary = ExpiringCoupons(days = 7, coupons = emptyList(), roomCount = 2, truncated = false),
                    onOpenCoupon = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithTag("expiring-soon-card").assertDoesNotExist()
    }

    @Test
    fun tappingAnItemOpensThatCouponNotItsRoom() {
        var openedRoom: String? = null
        var openedCoupon: String? = null
        composeRule.setContent {
            GDTheme {
                ExpiringSoonCard(
                    summary = ExpiringCoupons(
                        days = 7,
                        coupons = listOf(coupon("c1", "아메리카노")),
                        roomCount = 1,
                        truncated = false
                    ),
                    onOpenCoupon = { roomId, couponId ->
                        openedRoom = roomId
                        openedCoupon = couponId
                    }
                )
            }
        }

        composeRule.onNodeWithTag("expiring-soon-item-c1").performClick()
        composeRule.runOnIdle {
            assertEquals("room-c1", openedRoom)
            assertEquals("c1", openedCoupon)
        }
    }

    @Test
    fun onlyThreeItemsArePreviewedAndTheRestAreCounted() {
        // 방 목록이 주인공인 화면이므로 요약이 화면을 밀어내면 안 된다.
        val many = (1..5).map { coupon("c$it", "쿠폰$it") }
        composeRule.setContent {
            GDTheme {
                ExpiringSoonCard(
                    summary = ExpiringCoupons(days = 7, coupons = many, roomCount = 1, truncated = false),
                    onOpenCoupon = { _, _ -> }
                )
            }
        }

        composeRule.onNodeWithTag("expiring-soon-item-c3").assertIsDisplayed()
        composeRule.onNodeWithTag("expiring-soon-item-c4").assertDoesNotExist()
        composeRule.onNodeWithText("외 2개").assertIsDisplayed()
    }

    @Test
    fun truncationIsDisclosedInsteadOfHidden() {
        composeRule.setContent {
            GDTheme {
                ExpiringSoonCard(
                    summary = ExpiringCoupons(
                        days = 7,
                        coupons = listOf(coupon("c1", "아메리카노")),
                        roomCount = 40,
                        truncated = true
                    ),
                    onOpenCoupon = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText("일부만 표시했어요. 방에 들어가면 전체를 볼 수 있습니다.").assertIsDisplayed()
    }

    @Test
    fun headerStatesTheWindowAndCount() {
        composeRule.setContent {
            GDTheme {
                ExpiringSoonCard(
                    summary = ExpiringCoupons(
                        days = 7,
                        coupons = listOf(coupon("c1", "아메리카노"), coupon("c2", "라떼")),
                        roomCount = 1,
                        truncated = false
                    ),
                    onOpenCoupon = { _, _ -> }
                )
            }
        }
        composeRule.onNodeWithText("7일 안에 만료 2개").assertIsDisplayed()
    }
}
