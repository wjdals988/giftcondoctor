package com.giftcondoctor.app.ui.screens

import java.time.LocalDate

internal data class CouponRegistrationValidation(
    val isTitleValid: Boolean,
    val isExpiryValid: Boolean
) {
    val canSubmit: Boolean
        get() = isTitleValid && isExpiryValid
}

internal fun validateCouponRegistration(
    title: String,
    expiresLocalDate: String
): CouponRegistrationValidation = CouponRegistrationValidation(
    isTitleValid = title.isNotBlank(),
    isExpiryValid = runCatching { LocalDate.parse(expiresLocalDate) }.isSuccess
)
