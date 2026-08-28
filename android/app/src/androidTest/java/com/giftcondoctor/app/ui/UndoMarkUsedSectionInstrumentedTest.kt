package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.screens.UndoMarkUsedSection
import com.giftcondoctor.app.ui.theme.GDTheme
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 실행 취소 진입점이 서버 규칙과 같은 창에서만 보이는지 확인한다.
 *
 * 창이 지난 뒤에도 버튼이 남아 있으면 눌러도 Firestore 규칙에 막혀 실패하는 죽은
 * 버튼이 된다. 그래서 "보인다" 보다 "사라진다" 쪽이 더 중요한 검증이다.
 */
@RunWith(AndroidJUnit4::class)
class UndoMarkUsedSectionInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun usedCoupon(usedAt: Instant?, usedByUid: String?) = Coupon(
        id = "coupon-1",
        roomId = "room-1",
        title = "아메리카노",
        brand = "카페",
        ownerUid = "me",
        imageBlobPath = "blob",
        thumbnailBlobPath = null,
        imageWidth = null,
        imageHeight = null,
        expiresLocalDate = LocalDate.of(2026, 12, 31),
        timezone = "Asia/Seoul",
        status = "used",
        reservedByUid = null,
        usedByUid = usedByUid,
        usedAt = usedAt,
        visibility = "room",
        notifyTarget = "all"
    )

    private fun render(coupon: Coupon, currentUid: String?) {
        composeRule.setContent {
            GDTheme {
                UndoMarkUsedSection(
                    coupon = coupon,
                    currentUid = currentUid,
                    actionBusy = false,
                    onUndoUsed = {}
                )
            }
        }
    }

    @Test
    fun showsUndoInsideTheFiveMinuteWindow() {
        render(usedCoupon(Instant.now().minusSeconds(60), "me"), "me")
        composeRule.onNodeWithTag("coupon-undo-used").assertIsDisplayed()
        composeRule.onNodeWithTag("coupon-undo-used-button").assertIsDisplayed()
    }

    @Test
    fun hidesUndoAfterTheWindowClosed() {
        render(usedCoupon(Instant.now().minusSeconds(6 * 60), "me"), "me")
        composeRule.onNodeWithTag("coupon-undo-used").assertDoesNotExist()
    }

    @Test
    fun hidesUndoForSomeoneElsesUse() {
        // 규칙은 처리한 본인만 되돌릴 수 있게 한다. 남의 사용에 버튼을 보이면 안 된다.
        render(usedCoupon(Instant.now().minusSeconds(60), "someone-else"), "me")
        composeRule.onNodeWithTag("coupon-undo-used").assertDoesNotExist()
    }

    @Test
    fun hidesUndoWhenUsedAtIsMissing() {
        // 기존 문서에는 usedAt 이 없을 수 있다. 시각을 모르면 창을 계산할 수 없으므로 감춘다.
        render(usedCoupon(null, "me"), "me")
        composeRule.onNodeWithTag("coupon-undo-used").assertDoesNotExist()
    }
}
