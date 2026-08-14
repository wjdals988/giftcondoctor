package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppDeepLinksTest {
    @Test
    fun `방과 쿠폰 딥링크를 허용한다`() {
        assertEquals(
            "giftcondoctor://rooms/room-1",
            trustedAppDeepLink("giftcondoctor://rooms/room-1")
        )
        assertEquals(
            "giftcondoctor://rooms/room-1/coupons/coupon-1",
            trustedAppDeepLink("giftcondoctor://rooms/room-1/coupons/coupon-1")
        )
    }

    @Test
    fun `FCM extra 딥링크를 fallback으로 사용한다`() {
        assertEquals(
            "giftcondoctor://rooms/room-1/coupons/coupon-1",
            trustedAppDeepLink(null, "giftcondoctor://rooms/room-1/coupons/coupon-1")
        )
    }

    @Test
    fun `잘못된 data 뒤의 유효한 extra를 사용한다`() {
        assertEquals(
            "giftcondoctor://rooms/room-1",
            trustedAppDeepLink("https://example.com/rooms/room-1", "giftcondoctor://rooms/room-1")
        )
    }

    @Test
    fun `외부 스킴과 알 수 없는 경로를 거부한다`() {
        assertNull(trustedAppDeepLink("https://example.com/rooms/room-1"))
        assertNull(trustedAppDeepLink("giftcondoctor://settings/notifications"))
        assertNull(trustedAppDeepLink("giftcondoctor://rooms/room-1/unknown/value"))
    }
}
