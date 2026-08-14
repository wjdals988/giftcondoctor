package com.giftcondoctor.app.core

import java.net.URI

private const val APP_DEEP_LINK_SCHEME = "giftcondoctor"
private const val ROOMS_DEEP_LINK_HOST = "rooms"

fun trustedAppDeepLink(vararg candidates: String?): String? = candidates.firstNotNullOfOrNull { candidate ->
    candidate?.takeIf(::isTrustedAppDeepLink)
}

private fun isTrustedAppDeepLink(candidate: String): Boolean {
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return false
    if (uri.scheme != APP_DEEP_LINK_SCHEME || uri.host != ROOMS_DEEP_LINK_HOST) return false

    val segments = uri.path.orEmpty().split('/').filter(String::isNotBlank)
    return segments.size == 1 ||
        (segments.size == 3 && segments[1] == "coupons")
}
