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
import com.giftcondoctor.app.core.AppConstants
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
import com.giftcondoctor.app.data.HttpCache

class MainActivity : ComponentActivity() {
    private val pendingDeepLink = mutableStateOf<Uri?>(null)
    private val sharedImageImport = mutableStateOf<SharedImageImportState>(SharedImageImportState.None)
    private var sharedImageImportJob: Job? = null
    private var sharedImageRequestId = 0L
    private var activeSharedSourceUris: List<Uri> = emptyList()
    private var activeSharedDeclaredType: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CouponImageFileStore.purgeAbandonedOnce(applicationContext)
        CouponUploadOptimizer.purgeAbandonedOnce(applicationContext)
        SharedImageImportStore.purgeAbandonedOnce(applicationContext)
        NotificationChannels.create(this)
        // 서버가 썸네일에 max-age=3600 을 보내지만 OkHttp 는 Cache 가 있어야 그 헤더를
        // 쓴다. 첫 네트워크 요청 전에 준비해야 한다.
        HttpCache.install(this)
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
            is SharedImageImportState.Copying -> {
                outState.putStringArrayList(
                    SHARED_SOURCE_URIS_KEY,
                    ArrayList(activeSharedSourceUris.map(Uri::toString))
                )
                outState.putString(SHARED_SOURCE_TYPE_KEY, activeSharedDeclaredType)
            }
            is SharedImageImportState.Ready -> outState.putStringArrayList(
                SHARED_READY_URIS_KEY,
                ArrayList(state.uris.map(Uri::toString))
            )
            is SharedImageImportState.Error -> outState.putString(SHARED_ERROR_KEY, state.message)
        }
    }

    private fun handleIntent(intent: Intent?) {
        pendingDeepLink.value = extractDeepLink(intent)
        if (intent?.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return
        val incomingIntent = intent ?: return

        val sourceUris = extractSharedImageUris(incomingIntent)
        if (!acceptsSharedImageIntent(incomingIntent.action, incomingIntent.type, sourceUris.map(Uri::getScheme))) {
            rejectSharedImageImport(sharedImageErrorMessage(incomingIntent, sourceUris))
            return
        }

        startSharedImageImport(sourceUris, incomingIntent.type)
    }

    private fun startSharedImageImport(sourceUris: List<Uri>, declaredType: String? = null) {
        val requestId = ++sharedImageRequestId
        sharedImageImportJob?.cancel()
        (sharedImageImport.value as? SharedImageImportState.Ready)?.uris.orEmpty()
            .forEach { SharedImageImportStore.delete(applicationContext, it) }
        activeSharedSourceUris = sourceUris
        activeSharedDeclaredType = declaredType
        sharedImageImport.value = SharedImageImportState.Copying(completed = 0, total = sourceUris.size)
        sharedImageImportJob = lifecycleScope.launch {
            try {
                val importedUris = SharedImageImportStore.importAll(
                    context = applicationContext,
                    sourceUris = sourceUris,
                    declaredType = declaredType,
                    onProgress = { completed, total ->
                        if (requestId == sharedImageRequestId) {
                            sharedImageImport.value = SharedImageImportState.Copying(completed, total)
                        }
                    }
                )
                completeSharedImageImport(requestId, importedUris)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                failSharedImageImport(requestId, error)
            }
        }
    }

    private fun completeSharedImageImport(requestId: Long, importedUris: List<Uri>) {
        if (requestId != sharedImageRequestId) {
            importedUris.forEach { SharedImageImportStore.delete(applicationContext, it) }
            return
        }
        activeSharedSourceUris = emptyList()
        activeSharedDeclaredType = null
        sharedImageImport.value = SharedImageImportState.Ready(importedUris)
    }

    private fun failSharedImageImport(requestId: Long, error: Exception) {
        if (requestId != sharedImageRequestId) return
        activeSharedSourceUris = emptyList()
        activeSharedDeclaredType = null
        Log.w(TAG, "Shared image import failed", error)
        sharedImageImport.value = SharedImageImportState.Error(
            error.localizedMessage ?: "공유 이미지를 준비하지 못했습니다."
        )
    }

    private fun restoreSharedImageImport(savedInstanceState: Bundle) {
        val readyValues = savedInstanceState.getStringArrayList(SHARED_READY_URIS_KEY)
        val readyUris = SharedImageImportStore.restoreOwned(applicationContext, readyValues)
        if (readyUris.isNotEmpty() && readyUris.size == readyValues?.size) {
            sharedImageImport.value = SharedImageImportState.Ready(readyUris)
            return
        }
        readyUris.forEach { SharedImageImportStore.delete(applicationContext, it) }

        savedInstanceState.getStringArrayList(SHARED_SOURCE_URIS_KEY)
            ?.map(Uri::parse)
            ?.takeIf(List<Uri>::isNotEmpty)
            ?.let { sourceUris ->
                startSharedImageImport(sourceUris, savedInstanceState.getString(SHARED_SOURCE_TYPE_KEY))
        }
            ?: savedInstanceState.getString(SHARED_ERROR_KEY)?.let { message ->
                sharedImageImport.value = SharedImageImportState.Error(message)
            }
    }

    private fun dismissSharedImageImport() {
        sharedImageRequestId += 1
        sharedImageImportJob?.cancel()
        sharedImageImportJob = null
        activeSharedSourceUris = emptyList()
        activeSharedDeclaredType = null
        (sharedImageImport.value as? SharedImageImportState.Ready)?.uris.orEmpty()
            .forEach { SharedImageImportStore.delete(applicationContext, it) }
        sharedImageImport.value = SharedImageImportState.None
    }

    private fun rejectSharedImageImport(message: String) {
        sharedImageRequestId += 1
        sharedImageImportJob?.cancel()
        sharedImageImportJob = null
        activeSharedSourceUris = emptyList()
        activeSharedDeclaredType = null
        (sharedImageImport.value as? SharedImageImportState.Ready)?.uris.orEmpty()
            .forEach { SharedImageImportStore.delete(applicationContext, it) }
        sharedImageImport.value = SharedImageImportState.Error(message)
    }

    private fun sharedImageErrorMessage(intent: Intent, sourceUris: List<Uri>): String = when {
        sourceUris.size > AppConstants.MAX_SHARED_IMAGE_COUNT ->
            "이미지는 한 번에 ${AppConstants.MAX_SHARED_IMAGE_COUNT}장까지 공유할 수 있습니다."
        sourceUris.isEmpty() -> "공유한 항목에서 이미지를 찾지 못했습니다."
        intent.type?.startsWith("image/", ignoreCase = true) != true ->
            "이미지 파일만 쿠폰으로 등록할 수 있습니다."
        else -> "공유한 이미지 주소를 안전하게 열 수 없습니다."
    }

    private fun extractSharedImageUris(intent: Intent): List<Uri> {
        val rawUris = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(
                intent,
                Intent.EXTRA_STREAM,
                Uri::class.java
            ).orEmpty()
            else -> emptyList()
        }
        return rawUris.distinctBy(Uri::toString)
    }

    private fun extractDeepLink(intent: Intent?): Uri? = trustedAppDeepLink(
        intent?.data?.toString(),
        intent?.getStringExtra("deepLink")
    )?.let(Uri::parse)

    private companion object {
        const val SHARED_SOURCE_URIS_KEY = "shared_source_uris"
        const val SHARED_READY_URIS_KEY = "shared_ready_uris"
        const val SHARED_ERROR_KEY = "shared_error"
        const val SHARED_SOURCE_TYPE_KEY = "shared_source_type"
        const val PENDING_DEEP_LINK_KEY = "pending_deep_link"
        const val TAG = "GiftcondoctorShare"
    }
}
