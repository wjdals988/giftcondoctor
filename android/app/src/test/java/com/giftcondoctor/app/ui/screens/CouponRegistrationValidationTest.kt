package com.giftcondoctor.app.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CouponRegistrationValidationTest {
    @Test
    fun `이름과 ISO 날짜가 모두 유효하면 등록할 수 있다`() {
        val validation = validateCouponRegistration("아메리카노", "2026-12-31")

        assertTrue(validation.isTitleValid)
        assertTrue(validation.isExpiryValid)
        assertTrue(validation.canSubmit)
    }

    @Test
    fun `공백 이름은 등록할 수 없다`() {
        val validation = validateCouponRegistration("   ", "2026-12-31")

        assertFalse(validation.isTitleValid)
        assertTrue(validation.isExpiryValid)
        assertFalse(validation.canSubmit)
    }

    @Test
    fun `존재하지 않는 날짜는 등록할 수 없다`() {
        val validation = validateCouponRegistration("아메리카노", "2026-02-30")

        assertTrue(validation.isTitleValid)
        assertFalse(validation.isExpiryValid)
        assertFalse(validation.canSubmit)
    }

    @Test
    fun `다른 날짜 형식은 등록할 수 없다`() {
        val validation = validateCouponRegistration("아메리카노", "2026.12.31")

        assertFalse(validation.isExpiryValid)
        assertFalse(validation.canSubmit)
    }
}
