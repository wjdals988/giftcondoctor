package com.giftcondoctor.app.core

import android.net.Uri

sealed interface SharedImageImportState {
    data object None : SharedImageImportState
    data class Copying(val completed: Int, val total: Int) : SharedImageImportState
    data class Ready(val uris: List<Uri>) : SharedImageImportState {
        init {
            require(uris.isNotEmpty()) { "ready import must contain at least one image" }
        }
    }
    data class Error(val message: String) : SharedImageImportState
}

internal fun acceptsSharedImageIntent(
    action: String?,
    declaredType: String?,
    uriSchemes: List<String?>
): Boolean =
    action in setOf("android.intent.action.SEND", "android.intent.action.SEND_MULTIPLE") &&
        declaredType?.lowercase()?.startsWith("image/") == true &&
        uriSchemes.size in 1..AppConstants.MAX_SHARED_IMAGE_COUNT &&
        uriSchemes.all { it?.lowercase() == "content" }
