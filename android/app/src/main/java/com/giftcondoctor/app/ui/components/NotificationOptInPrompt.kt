package com.giftcondoctor.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * 첫 쿠폰을 저장한 직후에만 띄우는 알림 권한 제안.
 *
 * 권한을 언제 묻느냐가 수락률을 좌우한다. 앱을 처음 열자마자 물으면 사용자는 아직
 * 이 앱이 무엇을 해주는지 모르기 때문에 거절하기 쉽고, Android 는 두 번 거절하면
 * 다시 묻지 않으므로 그 거절은 사실상 되돌릴 수 없다.
 *
 * 쿠폰을 막 저장한 순간은 다르다. 사용자가 방금 "이걸 잊지 않고 쓰고 싶다" 는 행동을
 * 했고, 화면에 그 쿠폰의 만료일이 떠 있다. 이때의 제안은 앱의 요구가 아니라 방금 한
 * 행동의 자연스러운 다음 단계로 읽힌다.
 *
 * 거절해도 방 목록의 경고 카드가 남아 있으므로 나중에 다시 켤 수 있다.
 */
@Composable
fun NotificationOptInPrompt(
    expiryLabel: String,
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.NotificationsActive, contentDescription = null)
        },
        title = { Text("만료 전에 알려드릴까요?") },
        text = {
            Text(
                "이 쿠폰은 $expiryLabel 만료돼요. 알림을 켜두면 만료 전에 미리 알려드릴게요. " +
                    "매일 오전 9시에 만료 예정 쿠폰만 간결하게 보냅니다."
            )
        },
        confirmButton = {
            TextButton(
                onClick = onAllow,
                modifier = Modifier.testTag("optin-allow-notifications")
            ) {
                Text("알림 켜기")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("optin-skip-notifications")
            ) {
                Text("나중에")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
