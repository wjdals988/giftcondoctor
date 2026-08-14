package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSizingTest {
    @Test
    fun largeImageFitsViewportAndPixelBudget() {
        val result = fitImageDimensions(
            sourceWidth = 8_000,
            sourceHeight = 6_000,
            maxWidth = 2_160,
            maxHeight = 4_800,
            maxPixels = 8_000_000
        )

        assertEquals(2_160, result.width)
        assertEquals(1_620, result.height)
        assertTrue(result.width.toLong() * result.height <= 8_000_000)
    }

    @Test
    fun smallImageIsNotUpscaled() {
        assertEquals(
            ImageDimensions(640, 480),
            fitImageDimensions(640, 480, 2_160, 4_800, 8_000_000)
        )
    }

    @Test
    fun pixelBudgetLimitsVeryTallImage() {
        val result = fitImageDimensions(3_000, 12_000, 6_000, 24_000, 8_000_000)

        assertTrue(result.width.toLong() * result.height <= 8_000_000)
        assertEquals(4, result.height / result.width)
    }
}
