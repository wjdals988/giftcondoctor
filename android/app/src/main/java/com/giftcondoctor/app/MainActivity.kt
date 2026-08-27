package com.giftcondoctor.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.giftcondoctor.app.core.SharedImageImportState
import com.giftcondoctor.app.core.acceptsSharedImageIntent
import com.giftcondoctor.app.core.trustedAppDeepLink
import com.giftcondoctor.app.data.CouponImageFileStore
import com.giftcondoctor.app.data.CouponUploadOptimizer
import com.giftcondoctor.app.data.SharedImageImportStore
import com.giftcondoctor.app.notifications.NotificationChannels
import com.giftcondoctor.app.ui.GiftcondoctorApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<Uri?>(null)
    private val sharedImageImport = mutableStateOf<SharedImageImportState>(SharedImageImportState.None)
    private var sharedImageImportJob: Job? = null
    private var sharedImageRequestId = 0L
    private var activeSharedSourceUri: Uri? = null
    private var activeSharedDeclaredType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CouponImageFileStore.purgeAbandonedOnce(applicationContext)
        CouponUploadOptimizer.purgeAbandonedOnce(applicationContext)
        SharedImageImportStore.purgeAbandonedOnce(applicationContext)
        NotificationChannels.create(this)
        if (savedInstanceState == null) {
            handleIntent(intent)
        } else {
            pendingDeepLink.value = savedInstanceState.getString(PENDING_DEEP_LINK_KEY)
                ?.let(Uri::parse)
                ?: extractDeepLink(intent)
            restoreSharedImageImport(savedInstanceState)
        }
        setContent {
            GiftcondoctorApp(
                deepLink = pendingDeepLink.value,
                onDeepLinkHandled = { pendingDeepLink.value = null },
                sharedImageImport = sharedImageImport.value,
                onSharedImageHandled = { sharedImageImport.value = SharedImageImportState.None },
                onSharedImageDismissed = ::dismissSharedImageImport
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PENDING_DEEP_LINK_KEY, pendingDeepLink.value?.toString())
        when (val state = sharedImageImport.value) {
            SharedImageImportState.None -> Unit
            SharedImageImportState.Copying -> {
                outState.putString(SHARED_SOURCE_URI_KEY, activeSharedSourceUri?.toString())
                outState.putString(SHARED_SOURCE_TYPE_KEY, activeSharedDeclaredType)
            }
            is SharedImageImportState.Ready -> outState.putString(SHARED_READY_URI_KEY, state.uri.toString())
            is SharedImageImportState.Error -> outState.putString(SHARED_ERROR_KEY, state.message)
        }
    }

    private fun handleIntent(intent: Intent?) {
        pendingDeepLink.value = extractDeepLink(intent)
        if (intent?.action != Intent.ACTION_SEND) return

        val sourceUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (!acceptsSharedImageIntent(intent.action, intent.type, sourceUri?.scheme)) {
            sharedImageImport.value = SharedImageImportState.Error("공유한 항목을 이미지로 확인하지 못했습니다.")
            return
        }

        startSharedImageImport(sourceUri ?: return, intent.type)
    }

    private fun startSharedImageImport(sourceUri: Uri, declaredType: String? = null) {
        val requestId = ++sharedImageRequestId
        sharedImageImportJob?.cancel()
        val previousUri = (sharedImageImport.value as? SharedImageImportState.Ready)?.uri
        SharedImageImportStore.delete(applicationContext, previousUri)
        activeSharedSourceUri = sourceUri
        activeSharedDeclaredType = declaredType
        sharedImageImport.value = SharedImageImportState.Copying
        sharedImageImportJob = lifecycleScope.launch {
            try {
                val importedUri = SharedImageImportStore.import(applicationContext, sourceUri, declaredType)
                completeSharedImageImport(requestId, importedUri)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failSharedImageImport(requestId, error)
            }
        }
    }

    private fun completeSharedImageImport(requestId: Long, importedUri: Uri) {
        if (requestId != sharedImageRequestId) {
            SharedImageImportStore.delete(applicationContext, importedUri)
            return
        }
        activeSharedSourceUri = null
        activeSharedDeclaredType = null
        sharedImageImport.value = SharedImageImportState.Ready(importedUri)
    }

    private fun failSharedImageImport(requestId: Long, error: Exception) {
        if (requestId != sharedImageRequestId) return
        activeSharedSourceUri = null
        activeSharedDeclaredType = null
        Log.w(TAG, "Shared image import failed", error)
        sharedImageImport.value = SharedImageImportState.Error(
            error.localizedMessage ?: "공유 이미지를 준비하지 못했습니다."
        )
    }

    private fun restoreSharedImageImport(savedInstanceState: Bundle) {
        val readyUri = SharedImageImportStore.restoreOwned(
            applicationContext,
            savedInstanceState.getString(SHARED_READY_URI_KEY)
        )
        if (readyUri != null) {
            sharedImageImport.value = SharedImageImportState.Ready(readyUri)
            return
        }

        savedInstanceState.getString(SHARED_SOURCE_URI_KEY)?.let(Uri::parse)?.let { sourceUri ->
            startSharedImageImport(sourceUri, savedInstanceState.getString(SHARED_SOURCE_TYPE_KEY))
        }
            ?: savedInstanceState.getString(SHARED_ERROR_KEY)?.let { message ->
                sharedImageImport.value = SharedImageImportState.Error(message)
            }
    }

    private fun dismissSharedImageImport() {
        sharedImageRequestId += 1
        sharedImageImportJob?.cancel()
        sharedImageImportJob = null
        activeSharedSourceUri = null
        activeSharedDeclaredType = null
        val readyUri = (sharedImageImport.value as? SharedImageImportState.Ready)?.uri
        SharedImageImportStore.delete(applicationContext, readyUri)
        sharedImageImport.value = SharedImageImportState.None
    }

    private fun extractDeepLink(intent: Intent?): Uri? = trustedAppDeepLink(
        intent?.data?.toString(),
        intent?.getStringExtra("deepLink")
    )?.let(Uri::parse)

    private companion object {
        const val SHARED_SOURCE_URI_KEY = "shared_source_uri"
        const val SHARED_READY_URI_KEY = "shared_ready_uri"
        const val SHARED_ERROR_KEY = "shared_error"
        const val SHARED_SOURCE_TYPE_KEY = "shared_source_type"
        const val PENDING_DEEP_LINK_KEY = "pending_deep_link"
        const val TAG = "GiftcondoctorShare"
    }
}
