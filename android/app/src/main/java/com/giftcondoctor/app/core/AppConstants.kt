package com.giftcondoctor.app.core

object AppConstants {
    const val SEOUL_TIME_ZONE = "Asia/Seoul"
    const val EXPIRY_CHANNEL_ID = "coupon_expiry"

    /**
     * 혼자 쓰기 시작할 때 자동으로 붙는 방 이름.
     *
     * 이 앱은 쿠폰을 반드시 방 안에 저장한다. 그런데 첫 사용자의 필요는 대개
     * "혼자 저장하고 만료 알림 받기" 이고 공유는 나중에 생긴다. 방 이름을
     * 정하는 결정은 그 사용자에게 아무 가치가 없으면서 첫 관문이 된다.
     * 이 이름을 기본값으로 두어 결정을 없앤다.
     */
    const val PERSONAL_ROOM_NAME = "내 쿠폰"
    const val MAX_IMAGE_BYTES = 10 * 1024 * 1024
    const val MAX_IMAGE_PIXELS = 40_000_000L
    const val MAX_SHARED_IMAGE_COUNT = 10
    const val MAX_SHARED_IMAGE_TOTAL_BYTES = 50 * 1024 * 1024L
    // Vercel Functions reject request bodies above 4.5MB. Keep enough room for
    // multipart boundaries while still accepting source images up to 10MB.
    const val MAX_SERVER_UPLOAD_IMAGE_BYTES = 4 * 1024 * 1024
    const val PUSH_TEST_ROOM_ID = "push-test-room"
}
