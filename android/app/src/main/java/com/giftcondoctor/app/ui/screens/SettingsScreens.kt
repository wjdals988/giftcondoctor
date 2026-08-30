package com.giftcondoctor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.BuildConfig
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.ui.components.AppVersionText
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.NotificationPermissionState
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.components.ReminderTimeBanner
import com.giftcondoctor.app.ui.components.rememberNotificationPermissionState
import com.giftcondoctor.app.ui.components.gdHeading
import com.giftcondoctor.app.ui.viewmodel.SettingsViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomListViewModel
import com.giftcondoctor.app.ui.components.appVersionLabel

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenAppInfo: () -> Unit,
    viewModel: SettingsViewModel = viewModel(key = "notification-settings"),
    roomListViewModel: RoomListViewModel = viewModel(key = "notification-push-test-room")
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val testPushBusy by viewModel.testPushBusy.collectAsStateWithLifecycle()
    val expiryTestPushBusy by viewModel.expiryTestPushBusy.collectAsStateWithLifecycle()
    val savedMode by viewModel.defaultMode.collectAsStateWithLifecycle()
    val savedPushEnabled by viewModel.defaultPushEnabled.collectAsStateWithLifecycle()
    val rooms by roomListViewModel.rooms.collectAsStateWithLifecycle()
    val testRoomBusy by roomListViewModel.busy.collectAsStateWithLifecycle()
    val testRoomMessage by roomListViewModel.message.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(NotificationMode.Basic) }
    var pushEnabled by remember { mutableStateOf(true) }
    val notificationPermission = rememberNotificationPermissionState()
    val canUsePush = notificationPermission.granted || !notificationPermission.runtimeRequired
    val joinedTestRoom = when (val state = rooms) {
        is UiState.Success -> state.data.any { it.roomId == AppConstants.PUSH_TEST_ROOM_ID }
        else -> false
    }

    LaunchedEffect(Unit) {
        viewModel.loadDefaultSettings()
    }

    LaunchedEffect(savedMode, savedPushEnabled) {
        mode = savedMode
        pushEnabled = savedPushEnabled
    }

    GDScaffold(title = "알림 설정", onBack = onBack) { modifier ->
        NotificationSettingsContent(
            modifier = modifier,
            state = NotificationSettingsUiState(
                mode = mode,
                pushEnabled = pushEnabled,
                canUsePush = canUsePush,
                busy = busy,
                busyAction = busyAction,
                testPushBusy = testPushBusy,
                expiryTestPushBusy = expiryTestPushBusy,
                joinedTestRoom = joinedTestRoom,
                testRoomBusy = testRoomBusy,
                testRoomMessage = testRoomMessage,
                message = message
            ),
            notificationPermission = notificationPermission,
            actions = NotificationSettingsActions(
                onModeSelected = { mode = it },
                onPushEnabledChanged = { pushEnabled = it },
                onSave = { viewModel.updateDefault(mode, pushEnabled && canUsePush) },
                onSendTestPush = viewModel::sendTestPush,
                onSendExpiryTestPush = viewModel::sendExpiryReminderTestPush,
                onJoinTestRoom = { roomListViewModel.joinPushTestRoom { } },
                onOpenAppInfo = onOpenAppInfo
            )
        )
    }
}

internal data class NotificationSettingsUiState(
    val mode: NotificationMode,
    val pushEnabled: Boolean,
    val canUsePush: Boolean,
    val busy: Boolean,
    val busyAction: String?,
    val testPushBusy: Boolean,
    val expiryTestPushBusy: Boolean,
    val joinedTestRoom: Boolean,
    val testRoomBusy: Boolean,
    val testRoomMessage: String?,
    val message: String?
)

internal data class NotificationSettingsActions(
    val onModeSelected: (NotificationMode) -> Unit,
    val onPushEnabledChanged: (Boolean) -> Unit,
    val onSave: () -> Unit,
    val onSendTestPush: () -> Unit,
    val onSendExpiryTestPush: () -> Unit,
    val onJoinTestRoom: () -> Unit,
    val onOpenAppInfo: () -> Unit
)

@Composable
internal fun NotificationSettingsContent(
    modifier: Modifier,
    state: NotificationSettingsUiState,
    notificationPermission: NotificationPermissionState,
    actions: NotificationSettingsActions
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NotificationDefaultsSection(state, notificationPermission, actions)
        PushDiagnosticsSection(state, actions)
        InlineMessage(state.testRoomMessage)
        InlineMessage(state.message)
        HorizontalDivider()
        AppInfoCard(actions.onOpenAppInfo)
    }
}

@Composable
private fun NotificationDefaultsSection(
    state: NotificationSettingsUiState,
    permission: NotificationPermissionState,
    actions: NotificationSettingsActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("기본 만료 알림", style = MaterialTheme.typography.titleMedium, modifier = Modifier.gdHeading())
        ReminderTimeBanner()
        Text(
            "최소: 3일 전/당일 · 기본: 7일 전/3일 전/1일 전/당일 · 꼼꼼: 7/5/3/2/1일 전/당일",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ModeChips(selected = state.mode, onSelected = actions.onModeSelected)
        PushEnabledControl(
            checked = state.pushEnabled && state.canUsePush,
            enabled = state.canUsePush,
            onCheckedChange = actions.onPushEnabledChanged
        )
        NotificationPermissionStatus(permission)
        Button(
            onClick = actions.onSave,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("notification-save"),
            shape = MaterialTheme.shapes.small
        ) {
            val saving = state.busyAction == "default"
            if (saving) ButtonProgressIndicator()
            Text(if (saving) "저장 중..." else "저장")
        }
    }
}

@Composable
private fun PushDiagnosticsSection(
    state: NotificationSettingsUiState,
    actions: NotificationSettingsActions
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("푸시 연결 확인", style = MaterialTheme.typography.titleMedium, modifier = Modifier.gdHeading())
        Text(
            "알림 권한 → FCM 기기 등록 → 서버 전송 순서로 확인합니다. 실패한 단계와 해결 방법을 바로 표시해요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = actions.onSendTestPush,
            enabled = state.canUsePush && !state.testPushBusy,
            modifier = Modifier.fillMaxWidth().testTag("push-diagnostic"),
            shape = MaterialTheme.shapes.small
        ) {
            if (state.testPushBusy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(if (state.testPushBusy) "3단계 확인 중..." else "푸시 연결 진단")
        }
        OutlinedButton(
            onClick = actions.onSendExpiryTestPush,
            enabled = state.canUsePush && state.pushEnabled && !state.expiryTestPushBusy,
            modifier = Modifier.fillMaxWidth().testTag("expiry-push-test"),
            shape = MaterialTheme.shapes.small
        ) {
            if (state.expiryTestPushBusy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(if (state.expiryTestPushBusy) "만료 알림 테스트 중..." else "만료 알림 형식 테스트")
        }
        Text(
            "두 번째 테스트는 실제 만료 알림과 같은 짧은 형식으로 즉시 전송합니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        PushTestRoomStatus(state, actions.onJoinTestRoom)
    }
}

@Composable
private fun PushTestRoomStatus(state: NotificationSettingsUiState, onJoin: () -> Unit) {
    if (state.joinedTestRoom) {
        Text(
            "매일 오전 9시 cron 푸시 테스트에 참여 중입니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    } else {
        OutlinedButton(
            onClick = onJoin,
            enabled = state.canUsePush && !state.testRoomBusy,
            modifier = Modifier.fillMaxWidth().testTag("join-push-test-room")
        ) {
            if (state.testRoomBusy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(if (state.testRoomBusy) "참여 중..." else "매일 오전 9시 cron 푸시도 확인")
        }
    }
}

@Composable
private fun AppInfoCard(onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().testTag("app-info"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            leadingContent = {
                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            headlineContent = { Text("앱 정보") },
            supportingContent = { AppVersionText() },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) }
        )
    }
}

@Composable
private fun PushEnabledControl(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val switchContent: @Composable () -> Unit = {
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag("push-enabled-switch")
                .semantics {
                    contentDescription = "푸시 알림 사용"
                    stateDescription = if (checked) "켜짐" else "꺼짐"
                }
        )
    }
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("푸시 알림 사용")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { switchContent() }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("푸시 알림 사용")
            switchContent()
        }
    }
}

@Composable
fun AppInfoScreen(onBack: () -> Unit) {
    GDScaffold(title = "앱 정보", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("기프티콘닥터", style = MaterialTheme.typography.headlineSmall)
            Text(
                appVersionLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Text("변경사항", style = MaterialTheme.typography.titleMedium, modifier = Modifier.gdHeading())
            ChangeLogEntry(
                version = "0.1.35",
                changes = listOf(
                    "화면 아래 탭으로 쿠폰방·검색·즐겨찾기를 오갈 수 있습니다.",
                    "즐겨찾기한 쿠폰을 방과 상관없이 한 화면에서 모아 봅니다.",
                    "네트워크가 없어 원본 이미지를 못 불러와도, 바코드는 그대로 쓸 수 있다고 알려드립니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.34",
                changes = listOf(
                    "모든 쿠폰방을 한 번에 검색할 수 있습니다. 방을 하나씩 열어 볼 필요가 없습니다.",
                    "자주 쓰는 쿠폰에 별표를 달면 목록 맨 위로 올라옵니다.",
                    "사용 완료를 잘못 눌렀을 때 5분 안에는 쿠폰 상세에서 되돌릴 수 있습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.33",
                changes = listOf(
                    "방 목록 맨 위에서 7일 안에 만료되는 쿠폰을 모아 보고, 탭하면 방을 거치지 않고 바로 열 수 있습니다.",
                    "방 이름을 정하지 않고 \"내 쿠폰 바로 시작\"으로 혼자 쓰기를 시작할 수 있습니다.",
                    "알림이 꺼져 있으면 눈에 띄게 알리고, 차단된 경우 설정 화면으로 바로 이동합니다.",
                    "첫 쿠폰을 저장한 직후에 만료 알림을 켤지 물어봅니다.",
                    "쿠폰 이름을 찾지 못했을 때 바코드 번호를 제목으로 쓰지 않습니다.",
                    "앱 버전을 방 목록 아래에서 바로 확인할 수 있습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.32",
                changes = listOf(
                    "시스템 다크 모드를 지원합니다. 계산대용 바코드 화면은 스캐너 인식을 위해 흰 배경을 유지합니다.",
                    "버튼과 입력 필드 색 대비를 WCAG AA 기준으로 교정하고 한글 본문 행간을 넓혔습니다.",
                    "만료 임박도를 색·아이콘·문구 3중으로 표시하고, 30일 넘게 남은 쿠폰에는 D-day를 붙이지 않습니다.",
                    "바코드 자동 감지와 사용 완료 시 짧은 진동으로 알려드립니다.",
                    "Google 로그인이 실패하면 원인을 화면에 표시합니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.20",
                changes = listOf(
                    "목록 이미지 요청을 화면 이탈 즉시 취소하고 같은 이미지는 한 번만 내려받도록 개선했습니다.",
                    "Baseline Profile과 100개 목록 Release/R8 성능 비교를 추가했습니다.",
                    "쿠폰 이미지를 미리 확인해 교체하고 확대 화면을 1배부터 4배까지 조절할 수 있습니다.",
                    "알림 권한은 로그인 전에 묻지 않고 첫 쿠폰방 이후 이유를 확인한 뒤 요청합니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.14",
                changes = listOf(
                    "쿠폰 이름과 브랜드 검색, 상태별 필터, 만료 임박순 정렬을 추가했습니다.",
                    "사용 완료와 쿠폰 삭제 전에 한 번 더 확인하도록 안전 절차를 추가했습니다.",
                    "댓글 입력과 수정 화면은 서버 저장이 성공한 뒤에만 비우거나 닫도록 개선했습니다.",
                    "쿠폰 등록과 수정에서 만료일을 달력으로 선택할 수 있습니다.",
                    "목록 검색·필터·정렬 경계 사례를 자동 테스트로 검증합니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.13",
                changes = listOf(
                    "쿠폰 상세 화면에 댓글 작성과 삭제 기능을 추가했습니다.",
                    "방 상세와 방 설정에서 방장/멤버 표시를 더 명확하게 정리했습니다.",
                    "방장 전용 방 삭제 플로우를 추가하고 쿠폰, 댓글, 이미지까지 함께 정리되도록 했습니다.",
                    "만료 푸시 문구를 짧게 정리하고 알림 형식 테스트를 즉시 전송하도록 개선했습니다.",
                    "앱 시작 시 만료 알림 채널을 준비해 첫 푸시 수신 안정성을 높였습니다.",
                    "멤버 수와 쿠폰 예약 상태의 동시 변경 정합성을 보강했습니다.",
                    "첫 쿠폰방과 쿠폰 등록 흐름을 단계형으로 단순화했습니다.",
                    "푸시 연결 실패 단계를 구분해 해결 방법을 안내합니다.",
                    "백그라운드 알림을 탭해도 대상 쿠폰으로 이동하도록 보강했습니다.",
                    "목록 이미지를 화면 크기에 맞게 최적화해 메모리 사용량을 줄였습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.12",
                changes = listOf(
                    "설정 하단에 앱 정보와 버전별 변경사항 화면을 추가했습니다.",
                    "쿠폰 상세 이미지 선택 시 크게 볼 수 있는 팝업뷰를 추가했습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.11",
                changes = listOf(
                    "알림 설정을 개인 전체 푸시 설정으로 통합했습니다.",
                    "단말 푸시 테스트와 만료 알림 10초 테스트를 분리했습니다.",
                    "APK 파일명에 버전명과 버전코드가 포함되도록 변경했습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.10",
                changes = listOf(
                    "실제 만료 알림 로그 처리 방식을 개선해 누락된 대상자에게 재발송할 수 있게 했습니다.",
                    "방 설정에서 전체 알림 설정으로 이동할 수 있게 정리했습니다."
                )
            )
            ChangeLogEntry(
                version = "0.1.8",
                changes = listOf(
                    "푸시 알림 테스트방을 추가했습니다.",
                    "매일 오전 9시 실제 cron 경로로 테스트 푸시를 받을 수 있게 했습니다."
                )
            )
        }
    }
}

@Composable
private fun ChangeLogEntry(version: String, changes: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(version, style = MaterialTheme.typography.titleSmall)
            changes.forEach { change ->
                Text(
                    "• $change",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
