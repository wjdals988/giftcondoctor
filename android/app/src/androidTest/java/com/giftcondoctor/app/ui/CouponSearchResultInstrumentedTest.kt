package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.data.model.CouponSearchHit
import com.giftcondoctor.app.ui.screens.CouponSearchHitCard
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 방을 가로지르는 검색 결과가 "어느 방의 쿠폰인지" 를 반드시 보이는지 확인한다.
 *
 * 방 안 목록에서는 방 이름이 자명하지만 여기서는 아니다. 방 밖에서 찾았으니
 * 어디에 있는지를 알려줘야 사용자가 다음 행동을 정할 수 있다. 이 정보가 빠지면
 * 결과는 보이는데 쓸 수가 없다.
 */
@RunWith(AndroidJUnit4::class)
class CouponSearchResultInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun hit(status: String = "active") = CouponSearchHit(
        roomId = "room-1",
        roomName = "가족 쿠폰방",
        couponId = "coupon-1",
        title = "아메리카노 Tall",
        brand = "스타벅스",
        expiresLocalDate = "2099-12-31",
        status = status
    )

    @Test
    fun showsRoomNameAndBrandTogether() {
        composeRule.setContent { GDTheme { CouponSearchHitCard(hit = hit(), onOpen = {}) } }
        composeRule.onNodeWithText("아메리카노 Tall").assertIsDisplayed()
        composeRule.onNodeWithText("가족 쿠폰방 · 스타벅스").assertIsDisplayed()
    }

    @Test
    fun opensTheCouponWhenTapped() {
        var opened = 0
        composeRule.setContent { GDTheme { CouponSearchHitCard(hit = hit(), onOpen = { opened += 1 }) } }
        composeRule.onNodeWithTag("cross-room-search-item-coupon-1").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun rendersUsedCouponsToo() {
        // 검색은 "지금 쓸 것" 만 찾는 도구가 아니다. "그 쿠폰 썼던가?" 를 확인하는
        // 질의가 실제로 많고, 그때 결과가 비면 등록 자체를 안 했다고 오해한다.
        composeRule.setContent { GDTheme { CouponSearchHitCard(hit = hit(status = "used"), onOpen = {}) } }
        composeRule.onNodeWithText("아메리카노 Tall").assertIsDisplayed()
    }
}
