package com.giftcondoctor.app.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView

/**
 * 확인용 촉각 피드백.
 *
 * 바코드 자동 감지 성공이나 사용 완료처럼 "방금 무언가 확정됐다" 를 알리는 지점에만
 * 쓴다. 일반 버튼 탭에는 붙이지 않는다. 시스템이 이미 탭 피드백을 처리하고, 모든
 * 탭에 진동을 넣으면 신호가 아니라 잡음이 된다.
 *
 * View.performHapticFeedback 을 쓰는 이유는 두 가지다.
 *  - 사용자의 시스템 "터치 진동" 설정을 그대로 따른다. FLAG_IGNORE_GLOBAL_SETTING 을
 *    넘기지 않으므로 진동을 꺼 둔 사용자에게는 아무 일도 일어나지 않는다.
 *  - API 30+ 의 CONFIRM 상수를 쓸 수 있다. Compose 의 HapticFeedbackType 은 이
 *    버전(Compose 1.7)에서 LongPress 와 TextHandleMove 뿐이라 "확인" 의미를
 *    표현할 수 없다. minSdk 26 을 지원해야 하므로 하위에서는 VIRTUAL_KEY 로 낮춘다.
 */
fun android.view.View.performConfirmHaptic() {
    val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        HapticFeedbackConstants.CONFIRM
    } else {
        HapticFeedbackConstants.VIRTUAL_KEY
    }
    performHapticFeedback(constant)
}

/**
 * [trigger] 가 참으로 바뀌는 순간 한 번만 확인 피드백을 낸다.
 *
 * trigger 를 key 로 쓰므로 같은 상태가 유지되는 동안 재발화하지 않는다. 화면 회전이나
 * 재구성으로 값이 그대로 다시 들어와도 LaunchedEffect 키가 같아 중복 진동이 없다.
 */
@Composable
fun ConfirmHapticEffect(trigger: Boolean) {
    val view = LocalView.current
    LaunchedEffect(trigger) {
        if (trigger) view.performConfirmHaptic()
    }
}

/**
 * [version] 이 0 보다 커지고 값이 바뀔 때마다 확인 피드백을 낸다.
 *
 * 같은 동작이 반복될 수 있는 경우(사용 완료 후 실행 취소, 다시 사용 완료)에 쓴다.
 * 단조 증가하는 버전 값을 키로 삼아 매 발생마다 정확히 한 번 울린다.
 */
@Composable
fun ConfirmHapticEffect(version: Int, key: Any?) {
    val view = LocalView.current
    LaunchedEffect(version, key) {
        if (version > 0) view.performConfirmHaptic()
    }
}
