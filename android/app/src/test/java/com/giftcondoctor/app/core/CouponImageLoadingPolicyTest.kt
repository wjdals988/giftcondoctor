package com.giftcondoctor.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponImageLoadingPolicyTest {
    @Test
    fun defersOriginalWhenThumbnailCanRenderDetail() {
        assertFalse(shouldLoadOriginalImage(hasThumbnail = true))
    }

    @Test
    fun loadsOriginalImmediatelyForLegacyCouponWithoutThumbnail() {
        assertTrue(shouldLoadOriginalImage(hasThumbnail = false))
    }

    @Test
    fun loadsOriginalWhenUserRequestsExpansion() {
        assertTrue(shouldLoadOriginalImage(hasThumbnail = true, expansionRequested = true))
    }

    @Test
    fun loadsOriginalWhenThumbnailFails() {
        assertTrue(shouldLoadOriginalImage(hasThumbnail = true, thumbnailFailed = true))
    }
}
