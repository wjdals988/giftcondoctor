package com.giftcondoctor.app.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.Icons
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.core.app.ActivityCompat
import android.provider.Settings
import android.net.Uri
import android.content.Intent

data class NotificationPermissionState(
    val runtimeRequired: Boolean,
    val granted: Boolean,
    val request: () -> Unit,
    /**
     * 시스템이 더 이상 권한 대화상자를 띄우지 않는 상태.
     *
     * Android 는 사용자가 두 번 거절하면 이후 요청을 조용히 무시한다. 이때 "허용하기"
     * 버튼을 계속 보여주면 눌러도 아무 일이 일어나지 않아 사용자가 앱이 고장났다고
     * 느낀다. 이 경우에는 설정 앱으로 안내해야 한다.
     */
    val permanentlyDenied: Boolean = false,
    /** 앱의 알림 설정 화면을 연다. */
    val openSystemSettings: () -> Unit = {}
)

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }
    // 한 번이라도 요청했는지 기억한다. shouldShowRequestPermissionRationale 은 최초
    // 요청 전에도 false 를 반환하므로, 요청 이력 없이는 "아직 안 물어봄" 과
    // "영구 거절" 을 구분할 수 없다.
    var requested by rememberSaveable { mutableStateOf(hasRequestedBefore(context)) }
    var rationaleVisible by remember { mutableStateOf(shouldShowRationale(context)) }

    val refresh = {
        granted = isNotificationPermissionGranted(context)
        rationaleVisible = shouldShowRationale(context)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        rationaleVisible = shouldShowRationale(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return NotificationPermissionState(
        runtimeRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        granted = granted,
        request = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                markRequested(context)
                requested = true
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                refresh()
            }
        },
        // 요청한 적이 있는데 시스템이 근거 설명도 요구하지 않으면 영구 거절이다.
        permanentlyDenied = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !granted && requested && !rationaleVisible,
        openSystemSettings = { openAppNotificationSettings(context) }
    )
}

private const val PERMISSION_PREFS = "notification_permission"
private const val KEY_REQUESTED = "post_notifications_requested"

private fun hasRequestedBefore(context: Context): Boolean =
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_REQUESTED, false)

private fun markRequested(context: Context) {
    context.getSharedPreferences(PERMISSION_PREFS, Context.MODE_PRIVATE)
        .edit().putBoolean(KEY_REQUESTED, true).apply()
}

private fun shouldShowRationale(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.POST_NOTIFICATIONS
    )
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        // 일부 기기는 알림 설정 화면을 직접 열 수 없다. 앱 상세 설정으로 대체한다.
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(fallback) }
    }
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * 알림 권한 상태 카드.
 *
 * 권한이 없으면 errorContainer 로 올린다. 이 앱은 만료 알림이 존재 이유이고,
 * 권한이 없으면 핵심 기능이 전혀 동작하지 않는다. 2026-08-28 실기기 확인에서
 * 푸시가 오지 않는다는 신고의 원인이 이 권한 하나였는데, 당시 이 카드는
 * surface 색이라 방 목록 카드들과 같은 무게로 읽혀 사용자가 지나쳤다.
 *
 * 권한이 있을 때는 조용히 있는다. 정상 상태를 강조할 이유가 없다.
 */
@Composable
fun NotificationPermissionStatus(
    permission: NotificationPermissionState,
    modifier: Modifier = Modifier
) {
    val needsAttention = permission.runtimeRequired && !permission.granted
    Card(
        modifier = modifier.fillMaxWidth().testTag("notification-permission-card"),
        colors = CardDefaults.cardColors(
            containerColor = if (needsAttention) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        val contentColor = if (needsAttention) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (needsAttention) {
                        Icon(
                            Icons.Default.NotificationsOff,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text("만료 푸시 알림", color = contentColor, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    when {
                        !permission.runtimeRequired -> "사용 가능"
                        permission.granted -> "켜짐"
                        else -> "꺼짐"
                    },
                    color = if (needsAttention) contentColor else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (needsAttention) {
                Text(
                    if (permission.permanentlyDenied) {
                        "알림이 차단되어 있어 만료 알림을 보낼 수 없어요. 설정에서 알림을 켜 주세요."
                    } else {
                        "지금은 쿠폰이 만료돼도 알려드릴 수 없어요. 알림을 켜면 만료 전에 알려드릴게요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor
                )
                // 영구 거절 상태에서 요청 버튼을 그대로 두면 눌러도 아무 일이
                // 일어나지 않는다. 시스템이 대화상자를 더 이상 띄우지 않기 때문이다.
                if (permission.permanentlyDenied) {
                    Button(
                        onClick = permission.openSystemSettings,
                        modifier = Modifier.fillMaxWidth().testTag("open-notification-settings"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("설정에서 알림 켜기")
                    }
                } else {
                    Button(
                        onClick = permission.request,
                        modifier = Modifier.fillMaxWidth().testTag("request-notification-permission"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("만료 알림 받기")
                    }
                }
            }
        }
    }
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
}
