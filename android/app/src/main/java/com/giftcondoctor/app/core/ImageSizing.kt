package com.giftcondoctor.app.core

import kotlin.math.min
import kotlin.math.sqrt

data class ImageDimensions(val width: Int, val height: Int)

fun fitImageDimensions(
    sourceWidth: Int,
    sourceHeight: Int,
    maxWidth: Int,
    maxHeight: Int,
    maxPixels: Long
): ImageDimensions {
    if (sourceWidth <= 0 || sourceHeight <= 0 || maxWidth <= 0 || maxHeight <= 0 || maxPixels <= 0) {
        return ImageDimensions(1, 1)
    }
    val sourcePixels = sourceWidth.toLong() * sourceHeight.toLong()
    val dimensionScale = min(maxWidth.toDouble() / sourceWidth, maxHeight.toDouble() / sourceHeight)
    val pixelScale = sqrt(maxPixels.toDouble() / sourcePixels)
    val scale = min(1.0, min(dimensionScale, pixelScale))
    return ImageDimensions(
        width = (sourceWidth * scale).toInt().coerceAtLeast(1),
        height = (sourceHeight * scale).toInt().coerceAtLeast(1)
    )
}
