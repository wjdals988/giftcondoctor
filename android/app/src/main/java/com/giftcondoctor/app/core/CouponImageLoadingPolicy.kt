package com.giftcondoctor.app.core

fun shouldLoadOriginalImage(
    hasThumbnail: Boolean,
    expansionRequested: Boolean = false,
    thumbnailFailed: Boolean = false
): Boolean = !hasThumbnail || expansionRequested || thumbnailFailed
