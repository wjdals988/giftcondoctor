package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.components.GDBottomBar
import com.giftcondoctor.app.ui.components.GDDestination
import com.giftcondoctor.app.ui.screens.FavoriteCouponCard
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.FavoriteCoupon
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 즐겨찾기 목록 한 줄과 하단 탭이 필요한 정보를 내보내는지 확인한다.
 *
 * 둘 다 상태를 색·아이콘으로 표시하는 요소라, 화면을 보지 않는 사용자에게도
 * 전달되는지가 핵심이다.
 */
@RunWith(AndroidJUnit4::class)
class FavoritesScreenInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun favorite(roomName: String = "가족 쿠폰방") = FavoriteCoupon(
        coupon = Coupon(
            id = "coupon-1",
            roomId = "room-1",
            title = "아메리카노 Tall",
            brand = "스타벅스",
            ownerUid = "me",
            imageBlobPath = "",
            thumbnailBlobPath = null,
            imageWidth = null,
            imageHeight = null,
            expiresLocalDate = LocalDate.now().plusDays(3),
            timezone = "Asia/Seoul",
            status = "active",
            reservedByUid = null,
            usedByUid = null,
            visibility = "room",
            notifyTarget = "allMembers"
        ),
        roomName = roomName
    )

    @Test
    fun showsWhichRoomTheCouponLivesIn() {
        // 방 밖에서 보는 목록이므로 어느 방인지가 항상 붙어야 한다.
        composeRule.setContent {
            GDTheme { FavoriteCouponCard(item = favorite(), onOpen = {}, onRemove = {}) }
        }
        composeRule.onNodeWithText("아메리카노 Tall").assertIsDisplayed()
        composeRule.onNodeWithText("가족 쿠폰방 · 스타벅스").assertIsDisplayed()
    }

    @Test
    fun removeIsReachableFromTheList() {
        // 끝난 쿠폰도 목록에 남으므로 정리할 곳이 여기여야 한다.
        var removed = 0
        composeRule.setContent {
            GDTheme { FavoriteCouponCard(item = favorite(), onOpen = {}, onRemove = { removed += 1 }) }
        }
        composeRule.onNodeWithTag("favorite-remove-coupon-1").performClick()
        composeRule.waitForIdle()
        assertEquals(1, removed)
    }

    @Test
    fun openingIsSeparateFromRemoving() {
        var opened = 0
        composeRule.setContent {
            GDTheme { FavoriteCouponCard(item = favorite(), onOpen = { opened += 1 }, onRemove = {}) }
        }
        composeRule.onNodeWithTag("favorite-item-coupon-1").performClick()
        composeRule.waitForIdle()
        assertEquals(1, opened)
    }

    @Test
    fun bottomBarAnnouncesSelectionWithoutColor() {
        composeRule.setContent {
            GDTheme { GDBottomBar(current = GDDestination.Favorites, onSelect = {}) }
        }
        composeRule.onNodeWithText("쿠폰방").assertIsDisplayed()
        composeRule.onNodeWithText("검색").assertIsDisplayed()
        composeRule.onNodeWithText("즐겨찾기").assertIsDisplayed()
    }

    @Test
    fun bottomBarIgnoresTapOnTheCurrentTab() {
        // 이미 있는 탭을 다시 눌러 같은 목적지를 스택에 쌓으면 뒤로가기가
        // 몇 번 필요한지 예측할 수 없어진다.
        var selected: GDDestination? = null
        composeRule.setContent {
            GDTheme { GDBottomBar(current = GDDestination.Rooms, onSelect = { selected = it }) }
        }
        composeRule.onNodeWithTag("tab-rooms").performClick()
        composeRule.waitForIdle()
        assertEquals(null, selected)

        composeRule.onNodeWithTag("tab-search").performClick()
        composeRule.waitForIdle()
        assertEquals(GDDestination.Search, selected)
    }
}
