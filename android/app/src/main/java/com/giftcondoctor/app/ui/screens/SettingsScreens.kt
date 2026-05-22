package com.giftcondoctor.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
            AppVersionText(modifier = Modifier.padding(top = 8.dp))
        }
    }
}
