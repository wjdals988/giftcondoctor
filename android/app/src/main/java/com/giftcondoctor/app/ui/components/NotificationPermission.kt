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
import androidx.compose.runtime.LaunchedEffect
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

data class NotificationPermissionState(
    val runtimeRequired: Boolean,
    val granted: Boolean,
    val request: () -> Unit
)

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }

    val refresh = {
        granted = isNotificationPermissionGranted(context)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
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
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                refresh()
            }
        }
    )
}

@Composable
fun RequestNotificationPermissionOnLaunch() {
    val permission = rememberNotificationPermissionState()
    var requested by remember { mutableStateOf(false) }

    LaunchedEffect(permission.runtimeRequired, permission.granted) {
        if (!requested && permission.runtimeRequired && !permission.granted) {
            requested = true
            permission.request()
        }
    }
}

@Composable
fun NotificationPermissionStatus(
    permission: NotificationPermissionState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Android 알림 권한")
                Text(
                    when {
                        !permission.runtimeRequired -> "권한 필요 없음"
                        permission.granted -> "허용됨"
                        else -> "꺼져 있음"
                    },
                    color = if (permission.granted || !permission.runtimeRequired) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            if (permission.runtimeRequired && !permission.granted) {
                Text(
                    "푸시 알림을 받으려면 Android 알림 권한을 허용해야 합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = permission.request, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.small) {
                    Text("알림 권한 허용")
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
