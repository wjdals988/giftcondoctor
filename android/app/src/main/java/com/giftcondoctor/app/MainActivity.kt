package com.giftcondoctor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.giftcondoctor.app.core.trustedAppDeepLink
import com.giftcondoctor.app.data.CouponImageFileStore
import com.giftcondoctor.app.data.CouponUploadOptimizer
import com.giftcondoctor.app.notifications.NotificationChannels
import com.giftcondoctor.app.ui.GiftcondoctorApp

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CouponImageFileStore.purgeAbandonedOnce(applicationContext)
        CouponUploadOptimizer.purgeAbandonedOnce(applicationContext)
        NotificationChannels.create(this)
        pendingDeepLink.value = extractDeepLink(intent)
        setContent {
            GiftcondoctorApp(
                deepLink = pendingDeepLink.value,
                onDeepLinkHandled = { pendingDeepLink.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDeepLink.value = extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?): Uri? = trustedAppDeepLink(
        intent?.data?.toString(),
        intent?.getStringExtra("deepLink")
    )?.let(Uri::parse)
}
