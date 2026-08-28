package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 방 목록 빈 상태의 동작 위계.
 *
 * 이 앱은 쿠폰을 반드시 방 안에 저장하는데, 첫 사용자의 필요는 대개 혼자 저장하고
 * 알림 받는 것이다. 방 이름을 정하는 결정은 그 사용자에게 가치가 없으면서 첫
 * 관문이 된다. 그래서 이름 없이 시작하는 경로를 1순위로 두고, 공유용 방 만들기는
 * 3순위 TextButton 으로 내렸다.
 *
 * 세 동작이 모두 같은 무게면 사용자가 무엇을 먼저 누를지 판단해야 하고, 그 판단
 * 자체가 비용이다. 위계가 유지되는지 고정한다.
 */
@RunWith(AndroidJUnit4::class)
class EmptyStateActionsInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun emptyStateRendersAllThreeActionsWhenProvided() {
        composeRule.setContent {
            GDTheme {
                EmptyState(
                    title = "첫 쿠폰을 저장해 보세요",
                    message = "혼자 모아두고 만료 전에 알림을 받을 수 있어요.",
                    primaryActionLabel = "내 쿠폰 바로 시작",
                    onPrimaryAction = {},
                    secondaryActionLabel = "초대코드로 입장",
                    onSecondaryAction = {},
                    tertiaryActionLabel = "함께 쓸 방 만들기",
                    onTertiaryAction = {}
                )
            }
        }

        composeRule.onNodeWithText("내 쿠폰 바로 시작").assertIsDisplayed()
        composeRule.onNodeWithText("초대코드로 입장").assertIsDisplayed()
        composeRule.onNodeWithText("함께 쓸 방 만들기").assertIsDisplayed()
    }

    @Test
    fun eachActionInvokesOnlyItsOwnCallback() {
        var primary = 0
        var secondary = 0
        var tertiary = 0
        composeRule.setContent {
            GDTheme {
                EmptyState(
                    message = "테스트",
                    primaryActionLabel = "1순위",
                    onPrimaryAction = { primary += 1 },
                    secondaryActionLabel = "2순위",
                    onSecondaryAction = { secondary += 1 },
                    tertiaryActionLabel = "3순위",
                    onTertiaryAction = { tertiary += 1 }
                )
            }
        }

        composeRule.onNodeWithText("3순위").performClick()
        composeRule.runOnIdle {
            assertEquals(0, primary)
            assertEquals(0, secondary)
            assertEquals(1, tertiary)
        }
    }

    @Test
    fun tertiaryActionIsOptionalAndAbsentByDefault() {
        // 기존 호출부는 3순위를 넘기지 않는다. 그 경우 아무것도 렌더링되지 않아야 한다.
        composeRule.setContent {
            GDTheme {
                EmptyState(
                    message = "테스트",
                    primaryActionLabel = "1순위",
                    onPrimaryAction = {}
                )
            }
        }

        composeRule.onNodeWithText("1순위").assertIsDisplayed()
        composeRule.onNodeWithText("3순위").assertDoesNotExist()
    }

    @Test
    fun personalRoomNameIsAFixedDefaultSoUsersNeedNotDecide() {
        // 이름을 정하는 결정을 없애는 것이 이 변경의 목적이므로 기본값이 고정이어야 한다.
        assertEquals("내 쿠폰", AppConstants.PERSONAL_ROOM_NAME)
    }
}
