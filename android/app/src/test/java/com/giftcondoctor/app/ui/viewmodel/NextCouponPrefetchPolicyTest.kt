package com.giftcondoctor.app.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NextCouponPrefetchPolicyTest {
    @Test
    fun `starts only for a distinct next image while current work is idle`() {
        val idle = NextCouponPrefetchState()

        assertTrue(
            shouldStartNextCouponPrefetch(
                currentSource = "content://coupon/1",
                requestedSource = "content://coupon/2",
                currentImageProcessing = false,
                registrationBusy = false,
                existingState = idle
            )
        )
        assertFalse(
            shouldStartNextCouponPrefetch(
                currentSource = null,
                requestedSource = "content://coupon/2",
                currentImageProcessing = false,
                registrationBusy = false,
                existingState = idle
            )
        )
        assertFalse(
            shouldStartNextCouponPrefetch(
                currentSource = "content://coupon/1",
                requestedSource = "content://coupon/1",
                currentImageProcessing = false,
                registrationBusy = false,
                existingState = idle
            )
        )
        assertFalse(
            shouldStartNextCouponPrefetch(
                currentSource = "content://coupon/1",
                requestedSource = "content://coupon/2",
                currentImageProcessing = true,
                registrationBusy = false,
                existingState = idle
            )
        )
        assertFalse(
            shouldStartNextCouponPrefetch(
                currentSource = "content://coupon/1",
                requestedSource = "content://coupon/2",
                currentImageProcessing = false,
                registrationBusy = true,
                existingState = idle
            )
        )
    }

    @Test
    fun `does not repeat processing or a completed one-slot prefetch`() {
        listOf(NextCouponPrefetchStage.Processing, NextCouponPrefetchStage.Ready).forEach { stage ->
            assertFalse(
                shouldStartNextCouponPrefetch(
                    currentSource = "content://coupon/1",
                    requestedSource = "content://coupon/2",
                    currentImageProcessing = false,
                    registrationBusy = false,
                    existingState = NextCouponPrefetchState(
                        source = "content://coupon/2",
                        stage = stage
                    )
                )
            )
        }
    }

    @Test
    fun `status text distinguishes processing and partial readiness`() {
        assertEquals(
            "다음 쿠폰을 미리 읽는 중",
            nextCouponPrefetchStatusText(
                NextCouponPrefetchState("next", NextCouponPrefetchStage.Processing)
            )
        )
        assertEquals(
            "다음 쿠폰 자동 입력·빠른 업로드 준비 완료",
            nextCouponPrefetchStatusText(
                NextCouponPrefetchState(
                    "next",
                    NextCouponPrefetchStage.Ready,
                    analysisReady = true,
                    uploadReady = true
                )
            )
        )
        assertEquals(
            "다음 쿠폰 자동 입력 준비 완료",
            nextCouponPrefetchStatusText(
                NextCouponPrefetchState(
                    "next",
                    NextCouponPrefetchStage.Ready,
                    analysisReady = true
                )
            )
        )
        assertEquals(
            "다음 쿠폰 빠른 업로드 준비 완료",
            nextCouponPrefetchStatusText(
                NextCouponPrefetchState(
                    "next",
                    NextCouponPrefetchStage.Ready,
                    uploadReady = true
                )
            )
        )
        assertNull(nextCouponPrefetchStatusText(NextCouponPrefetchState()))
    }
}
