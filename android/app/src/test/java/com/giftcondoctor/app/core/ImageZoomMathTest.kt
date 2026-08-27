package com.giftcondoctor.app.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageZoomMathTest {
    @Test
    fun doubleTapKeepsTappedPointNearTheViewportCenter() {
        val offset = zoomOffsetForDoubleTap(
            tap = Offset(100f, 200f),
            targetScale = 2f,
            viewportSize = IntSize(1_000, 2_000)
        )

        assertEquals(Offset(400f, 800f), offset)
    }

    @Test
    fun panIsClampedToTheScaledViewportBounds() {
        val offset = clampZoomOffset(
            offset = Offset(9_999f, -9_999f),
            scale = 3f,
            viewportSize = IntSize(1_000, 2_000)
        )

        assertEquals(Offset(1_000f, -2_000f), offset)
    }

    @Test
    fun resetZoomAlwaysCentersTheImage() {
        assertEquals(
            Offset.Zero,
            clampZoomOffset(Offset(200f, 300f), 1f, IntSize(1_000, 2_000))
        )
    }

    @Test
    fun smallPinchKeepsDisplayResolutionWithoutPreparingLargeBitmap() {
        assertFalse(shouldPrepareHighResolutionZoom(1f))
        assertFalse(shouldPrepareHighResolutionZoom(1.14f))
    }

    @Test
    fun deliberateZoomPreparesHighResolutionBitmap() {
        assertTrue(shouldPrepareHighResolutionZoom(1.15f))
        assertTrue(shouldPrepareHighResolutionZoom(4f))
    }

    @Test
    fun earlyZoomSkipsRedundantDisplayResolutionDecode() {
        assertFalse(shouldDecodeDisplayResolution(zoomRequested = true, displayReady = false))
        assertFalse(shouldDecodeDisplayResolution(zoomRequested = true, displayReady = true))
    }

    @Test
    fun displayResolutionDecodesOnlyUntilItIsReady() {
        assertTrue(shouldDecodeDisplayResolution(zoomRequested = false, displayReady = false))
        assertFalse(shouldDecodeDisplayResolution(zoomRequested = false, displayReady = true))
    }
}
