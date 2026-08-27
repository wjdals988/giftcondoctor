package com.giftcondoctor.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.SharedImageImportState
import android.net.Uri
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.ui.components.NotificationPermissionState
import com.giftcondoctor.app.ui.screens.LoginScreen
import com.giftcondoctor.app.ui.screens.MemberRow
import com.giftcondoctor.app.ui.screens.NotificationSettingsActions
import com.giftcondoctor.app.ui.screens.NotificationSettingsContent
import com.giftcondoctor.app.ui.screens.NotificationSettingsUiState
import com.giftcondoctor.app.ui.screens.RoomSettingsActions
import com.giftcondoctor.app.ui.screens.RoomSettingsContent
import com.giftcondoctor.app.ui.screens.RoomSettingsUiState
import com.giftcondoctor.app.ui.screens.SharedImageImportBanner
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AccessibilityFlowsInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginKeepsPrimaryAndFallbackActionsReachableAt200PercentFont() {
        setLargeFontContent { LoginScreen(SessionViewModel()) }

        listOf("login-google", "login-submit", "login-register").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertMinimumTarget(tag)
        }
        composeRule.onNodeWithTag("login-email").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("login-password").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun notificationSettingsExposeLabeledSwitchAndActionsAt200PercentFont() {
        setLargeFontContent {
            NotificationSettingsContent(
                modifier = Modifier,
                state = NotificationSettingsUiState(
                    mode = NotificationMode.Basic,
                    pushEnabled = true,
                    canUsePush = true,
                    busy = false,
                    busyAction = null,
                    testPushBusy = false,
                    expiryTestPushBusy = false,
                    joinedTestRoom = false,
                    testRoomBusy = false,
                    testRoomMessage = null,
                    message = null
                ),
                notificationPermission = NotificationPermissionState(true, true, {}),
                actions = NotificationSettingsActions({}, {}, {}, {}, {}, {}, {})
            )
        }

        composeRule.onNodeWithTag("push-enabled-switch")
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("푸시 알림 사용")))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "켜짐"))
        listOf(
            "notification-mode-basic",
            "notification-save",
            "push-diagnostic",
            "expiry-push-test",
            "join-push-test-room",
            "app-info"
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertMinimumTarget(tag)
        }
    }

    @Test
    fun memberRoomSettingsHideOwnerOnlyInviteActionAt200PercentFont() {
        setLargeFontContent {
            RoomSettingsContent(
                state = RoomSettingsUiState(testRoom(), false, false, null, null),
                actions = RoomSettingsActions({}, {}, {}, {}, {})
            )
        }

        composeRule.onNodeWithTag("regenerate-invite").assertDoesNotExist()
        composeRule.onNodeWithText("아주 긴 가족 공동 쿠폰방 이름").assertIsDisplayed()
        listOf("open-notification-settings", "open-trash", "leave-room").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertMinimumTarget(tag)
        }
    }

    @Test
    fun ownerRoomSettingsKeepPrivilegedActionsReachableAt200PercentFont() {
        setLargeFontContent {
            RoomSettingsContent(
                state = RoomSettingsUiState(testRoom(), true, false, null, null),
                actions = RoomSettingsActions({}, {}, {}, {}, {})
            )
        }

        listOf("regenerate-invite", "delete-room").forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
            assertMinimumTarget(tag)
        }
    }

    @Test
    fun memberRemovalRequiresConfirmationAt200PercentFont() {
        var removed = false
        val member = RoomMember(
            uid = "member-1",
            role = "member",
            displayName = "아주 긴 이름을 가진 가족 구성원",
            notificationEnabled = true,
            notificationMode = null,
            notificationDays = null
        )
        setLargeFontContent {
            MemberRow(member = member, canRemove = true, onRemove = { removed = true })
        }

        assertMinimumTarget("remove-member-${member.uid}")
        composeRule.onNodeWithTag("remove-member-${member.uid}").performClick()
        composeRule.runOnIdle { assertFalse(removed) }
        composeRule.onNodeWithText("멤버 제거").assertIsDisplayed()
        composeRule.onNodeWithText("${member.displayName}님을 이 방에서 제거할까요?").assertIsDisplayed()
        assertMinimumTarget("confirm-remove-member")
        composeRule.onNodeWithTag("confirm-remove-member").performClick()
        composeRule.runOnIdle { assertTrue(removed) }
    }

    @Test
    fun sharedImageRoomPickerExplainsNextStepAndKeepsCancelReachableAt200PercentFont() {
        var dismissed = false
        setLargeFontContent {
            SharedImageImportBanner(
                state = SharedImageImportState.Ready(
                    List(3) { index -> Uri.parse("file:///tmp/shared-coupon-$index.image") }
                ),
                onDismiss = { dismissed = true }
            )
        }

        composeRule.onNodeWithText("3장을 등록할 쿠폰방을 선택하세요").assertIsDisplayed()
        composeRule.onNodeWithText("방을 고른 뒤 한 장씩 내용을 확인하고 저장합니다.")
            .assertIsDisplayed()
        assertMinimumTarget("dismiss-shared-image")
        composeRule.onNodeWithTag("dismiss-shared-image").performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }

    private fun setLargeFontContent(content: @Composable () -> Unit) {
        composeRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale = 2f)
            ) {
                GDTheme { content() }
            }
        }
    }

    private fun assertMinimumTarget(tag: String) {
        val node = composeRule.onNodeWithTag(tag).assertHasClickAction()
        val bounds = node.fetchSemanticsNode().touchBoundsInRoot
        val minimumPixels = 48f * InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
        assertTrue("$tag 터치 너비가 48dp보다 작습니다: ${bounds.width}px", bounds.width >= minimumPixels)
        assertTrue("$tag 터치 높이가 48dp보다 작습니다: ${bounds.height}px", bounds.height >= minimumPixels)
    }

    private fun testRoom() = Room(
        id = "room-1",
        name = "아주 긴 가족 공동 쿠폰방 이름",
        ownerUid = "owner-1",
        inviteCode = "1234",
        inviteExpiresAt = Instant.parse("2026-08-20T00:00:00Z"),
        defaultNotificationMode = "basic",
        defaultNotificationDays = listOf(7, 3, 1, 0)
    )

    @Test
    fun screenAndSectionTitlesAreExposedAsHeadingsForTalkBack() {
        // TalkBack 은 heading 표식이 있는 노드만 제목 단위로 건너뛴다. 표식이 없으면
        // 긴 설정 화면에서 원하는 섹션까지 모든 요소를 하나씩 지나가야 한다.
        setLargeFontContent {
            NotificationSettingsContent(
                modifier = Modifier,
                state = NotificationSettingsUiState(
                    mode = NotificationMode.Basic,
                    pushEnabled = true,
                    canUsePush = true,
                    busy = false,
                    busyAction = null,
                    testPushBusy = false,
                    expiryTestPushBusy = false,
                    joinedTestRoom = false,
                    testRoomBusy = false,
                    testRoomMessage = null,
                    message = null
                ),
                notificationPermission = NotificationPermissionState(true, true, {}),
                actions = NotificationSettingsActions({}, {}, {}, {}, {}, {}, {})
            )
        }

        listOf("기본 만료 알림", "푸시 연결 확인").forEach { title ->
            composeRule.onNodeWithText(title)
                .performScrollTo()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    @Test
    fun listItemTitlesAreNotExposedAsHeadings() {
        // 목록 항목 제목까지 heading 이면 항목 100개에서 heading 도 100개가 되어
        // 제목 단위 탐색이 무의미해진다. 의도적으로 표식하지 않는다.
        val member = RoomMember(
            uid = "member-heading",
            role = "member",
            displayName = "목록 항목 이름",
            notificationEnabled = true,
            notificationMode = null,
            notificationDays = null
        )
        setLargeFontContent {
            MemberRow(member = member, canRemove = false, onRemove = {})
        }

        composeRule.onNodeWithText(member.displayName)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading))
    }
}
