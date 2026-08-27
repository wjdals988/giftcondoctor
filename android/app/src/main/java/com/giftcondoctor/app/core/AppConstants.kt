package com.giftcondoctor.app.core

object AppConstants {
    const val SEOUL_TIME_ZONE = "Asia/Seoul"
    const val EXPIRY_CHANNEL_ID = "coupon_expiry"
    const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    const val MAX_IMAGE_PIXELS = 40_000_000L
    const val MAX_SHARED_IMAGE_COUNT = 10
    const val MAX_SHARED_IMAGE_TOTAL_BYTES = 50 * 1024 * 1024L
    // Vercel Functions reject request bodies above 4.5MB. Keep enough room for
    // multipart boundaries while still accepting source images up to 10MB.
    const val MAX_SERVER_UPLOAD_IMAGE_BYTES = 4 * 1024 * 1024
    const val PUSH_TEST_ROOM_ID = "push-test-room"
}
