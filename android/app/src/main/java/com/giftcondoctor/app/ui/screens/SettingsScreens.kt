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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.BuildConfig
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.ui.components.AppVersionText
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.components.ReminderTimeBanner
import com.giftcondoctor.app.ui.components.rememberNotificationPermissionState
import com.giftcondoctor.app.ui.viewmodel.SettingsViewModel

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    onOpenAppInfo: () -> Unit,
    viewModel: SettingsViewModel = viewModel(key = "notification-settings")
) {
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val testPushBusy by viewModel.testPushBusy.collectAsStateWithLifecycle()
    val expiryTestPushBusy by viewModel.expiryTestPushBusy.collectAsStateWithLifecycle()
    val savedMode by viewModel.defaultMode.collectAsStateWithLifecycle()
    val savedPushEnabled by viewModel.defaultPushEnabled.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(NotificationMode.Basic) }
    var pushEnabled by remember { mutableStateOf(true) }
    val notificationPermission = rememberNotificationPermissionState()
    val canUsePush = notificationPermission.granted || !notificationPermission.runtimeRequired

    LaunchedEffect(Unit) {
        viewModel.loadDefaultSettings()
    }

    LaunchedEffect(savedMode, savedPushEnabled) {
        mode = savedMode
        pushEnabled = savedPushEnabled
    }

    GDScaffold(title = "알림 설정", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("기본 만료 알림", style = MaterialTheme.typography.titleMedium)
            ReminderTimeBanner()
            Text(
                "최소: 3일 전/당일 · 기본: 7일 전/3일 전/1일 전/당일 · 꼼꼼: 7/5/3/2/1일 전/당일",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ModeChips(selected = mode, onSelected = { mode = it })
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("푸시 알림 사용")
                Switch(
                    checked = pushEnabled && canUsePush,
                    enabled = canUsePush,
                    onCheckedChange = { pushEnabled = it }
                )
            }
            NotificationPermissionStatus(notificationPermission)
            Button(
                onClick = { viewModel.updateDefault(mode, pushEnabled && canUsePush) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                val saving = busyAction == "default"
                if (saving) ButtonProgressIndicator()
                Text(if (saving) "저장 중..." else "저장")
            }
            OutlinedButton(
                onClick = { viewModel.sendTestPush() },
                enabled = canUsePush && !testPushBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                if (testPushBusy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(if (testPushBusy) "단말 테스트 보내는 중..." else "단말 푸시 알림 설정 테스트")
            }
            OutlinedButton(
                onClick = { viewModel.sendExpiryReminderTestPush() },
                enabled = canUsePush && pushEnabled && !expiryTestPushBusy,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                if (expiryTestPushBusy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(if (expiryTestPushBusy) "10초 뒤 만료 알림 테스트 중..." else "만료 알림 10초 테스트")
            }
            Text(
                "두 번째 테스트는 실제 만료 알림과 같은 형식으로 서버가 10초 뒤 푸시를 보냅니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            InlineMessage(message)
            HorizontalDivider()
            Card(
                onClick = onOpenAppInfo,
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = { Text("앱 정보") },
                    supportingContent = { AppVersionText() },
                    trailingContent = {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                )
            }
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
                "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            Text("변경사항", style = MaterialTheme.typography.titleMedium)
            ChangeLogEntry(
                version = "0.1.13",
                changes = listOf(
                    "쿠폰 상세 화면에 댓글 작성과 삭제 기능을 추가했습니다.",
                    "방 상세와 방 설정에서 방장/멤버 표시를 더 명확하게 정리했습니다.",
                    "방장 전용 방 삭제 플로우를 추가하고 쿠폰, 댓글, 이미지까지 함께 정리되도록 했습니다."
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
