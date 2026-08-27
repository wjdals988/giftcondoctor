package com.giftcondoctor.app.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.giftcondoctor.app.ui.components.ConfirmHapticEffect
import com.giftcondoctor.app.ui.components.performConfirmHaptic
import com.giftcondoctor.app.ui.theme.GDTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 확인 촉각 피드백 검증.
 *
 * 실제 진동은 계측 환경에서 관측할 수 없다. 대신 관측 가능한 두 가지를 검증한다.
 *   1. performHapticFeedback 이 실제로 호출되고 그 횟수가 정확한지
 *      (중복 발화는 사용자에게 잡음이므로 횟수가 계약이다)
 *   2. API 수준에 맞는 상수를 고르는지
 */
@RunWith(AndroidJUnit4::class)
class HapticFeedbackInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    /** performHapticFeedback 호출을 가로채 세는 View. */
    private class CountingView(context: Context) : View(context) {
        val constants = mutableListOf<Int>()
        override fun performHapticFeedback(feedbackConstant: Int): Boolean {
            constants += feedbackConstant
            return true
        }
    }

    @Test
    fun performConfirmHapticPicksApiAppropriateConstant() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = CountingView(context)

        view.performConfirmHaptic()

        val expected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        assertEquals(listOf(expected), view.constants)
    }

    @Test
    fun performConfirmHapticFiresExactlyOncePerCall() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val view = CountingView(context)

        view.performConfirmHaptic()
        view.performConfirmHaptic()

        assertEquals(2, view.constants.size)
    }

    @Test
    fun confirmHapticEffectSurvivesTriggerTransitionsWithoutCrashing() {
        // ConfirmHapticEffect 는 LocalView 를 쓰므로 실제 호출 횟수를 가로챌 수 없다.
        // 여기서는 상태 전이(false -> true -> false -> true)와 같은 값 재대입에서
        // 컴포지션이 깨지지 않는지만 확인한다. 호출 횟수 계약은 위 두 테스트가 담당한다.
        val trigger = mutableStateOf(false)
        composeRule.setContent { GDTheme { ConfirmHapticEffect(trigger = trigger.value) } }
        composeRule.waitForIdle()

        listOf(true, true, false, true).forEach { next ->
            composeRule.runOnIdle { trigger.value = next }
            composeRule.waitForIdle()
        }
    }

    @Test
    fun versionedConfirmHapticSurvivesVersionIncrements() {
        val version = mutableStateOf(0)
        composeRule.setContent {
            GDTheme { ConfirmHapticEffect(version = version.value, key = "coupon-1") }
        }
        composeRule.waitForIdle()

        listOf(1, 2, 3).forEach { next ->
            composeRule.runOnIdle { version.value = next }
            composeRule.waitForIdle()
        }
    }
}
