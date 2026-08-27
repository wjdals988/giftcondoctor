package com.giftcondoctor.app.ui.viewmodel

internal enum class NextCouponPrefetchStage {
    Idle,
    Processing,
    Ready
}

internal data class NextCouponPrefetchState(
    val source: String? = null,
    val stage: NextCouponPrefetchStage = NextCouponPrefetchStage.Idle,
    val analysisReady: Boolean = false,
    val uploadReady: Boolean = false
)

internal fun shouldStartNextCouponPrefetch(
    currentSource: String?,
    requestedSource: String?,
    currentImageProcessing: Boolean,
    registrationBusy: Boolean,
    existingState: NextCouponPrefetchState
): Boolean = requestedSource != null &&
    currentSource != null &&
    requestedSource != currentSource &&
    !currentImageProcessing &&
    !registrationBusy &&
    existingState.source != requestedSource

internal fun nextCouponPrefetchStatusText(state: NextCouponPrefetchState): String? = when {
    state.stage == NextCouponPrefetchStage.Processing -> "다음 쿠폰을 미리 읽는 중"
    state.stage != NextCouponPrefetchStage.Ready -> null
    state.analysisReady && state.uploadReady -> "다음 쿠폰 자동 입력·빠른 업로드 준비 완료"
    state.analysisReady -> "다음 쿠폰 자동 입력 준비 완료"
    state.uploadReady -> "다음 쿠폰 빠른 업로드 준비 완료"
    else -> null
}
