package com.giftcondoctor.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

private fun coupon(id: String, room: String = "r1", title: String = "쿠폰$id") =
    RecentCoupon(roomId = room, couponId = id, title = title)

class RecentCouponsTest {
    @Test
    fun mostRecentGoesFirst() {
        val list = withRecentCoupon(listOf(coupon("a")), coupon("b"))
        assertEquals(listOf("b", "a"), list.map { it.couponId })
    }

    @Test
    fun reopeningMovesToFrontWithoutDuplicating() {
        // 매장에서 반복해 쓰는 쿠폰이 뒤로 밀리면 바로가기의 의미가 없다.
        val start = listOf(coupon("a"), coupon("b"), coupon("c"))
        val list = withRecentCoupon(start, coupon("c"))
        assertEquals(listOf("c", "a", "b"), list.map { it.couponId })
        assertEquals(3, list.size)
    }

    @Test
    fun listIsCappedAtTheLimit() {
        var list = emptyList<RecentCoupon>()
        listOf("a", "b", "c", "d", "e").forEach { list = withRecentCoupon(list, coupon(it)) }
        assertEquals(MAX_RECENT_COUPON_SHORTCUTS, list.size)
        assertEquals(listOf("e", "d", "c"), list.map { it.couponId })
    }

    @Test
    fun sameCouponIdInDifferentRoomsIsNotDeduped() {
        val start = listOf(coupon("x", room = "r1"))
        val list = withRecentCoupon(start, coupon("x", room = "r2"))
        assertEquals(2, list.size)
    }

    @Test
    fun blankIdentifiersAreIgnored() {
        val start = listOf(coupon("a"))
        assertEquals(start, withRecentCoupon(start, RecentCoupon("", "c", "t")))
        assertEquals(start, withRecentCoupon(start, RecentCoupon("r", "", "t")))
    }

    @Test
    fun removalDropsOnlyTheMatchingEntry() {
        // 바로가기를 눌렀는데 "쿠폰을 찾을 수 없습니다" 가 뜨면 앱이 고장난 것처럼 보인다.
        val start = listOf(coupon("a", room = "r1"), coupon("a", room = "r2"), coupon("b"))
        val list = withoutRecentCoupon(start, "r1", "a")
        assertEquals(2, list.size)
        assertEquals(listOf("r2", "r1"), list.map { it.roomId })
    }

    @Test
    fun labelFallsBackWhenTitleIsBlank() {
        assertEquals("이름 없는 쿠폰", recentCouponShortcutLabel("   "))
        assertEquals("아메리카노", recentCouponShortcutLabel("  아메리카노  "))
    }
}
