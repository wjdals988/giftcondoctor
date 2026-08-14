package com.giftcondoctor.app.core

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

internal fun clampZoomOffset(offset: Offset, scale: Float, viewportSize: IntSize): Offset {
    if (scale <= 1f || viewportSize == IntSize.Zero) return Offset.Zero
    val maxOffsetX = viewportSize.width / 2f * (scale - 1f)
    val maxOffsetY = viewportSize.height / 2f * (scale - 1f)
    return Offset(
        x = offset.x.coerceIn(-maxOffsetX, maxOffsetX),
        y = offset.y.coerceIn(-maxOffsetY, maxOffsetY)
    )
}

internal fun zoomOffsetForDoubleTap(tap: Offset, targetScale: Float, viewportSize: IntSize): Offset {
    if (targetScale <= 1f || viewportSize == IntSize.Zero) return Offset.Zero
    val centeredOffset = Offset(
        x = (viewportSize.width / 2f - tap.x) * max(0f, targetScale - 1f),
        y = (viewportSize.height / 2f - tap.y) * max(0f, targetScale - 1f)
    )
    return clampZoomOffset(centeredOffset, targetScale, viewportSize)
}
