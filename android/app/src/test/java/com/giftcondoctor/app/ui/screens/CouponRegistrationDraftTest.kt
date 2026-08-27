package com.giftcondoctor.app.ui.screens

import com.giftcondoctor.app.core.CouponTextSuggestion
import com.giftcondoctor.app.core.DetectedCouponBarcode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CouponRegistrationDraftTest {
    @Test
    fun `automatic results fill untouched draft fields`() {
        val draft = CouponRegistrationDraft.create("2026-08-22")

        draft.applySuggestion(
            CouponTextSuggestion(
                title = "아메리카노",
                brand = "스타벅스",
                expiresLocalDate = LocalDate.parse("2026-12-31")
            )
        )
        draft.applyDetectedBarcode(DetectedCouponBarcode("123456", "CODE_128"))

        assertEquals("아메리카노", draft.title)
        assertEquals("스타벅스", draft.brand)
        assertEquals("2026-12-31", draft.expiresLocalDate)
        assertEquals("123456", draft.barcodeValue)
        assertEquals("CODE_128", draft.barcodeFormat)
        assertFalse(draft.manualBarcodeEntry)
    }

    @Test
    fun `late automatic results never overwrite manual edits`() {
        val draft = CouponRegistrationDraft.create("2026-08-22")
        draft.updateTitle("직접 입력 이름")
        draft.updateBrand("직접 입력 브랜드")
        draft.updateExpiry("2027-01-01")
        draft.startManualBarcodeEntry()
        draft.updateBarcodeValue("MANUAL-123")
        draft.updateBarcodeFormat("QR_CODE")

        draft.applySuggestion(
            CouponTextSuggestion(
                title = "늦은 OCR 이름",
                brand = "늦은 OCR 브랜드",
                expiresLocalDate = LocalDate.parse("2026-09-01")
            )
        )
        draft.applyDetectedBarcode(DetectedCouponBarcode("AUTO-999", "CODE_128"))

        assertEquals("직접 입력 이름", draft.title)
        assertEquals("직접 입력 브랜드", draft.brand)
        assertEquals("2027-01-01", draft.expiresLocalDate)
        assertEquals("MANUAL-123", draft.barcodeValue)
        assertEquals("QR_CODE", draft.barcodeFormat)
    }

    @Test
    fun `clearing barcode is an explicit edit that blocks redetection`() {
        val draft = CouponRegistrationDraft.create("2026-08-22")
        draft.clearBarcode()

        draft.applyDetectedBarcode(DetectedCouponBarcode("AUTO-999", "CODE_128"))

        assertEquals("", draft.barcodeValue)
        assertNull(draft.barcodeFormat)
        assertFalse(draft.manualBarcodeEntry)
    }

    @Test
    fun `manual and automatic text stay within firestore field limits`() {
        val draft = CouponRegistrationDraft.create("2026-08-22")
        draft.updateTitle("T".repeat(COUPON_TITLE_MAX_LENGTH + 1))
        draft.updateExpiry("2026-08-22-extra")
        draft.applySuggestion(
            CouponTextSuggestion(brand = "B".repeat(COUPON_BRAND_MAX_LENGTH + 1))
        )

        assertEquals(COUPON_TITLE_MAX_LENGTH, draft.title.length)
        assertEquals(COUPON_BRAND_MAX_LENGTH, draft.brand.length)
        assertEquals("2026-08-22", draft.expiresLocalDate)
    }
}
