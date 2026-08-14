package com.giftcondoctor.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.giftcondoctor.app.core.AppConstants

object NotificationChannels {
    fun create(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            AppConstants.EXPIRY_CHANNEL_ID,
            "쿠폰 만료 알림",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "매일 오전 9시, 만료 예정 쿠폰을 알려드립니다."
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        }
        manager.createNotificationChannel(channel)
    }
}
