package com.giftcondoctor.app.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.screens.RoomDashboard
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 즐겨찾기가 목록에서 어떻게 보이고 눌리는지 확인한다.
 *
 * 정렬 규칙 자체는 `CouponListRulesTest` 가 덮는다. 여기서는 화면이 그 규칙을
 * 실제로 통과시키는지, 그리고 별표 상태가 화면을 못 보는 사용자에게도 전달되는지를
 * 본다. 아이콘 모양은 스크린리더가 읽지 못한다.
 */
@RunWith(AndroidJUnit4::class)
class CouponFavoriteInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun coupon(id: String, title: String, daysUntilExpiry: Long) = Coupon(
        id = id,
        roomId = "room-1",
        title = title,
        brand = "카페",
        ownerUid = "owner",
        imageBlobPath = "",
        thumbnailBlobPath = null,
        imageWidth = null,
        imageHeight = null,
        expiresLocalDate = LocalDate.now().plusDays(daysUntilExpiry),
        timezone = "Asia/Seoul",
        status = "active",
        reservedByUid = null,
        usedByUid = null,
        visibility = "room",
        notifyTarget = "allMembers"
    )

    private fun render(
        favorites: Set<String>,
        onToggleFavorite: (String) -> Unit = {}
    ) {
        val coupons = listOf(
            coupon("soon", "곧 만료되는 쿠폰", 1),
            coupon("fav", "즐겨찾는 쿠폰", 300)
        )
        composeRule.setContent {
            GDTheme {
                RoomDashboard(
                    roomId = "room-1",
                    coupons = coupons,
                    isOwner = true,
                    hasMore = false,
                    isLoadingMore = false,
                    pagingError = null,
                    onLoadMore = {},
                    onRetry = {},
                    onOpenCoupon = {},
                    onAddCoupon = {},
                    favoriteCouponIds = favorites,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier
                )
            }
        }
    }

    @Test
    fun starStateIsReadableWithoutSeeingTheIcon() {
        render(favorites = setOf("fav"))
        // 즐겨찾기한 쿠폰은 해제를, 안 한 쿠폰은 추가를 제안해야 한다.
        composeRule.onNodeWithContentDescription("즐겨찾기 해제").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("즐겨찾기에 추가").assertIsDisplayed()
    }

    @Test
    fun tappingTheStarReportsTheCoupon() {
        var toggled: String? = null
        render(favorites = emptySet(), onToggleFavorite = { toggled = it })
        composeRule.onNodeWithTag("coupon-favorite-fav").performClick()
        composeRule.waitForIdle()
        assertEquals("fav", toggled)
    }

    @Test
    fun sortLabelTellsWhichOrderIsInEffect() {
        // 순서가 왜 이렇게 됐는지 화면이 말하지 않으면 사용자는 목록이 고장난 줄 안다.
        render(favorites = setOf("fav"))
        composeRule.onNodeWithText("불러온 2개 중 2개 · 즐겨찾기 먼저, 만료 임박순").assertExists()
    }

    @Test
    fun sortLabelFallsBackWhenNothingIsFavorited() {
        render(favorites = emptySet())
        composeRule.onNodeWithText("불러온 2개 중 2개 · 만료 임박순").assertExists()
    }
}
