package com.giftcondoctor.app.core

data class CouponUploadOptimizationPlan(
    val shouldOptimize: Boolean,
    val targetWidth: Int,
    val targetHeight: Int
)

private const val MAX_UPLOAD_EDGE = 2_560
private const val MAX_UPLOAD_PIXELS = 6_553_600L
private const val MIN_SOURCE_BYTES_TO_OPTIMIZE = 1_500_000L
private const val MIN_SAVING_PERCENT = 10

fun couponUploadOptimizationPlan(
    sourceWidth: Int,
    sourceHeight: Int,
    sourceBytes: Long?,
    requiresTranscode: Boolean = false
): CouponUploadOptimizationPlan {
    val target = fitImageDimensions(
        sourceWidth = sourceWidth,
        sourceHeight = sourceHeight,
        maxWidth = MAX_UPLOAD_EDGE,
        maxHeight = MAX_UPLOAD_EDGE,
        maxPixels = MAX_UPLOAD_PIXELS
    )
    val oversizedDimensions = sourceWidth > target.width || sourceHeight > target.height
    val oversizedPayload = sourceBytes?.let { it >= MIN_SOURCE_BYTES_TO_OPTIMIZE } ?: false
    return CouponUploadOptimizationPlan(
        shouldOptimize = sourceWidth > 0 && sourceHeight > 0 &&
            (oversizedDimensions || oversizedPayload || requiresTranscode),
        targetWidth = target.width,
        targetHeight = target.height
    )
}

fun shouldUseOptimizedCouponUpload(
    sourceBytes: Long?,
    optimizedBytes: Long,
    requiresTranscode: Boolean = false
): Boolean {
    if (optimizedBytes <= 0L || optimizedBytes > AppConstants.MAX_IMAGE_BYTES) return false
    if (requiresTranscode) return true
    if (sourceBytes == null || sourceBytes <= 0L) return true
    return optimizedBytes * 100L <= sourceBytes * (100L - MIN_SAVING_PERCENT)
}
