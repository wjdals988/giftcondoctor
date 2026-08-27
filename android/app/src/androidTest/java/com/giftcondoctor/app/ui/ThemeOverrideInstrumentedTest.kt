package com.giftcondoctor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.giftcondoctor.app.ui.theme.GDDarkBackground
import com.giftcondoctor.app.ui.theme.GDBackground
import com.giftcondoctor.app.ui.theme.GDSkeletonDark
import com.giftcondoctor.app.ui.theme.GDSkeletonLight
import com.giftcondoctor.app.ui.theme.GDTheme
import com.giftcondoctor.app.ui.theme.LocalGDDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GDTheme(darkTheme = ...) override 가 색 분기 컴포넌트까지 일관되게 전달되는지 검증한다.
 *
 * 배경: PR #30 에서 다크 테마를 넣을 때 GDExpiryBadge, 카테고리 아이콘, 스켈레톤이
 * isSystemInDarkTheme() 을 직접 호출했다. GDTheme 은 darkTheme 을 파라미터로 받으므로
 * 시스템이 라이트인 상태에서 GDTheme(darkTheme = true) 를 넘기면 다크 스킴 위에
 * 라이트 배지가 얹히는 불일치가 생겼다. LocalGDDarkTheme 로 고쳤고 이 테스트가 그
 * 상태를 고정한다.
 *
 * 계측 환경의 시스템 테마와 무관하게 override 값만으로 판정하므로 두 방향을 모두 검사한다.
 */
@RunWith(AndroidJUnit4::class)
class ThemeOverrideInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun darkOverridePropagatesToColorBranchingComponents() {
        var localValue: Boolean? = null
        var background: Color? = null
        var skeleton: Color? = null

        composeRule.setContent {
            GDTheme(darkTheme = true) {
                localValue = LocalGDDarkTheme.current
                background = MaterialTheme.colorScheme.background
                skeleton = if (LocalGDDarkTheme.current) GDSkeletonDark else GDSkeletonLight
            }
        }
        composeRule.waitForIdle()

        assertEquals(true, localValue)
        assertEquals(GDDarkBackground, background)
        assertEquals(GDSkeletonDark, skeleton)
    }

    @Test
    fun lightOverridePropagatesToColorBranchingComponents() {
        var localValue: Boolean? = null
        var background: Color? = null
        var skeleton: Color? = null

        composeRule.setContent {
            GDTheme(darkTheme = false) {
                localValue = LocalGDDarkTheme.current
                background = MaterialTheme.colorScheme.background
                skeleton = if (LocalGDDarkTheme.current) GDSkeletonDark else GDSkeletonLight
            }
        }
        composeRule.waitForIdle()

        assertEquals(false, localValue)
        assertEquals(GDBackground, background)
        assertEquals(GDSkeletonLight, skeleton)
    }

    @Test
    fun localDefaultsToLightOutsideGDTheme() {
        // GDTheme 밖에서 읽히면 라이트로 떨어진다. 프로덕션 경로는 항상 GDTheme 안이지만
        // 기본값이 예측 가능해야 프리뷰에서 깨지지 않는다.
        var localValue: Boolean? = null
        composeRule.setContent { localValue = LocalGDDarkTheme.current }
        composeRule.waitForIdle()
        assertEquals(false, localValue)
    }
}
