package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.ui.components.NotificationOptInPrompt
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 첫 쿠폰 저장 직후의 알림 제안 다이얼로그.
 *
 * 이 제안이 존재하는 이유는 Android 권한의 비대칭성이다. 두 번 거절하면 시스템이
 * 다시 묻지 않으므로, 거절 한 번의 비용이 수락 한 번의 이득보다 훨씬 크다. 따라서
 * 사용자가 가장 납득하기 쉬운 순간에만 물어야 한다.
 *
 * 고정하는 계약은 두 가지다.
 *   1. 방금 저장한 쿠폰의 만료 시점을 문구에 담아 제안이 구체적이어야 한다
 *   2. "나중에" 는 권한 요청을 유발하지 않아야 한다. 시스템 거절 횟수를 소진하면
 *      되돌릴 수 없다
 */
@RunWith(AndroidJUnit4::class)
class NotificationOptInPromptInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun promptNamesTheExpiryOfTheCouponJustSaved() {
        composeRule.setContent {
            GDTheme {
                NotificationOptInPrompt(expiryLabel = "D-7", onAllow = {}, onDismiss = {})
            }
        }

        composeRule.onNodeWithText("만료 전에 알려드릴까요?").assertIsDisplayed()
        // 일반론이 아니라 방금 저장한 쿠폰을 가리켜야 설득력이 있다.
        composeRule.onNodeWithText(
            "이 쿠폰은 D-7 만료돼요. 알림을 켜두면 만료 전에 미리 알려드릴게요. " +
                "매일 오전 9시에 만료 예정 쿠폰만 간결하게 보냅니다."
        ).assertIsDisplayed()
    }

    @Test
    fun allowTriggersPermissionRequestExactlyOnce() {
        var allowed = 0
        var dismissed = 0
        composeRule.setContent {
            GDTheme {
                NotificationOptInPrompt(
                    expiryLabel = "오늘 만료",
                    onAllow = { allowed += 1 },
                    onDismiss = { dismissed += 1 }
                )
            }
        }

        composeRule.onNodeWithTag("optin-allow-notifications").performClick()
        composeRule.runOnIdle {
            assertEquals(1, allowed)
            assertEquals(0, dismissed)
        }
    }

    @Test
    fun laterNeverTriggersAPermissionRequest() {
        var allowed = false
        var dismissed = false
        composeRule.setContent {
            GDTheme {
                NotificationOptInPrompt(
                    expiryLabel = "D-3",
                    onAllow = { allowed = true },
                    onDismiss = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("optin-skip-notifications").performClick()
        composeRule.runOnIdle {
            assertTrue(dismissed)
            // 시스템 거절 횟수는 두 번뿐이다. "나중에" 로 소진하면 안 된다.
            assertEquals(false, allowed)
        }
    }
}
