package com.giftcondoctor.app.core

fun bitmapSampleSize(
    sourceWidth: Int,
    sourceHeight: Int,
    targetWidth: Int,
    targetHeight: Int
): Int {
    if (sourceWidth <= 0 || sourceHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) return 1

    val targetPixels = targetWidth.toLong() * targetHeight.toLong()
    var sampleSize = 1
    while (true) {
        val nextSample = sampleSize * 2
        val sampledPixels =
            (sourceWidth / nextSample).coerceAtLeast(1).toLong() *
                (sourceHeight / nextSample).coerceAtLeast(1).toLong()
        if (sampledPixels < targetPixels) return sampleSize
        sampleSize = nextSample
    }
}
