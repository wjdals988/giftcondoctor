package com.giftcondoctor.app.core

import com.google.android.gms.common.api.CommonStatusCodes

/**
 * Google 로그인 실패 원인을 사용자가 행동할 수 있는 문구로 옮긴다.
 *
 * 기존 구현은 결과 코드가 RESULT_OK 가 아니면 무조건 "Google 로그인이 취소되었습니다."
 * 를 보여줬다. 그런데 GoogleSignIn 은 설정 오류(DEVELOPER_ERROR), 네트워크 실패,
 * Play 서비스 문제에서도 RESULT_CANCELED 를 돌려준다. 즉 사용자가 취소한 적이 없는데도
 * 취소했다고 안내했고, 실제 원인을 알 방법이 전혀 없었다.
 *
 * 상수는 GoogleSignInStatusCodes 와 CommonStatusCodes 에서 온다. 여기서는 숫자를 직접
 * 다뤄 테스트가 Play 서비스 의존성 없이 돌아가게 한다.
 */
object GoogleSignInErrors {
    /** 사용자가 계정 선택 화면을 직접 닫았다. */
    const val SIGN_IN_CANCELLED = 12501

    /** 로그인 시도가 실패했지만 원인이 특정되지 않았다. */
    const val SIGN_IN_FAILED = 12500

    /** 이미 로그인 진행 중이다. */
    const val SIGN_IN_CURRENTLY_IN_PROGRESS = 12502

    /**
     * 상태 코드를 사용자 문구로 변환한다.
     *
     * @param statusCode GoogleSignIn 이 돌려준 상태 코드. 알 수 없으면 null.
     */
    fun message(statusCode: Int?): String = when (statusCode) {
        SIGN_IN_CANCELLED -> "Google 로그인을 취소했어요."
        SIGN_IN_CURRENTLY_IN_PROGRESS -> "이미 Google 로그인을 진행 중이에요. 잠시 후 다시 시도해 주세요."
        CommonStatusCodes.NETWORK_ERROR ->
            "네트워크에 연결하지 못했어요. 연결 상태를 확인한 뒤 다시 시도해 주세요."
        CommonStatusCodes.DEVELOPER_ERROR ->
            "이 앱의 Google 로그인 설정이 서버에 등록되지 않았습니다. " +
                "설치한 앱의 서명 인증서가 Firebase에 등록되어 있는지 확인이 필요합니다."
        CommonStatusCodes.API_NOT_CONNECTED, CommonStatusCodes.SERVICE_DISABLED ->
            "이 기기의 Google Play 서비스를 사용할 수 없어요. 업데이트 후 다시 시도해 주세요."
        CommonStatusCodes.INVALID_ACCOUNT ->
            "선택한 Google 계정을 사용할 수 없어요. 다른 계정으로 시도해 주세요."
        SIGN_IN_FAILED, null ->
            "Google 로그인에 실패했어요. 잠시 후 다시 시도하거나 이메일로 로그인해 주세요."
        else ->
            "Google 로그인에 실패했어요. (오류 $statusCode) 잠시 후 다시 시도하거나 이메일로 로그인해 주세요."
    }

    /**
     * 원인을 사용자가 스스로 해결할 수 있는지 여부.
     *
     * DEVELOPER_ERROR 는 앱 배포자가 Firebase 설정을 고쳐야 하므로 재시도해도 소용없다.
     * 이 경우 재시도를 권하는 대신 이메일 로그인을 안내해야 한다.
     */
    fun isUserActionable(statusCode: Int?): Boolean =
        statusCode != CommonStatusCodes.DEVELOPER_ERROR
}
