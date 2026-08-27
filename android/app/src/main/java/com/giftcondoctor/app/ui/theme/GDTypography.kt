package com.giftcondoctor.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * 한글 본문용 타이포그래피.
 *
 * M3 기본 타입스케일은 라틴 문자 기준으로 행간이 설계되어 있다. 한글은 받침 때문에
 * 실제 글자 높이가 라틴보다 크고, 같은 lineHeight 에서 행간이 좁아 보인다. 본문
 * 계열(body*, title*)의 lineHeight 를 폰트 크기의 1.5배 이상으로 올린다.
 *
 * label* 과 headline* 은 기본값에 가깝게 유지한다. label 은 배지·칩 안에서 쓰이므로
 * 행간을 올리면 컴포넌트 높이가 커져 48dp 터치 타겟 계산과 목록 밀도가 함께 흔들린다.
 * headline 은 한 줄로 끝나는 경우가 많아 1.35~1.42 로도 충분하다.
 *
 * includeFontPadding = false 로 한글 글꼴의 상하 여백을 제거하고, LineHeightStyle 로
 * 늘어난 행간을 위아래에 균등 배분해 첫 줄이 아래로 밀리지 않게 한다.
 */
private val KoreanPlatformStyle = PlatformTextStyle(includeFontPadding = false)

private val KoreanLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None
)

private fun koreanBody(fontSize: Int, lineHeight: Int, base: TextStyle): TextStyle =
    base.copy(
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        platformStyle = KoreanPlatformStyle,
        lineHeightStyle = KoreanLineHeightStyle
    )

private val Default = Typography()

/** 본문 배율은 lineHeight / fontSize 로, 주석의 배수는 검증값이다. */
val GDTypography = Typography(
    displayLarge = Default.displayLarge.copy(platformStyle = KoreanPlatformStyle),
    displayMedium = Default.displayMedium.copy(platformStyle = KoreanPlatformStyle),
    displaySmall = Default.displaySmall.copy(platformStyle = KoreanPlatformStyle),

    // headline: 제목 한 줄 위주라 1.35~1.42 유지
    headlineLarge = koreanBody(32, 44, Default.headlineLarge),   // 1.38
    headlineMedium = koreanBody(28, 38, Default.headlineMedium), // 1.36
    headlineSmall = koreanBody(24, 34, Default.headlineSmall),   // 1.42

    // title: 카드 제목·섹션 제목. 본문과 함께 읽히므로 1.5 확보
    titleLarge = koreanBody(22, 33, Default.titleLarge),   // 1.50
    titleMedium = koreanBody(16, 24, Default.titleMedium), // 1.50
    titleSmall = koreanBody(14, 21, Default.titleSmall),   // 1.50

    // body: 실제 읽는 문장. 1.55 이상
    bodyLarge = koreanBody(16, 26, Default.bodyLarge),   // 1.63
    bodyMedium = koreanBody(14, 22, Default.bodyMedium), // 1.57
    bodySmall = koreanBody(12, 19, Default.bodySmall),   // 1.58

    // label: 배지·칩 내부. 높이 증가를 막기 위해 기본 행간 유지
    labelLarge = Default.labelLarge.copy(platformStyle = KoreanPlatformStyle),
    labelMedium = Default.labelMedium.copy(platformStyle = KoreanPlatformStyle),
    labelSmall = Default.labelSmall.copy(platformStyle = KoreanPlatformStyle)
)
