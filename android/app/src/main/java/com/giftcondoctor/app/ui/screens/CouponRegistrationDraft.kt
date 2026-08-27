package com.giftcondoctor.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.giftcondoctor.app.core.CouponTextSuggestion
import com.giftcondoctor.app.core.DetectedCouponBarcode

internal const val COUPON_TITLE_MAX_LENGTH = 100
internal const val COUPON_BRAND_MAX_LENGTH = 100
internal const val COUPON_EXPIRY_MAX_LENGTH = 10

@Stable
internal class CouponRegistrationDraft private constructor(
    title: String,
    brand: String,
    expiresLocalDate: String,
    barcodeValue: String,
    barcodeFormat: String?,
    manualBarcodeEntry: Boolean,
    private var titleEdited: Boolean,
    private var brandEdited: Boolean,
    private var expiryEdited: Boolean,
    private var barcodeEdited: Boolean
) {
    var title by mutableStateOf(title)
        private set
    var brand by mutableStateOf(brand)
        private set
    var expiresLocalDate by mutableStateOf(expiresLocalDate)
        private set
    var barcodeValue by mutableStateOf(barcodeValue)
        private set
    var barcodeFormat by mutableStateOf(barcodeFormat)
        private set
    var manualBarcodeEntry by mutableStateOf(manualBarcodeEntry)
        private set

    fun updateTitle(value: String) {
        title = value.take(COUPON_TITLE_MAX_LENGTH)
        titleEdited = true
    }

    fun updateBrand(value: String) {
        brand = value.take(COUPON_BRAND_MAX_LENGTH)
        brandEdited = true
    }

    fun updateExpiry(value: String) {
        expiresLocalDate = value.take(COUPON_EXPIRY_MAX_LENGTH)
        expiryEdited = true
    }

    fun applySuggestion(suggestion: CouponTextSuggestion) {
        if (!titleEdited) suggestion.title?.let { title = it.take(COUPON_TITLE_MAX_LENGTH) }
        if (!brandEdited) suggestion.brand?.let { brand = it.take(COUPON_BRAND_MAX_LENGTH) }
        if (!expiryEdited) {
            suggestion.expiresLocalDate?.let { expiresLocalDate = it.toString() }
        }
    }

    fun applyDetectedBarcode(barcode: DetectedCouponBarcode?) {
        if (barcodeEdited) return
        barcodeValue = barcode?.value.orEmpty()
        barcodeFormat = barcode?.format
        manualBarcodeEntry = false
    }

    fun updateBarcodeValue(value: String) {
        barcodeValue = value
        barcodeEdited = true
    }

    fun updateBarcodeFormat(format: String) {
        barcodeFormat = format
        barcodeEdited = true
    }

    fun clearBarcode() {
        barcodeValue = ""
        barcodeFormat = null
        manualBarcodeEntry = false
        barcodeEdited = true
    }

    fun startManualBarcodeEntry() {
        barcodeValue = ""
        barcodeFormat = "CODE_128"
        manualBarcodeEntry = true
        barcodeEdited = true
    }

    companion object {
        fun create(defaultExpiry: String) = CouponRegistrationDraft(
            title = "",
            brand = "",
            expiresLocalDate = defaultExpiry,
            barcodeValue = "",
            barcodeFormat = null,
            manualBarcodeEntry = false,
            titleEdited = false,
            brandEdited = false,
            expiryEdited = false,
            barcodeEdited = false
        )

        val Saver: Saver<CouponRegistrationDraft, Any> = mapSaver(
            save = { draft ->
                mapOf(
                    "title" to draft.title,
                    "brand" to draft.brand,
                    "expiry" to draft.expiresLocalDate,
                    "barcodeValue" to draft.barcodeValue,
                    "barcodeFormat" to draft.barcodeFormat.orEmpty(),
                    "manualBarcode" to draft.manualBarcodeEntry,
                    "titleEdited" to draft.titleEdited,
                    "brandEdited" to draft.brandEdited,
                    "expiryEdited" to draft.expiryEdited,
                    "barcodeEdited" to draft.barcodeEdited
                )
            },
            restore = { values ->
                CouponRegistrationDraft(
                    title = values.getValue("title") as String,
                    brand = values.getValue("brand") as String,
                    expiresLocalDate = values.getValue("expiry") as String,
                    barcodeValue = values.getValue("barcodeValue") as String,
                    barcodeFormat = (values.getValue("barcodeFormat") as String).ifBlank { null },
                    manualBarcodeEntry = values.getValue("manualBarcode") as Boolean,
                    titleEdited = values.getValue("titleEdited") as Boolean,
                    brandEdited = values.getValue("brandEdited") as Boolean,
                    expiryEdited = values.getValue("expiryEdited") as Boolean,
                    barcodeEdited = values.getValue("barcodeEdited") as Boolean
                )
            }
        )
    }
}

@Composable
internal fun rememberCouponRegistrationDraft(
    imageSource: String?,
    defaultExpiry: String
): CouponRegistrationDraft = rememberSaveable(imageSource, saver = CouponRegistrationDraft.Saver) {
    CouponRegistrationDraft.create(defaultExpiry)
}
