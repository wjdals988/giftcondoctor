package com.giftcondoctor.app.core

import android.net.Uri

sealed interface SharedImageImportState {
    data object None : SharedImageImportState
    data object Copying : SharedImageImportState
    data class Ready(val uri: Uri) : SharedImageImportState
    data class Error(val message: String) : SharedImageImportState
}

internal fun acceptsSharedImageIntent(action: String?, declaredType: String?, uriScheme: String?): Boolean =
    action == "android.intent.action.SEND" &&
        declaredType?.lowercase()?.startsWith("image/") == true &&
        uriScheme?.lowercase() == "content"
