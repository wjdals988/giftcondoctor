package com.giftcondoctor.app.data

import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 최근 쿠폰 바로가기가 실제 시스템에 등록되는지 검증한다.
 *
 * 순수 로직은 `RecentCouponsTest` 가 다룬다. 여기서는 그 목록이 실제
 * `ShortcutManagerCompat` 에 반영되는지, 그리고 삭제·로그아웃 시 사라지는지를 본다.
 *
 * 바로가기가 조용히 등록되지 않으면 화면에서는 아무 증상도 보이지 않는다. 사용자는
 * 그냥 "이 앱은 바로가기가 없네" 라고 생각하고 끝난다. 그래서 테스트로 고정한다.
 */
@RunWith(AndroidJUnit4::class)
class RecentCouponShortcutsInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clean() = RecentCouponShortcuts.clear(context)

    @After
    fun cleanup() = RecentCouponShortcuts.clear(context)

    @Test
    fun recordingACouponPublishesADynamicShortcut() {
        RecentCouponShortcuts.record(context, "room-1", "coupon-1", "아메리카노")

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context)
        assertEquals(1, shortcuts.size)
        assertEquals("coupon-room-1-coupon-1", shortcuts[0].id)
        assertEquals("아메리카노", shortcuts[0].shortLabel)
    }

    @Test
    fun shortcutIntentCarriesTheCouponDeepLink() {
        // 바로가기의 존재 이유는 방을 거치지 않고 쿠폰에 직행하는 것이다.
        RecentCouponShortcuts.record(context, "room-9", "coupon-9", "라떼")

        val intent = ShortcutManagerCompat.getDynamicShortcuts(context).first().intent
        assertEquals("giftcondoctor://rooms/room-9/coupons/coupon-9", intent.data?.toString())
    }

    @Test
    fun mostRecentCouponComesFirst() {
        RecentCouponShortcuts.record(context, "r", "a", "먼저")
        RecentCouponShortcuts.record(context, "r", "b", "나중")

        val ranked = ShortcutManagerCompat.getDynamicShortcuts(context).sortedBy { it.rank }
        assertEquals("나중", ranked.first().shortLabel)
    }

    @Test
    fun deletedCouponIsRemovedFromShortcuts() {
        // 바로가기를 눌렀는데 "쿠폰을 찾을 수 없습니다" 가 뜨면 앱이 고장난 것처럼 보인다.
        RecentCouponShortcuts.record(context, "r", "a", "남을 것")
        RecentCouponShortcuts.record(context, "r", "b", "지울 것")

        RecentCouponShortcuts.forget(context, "r", "b")

        val ids = ShortcutManagerCompat.getDynamicShortcuts(context).map { it.id }
        assertTrue(ids.contains("coupon-r-a"))
        assertEquals(false, ids.contains("coupon-r-b"))
    }

    @Test
    fun signOutClearsEverything() {
        // 다음 사용자에게 이전 계정의 쿠폰 이름이 남으면 안 된다.
        RecentCouponShortcuts.record(context, "r", "a", "비밀 쿠폰")

        RecentCouponShortcuts.clear(context)

        assertEquals(0, ShortcutManagerCompat.getDynamicShortcuts(context).size)
    }

    @Test
    fun blankTitleGetsAReadableFallback() {
        RecentCouponShortcuts.record(context, "r", "a", "   ")
        assertEquals("이름 없는 쿠폰", ShortcutManagerCompat.getDynamicShortcuts(context).first().shortLabel)
    }
}
