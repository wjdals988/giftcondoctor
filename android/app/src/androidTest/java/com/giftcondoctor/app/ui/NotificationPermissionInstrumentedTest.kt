package com.giftcondoctor.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.ui.components.NotificationPermissionState
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 알림 권한 카드의 상태별 동작을 고정한다.
 *
 * 2026-08-28 실기기 확인에서 "푸시가 오지 않는다" 는 신고의 원인이 이 권한 하나였다.
 * 서버·FCM·Firebase 는 모두 정상이었고 권한을 부여하자 즉시 도착했다. 앱은 이미
 * 배너로 안내하고 있었지만 방 목록 카드들과 같은 시각적 무게라 사용자가 지나쳤다.
 *
 * 여기서 고정하는 계약은 두 가지다.
 *   1. 권한이 없으면 눈에 띄어야 하고, 있으면 조용해야 한다
 *   2. 영구 거절 상태에서는 눌러도 소용없는 요청 버튼 대신 설정 경로를 제공한다
 */
@RunWith(AndroidJUnit4::class)
class NotificationPermissionInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun grantedStateStaysQuiet() {
        composeRule.setContent {
            GDTheme {
                NotificationPermissionStatus(
                    NotificationPermissionState(runtimeRequired = true, granted = true, request = {})
                )
            }
        }

        composeRule.onNodeWithText("켜짐").assertIsDisplayed()
        // 정상 상태에서는 행동을 요구하지 않는다.
        composeRule.onNodeWithTag("request-notification-permission").assertDoesNotExist()
        composeRule.onNodeWithTag("open-notification-settings").assertDoesNotExist()
    }

    @Test
    fun deniedStateAsksForPermissionAndExplainsTheConsequence() {
        var requested = false
        composeRule.setContent {
            GDTheme {
                NotificationPermissionStatus(
                    NotificationPermissionState(
                        runtimeRequired = true,
                        granted = false,
                        request = { requested = true }
                    )
                )
            }
        }

        composeRule.onNodeWithText("꺼짐").assertIsDisplayed()
        // 결과를 말해야 사용자가 켤 이유를 안다.
        composeRule.onNodeWithText(
            "지금은 쿠폰이 만료돼도 알려드릴 수 없어요. 알림을 켜면 만료 전에 알려드릴게요."
        ).assertIsDisplayed()

        composeRule.onNodeWithTag("request-notification-permission").performClick()
        composeRule.runOnIdle { assertTrue(requested) }
    }

    @Test
    fun permanentlyDeniedStateOffersSettingsInsteadOfADeadButton() {
        var requested = false
        var settingsOpened = false
        composeRule.setContent {
            GDTheme {
                NotificationPermissionStatus(
                    NotificationPermissionState(
                        runtimeRequired = true,
                        granted = false,
                        request = { requested = true },
                        permanentlyDenied = true,
                        openSystemSettings = { settingsOpened = true }
                    )
                )
            }
        }

        // 시스템이 더 이상 대화상자를 띄우지 않으므로 요청 버튼은 없어야 한다.
        composeRule.onNodeWithTag("request-notification-permission").assertDoesNotExist()
        composeRule.onNodeWithTag("open-notification-settings").performClick()
        composeRule.runOnIdle {
            assertTrue(settingsOpened)
            assertFalse("영구 거절 상태에서 권한 요청을 시도하면 안 된다", requested)
        }
    }

    @Test
    fun legacyDevicesWithoutRuntimePermissionShowNoAction() {
        composeRule.setContent {
            GDTheme {
                NotificationPermissionStatus(
                    NotificationPermissionState(runtimeRequired = false, granted = false, request = {})
                )
            }
        }

        composeRule.onNodeWithText("사용 가능").assertIsDisplayed()
        composeRule.onNodeWithTag("request-notification-permission").assertDoesNotExist()
    }
}
