package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponUploadOptimizationPolicyTest {
    @Test
    fun `small upload keeps original`() {
        val plan = couponUploadOptimizationPlan(1_080, 2_400, 600_000)

        assertFalse(plan.shouldOptimize)
        assertEquals(1_080, plan.targetWidth)
        assertEquals(2_400, plan.targetHeight)
    }

    @Test
    fun `large payload is recompressed without upscaling`() {
        val plan = couponUploadOptimizationPlan(1_080, 2_400, 2_000_000)

        assertTrue(plan.shouldOptimize)
        assertEquals(1_080, plan.targetWidth)
        assertEquals(2_400, plan.targetHeight)
    }

    @Test
    fun `camera image is resized within upload edge`() {
        val plan = couponUploadOptimizationPlan(4_000, 3_000, 4_000_000)

        assertTrue(plan.shouldOptimize)
        assertEquals(2_560, plan.targetWidth)
        assertEquals(1_920, plan.targetHeight)
    }

    @Test
    fun `unsupported server format is transcoded even when small`() {
        val plan = couponUploadOptimizationPlan(1_080, 1_920, 500_000, requiresTranscode = true)

        assertTrue(plan.shouldOptimize)
        assertEquals(1_080, plan.targetWidth)
        assertEquals(1_920, plan.targetHeight)
    }

    @Test
    fun `optimized upload requires ten percent saving`() {
        assertFalse(shouldUseOptimizedCouponUpload(2_000_000, 1_900_001))
        assertTrue(shouldUseOptimizedCouponUpload(2_000_000, 1_800_000))
    }

    @Test
    fun `unknown source length accepts bounded optimized output`() {
        assertTrue(shouldUseOptimizedCouponUpload(null, 900_000))
        assertFalse(shouldUseOptimizedCouponUpload(null, AppConstants.MAX_IMAGE_BYTES.toLong() + 1L))
    }

    @Test
    fun `required transcode accepts bounded output without saving`() {
        assertTrue(shouldUseOptimizedCouponUpload(500_000, 550_000, requiresTranscode = true))
    }
}
