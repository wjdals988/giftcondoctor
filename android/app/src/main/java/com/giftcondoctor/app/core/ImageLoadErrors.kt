package com.giftcondoctor.app.core

/**
 * 쿠폰 원본 이미지 로딩 실패를 사용자가 행동할 수 있는 문구로 옮긴다.
 *
 * 2026-08-28 비행기 모드 실측에서 오프라인 상세 화면이 이 문구를 그대로 보여줬다.
 *
 * ```
 * A network error (such as timeout, interrupted connection or unreachable host) has occurred.
 * ```
 *
 * 한국어 앱에 Firebase SDK 의 영어 원문 예외가 노출된다. 사용자는 무슨 뜻인지도,
 * 무엇을 해야 하는지도 알 수 없다. 원인은 오류를 `localizedMessage` 로 그대로
 * 흘리는 경로다. Google 로그인에도 같은 문제가 있었고(PR #40) 상태 코드를 문구로
 * 옮겨 해결했다.
 *
 * 예외 타입 대신 메시지 문자열을 보는 이유는, Firebase Storage 와 Firestore 가
 * 서로 다른 예외 계층을 쓰면서 네트워크 실패를 같은 영어 문장으로 표현하기
 * 때문이다. 타입으로 나누려면 두 SDK 의 내부 분류를 모두 따라가야 하는데,
 * 사용자에게 필요한 구분은 "네트워크인가 아닌가" 하나뿐이다.
 */
object ImageLoadErrors {

    private val NETWORK_HINTS = listOf(
        "network error",
        "unreachable",
        "timeout",
        "timed out",
        "interrupted connection",
        "unable to resolve host",
        "failed to connect",
        "no address associated"
    )

    /**
     * 이 실패가 네트워크 때문인지 판정한다.
     *
     * 네트워크 실패는 사용자가 고칠 수 있는 것이 없고, 대신 **다른 경로로 쿠폰을
     * 쓸 수 있다**. 그래서 다른 실패와 구분해야 한다.
     */
    fun isNetworkFailure(rawMessage: String?): Boolean {
        val message = rawMessage?.lowercase() ?: return false
        return NETWORK_HINTS.any { message.contains(it) }
    }

    /**
     * 사용자 문구로 변환한다.
     *
     * @param hasBarcode 이 쿠폰에 바코드 값이 저장돼 있는지.
     *
     * 바코드가 있으면 네트워크가 없어도 계산대에서 쓸 수 있다. 바코드 값은
     * 쿠폰 문서에 있고(Firestore 오프라인 캐시가 기본 활성) 이미지는 ZXing 이
     * 기기에서 그린다. 즉 원본 이미지 실패는 **결제를 막지 않는다.** 그 사실을
     * 말하지 않으면 사용자는 "데이터 안 터지면 이 앱은 못 쓴다" 고 학습한다.
     */
    fun message(rawMessage: String?, hasBarcode: Boolean): String = when {
        isNetworkFailure(rawMessage) && hasBarcode ->
            "지금은 네트워크가 없어 원본 이미지를 불러올 수 없어요. 바코드는 그대로 사용할 수 있습니다."
        isNetworkFailure(rawMessage) ->
            "지금은 네트워크가 없어 원본 이미지를 불러올 수 없어요. 연결된 뒤 다시 시도해 주세요."
        // 원인을 모를 때 영어 원문을 흘리느니 할 수 있는 행동을 말한다.
        else -> "원본 이미지를 불러오지 못했어요. 다시 시도해 주세요."
    }
}
