package com.giftcondoctor.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.giftcondoctor.app.BuildConfig
import com.giftcondoctor.app.core.ExpiryUrgency
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Shape
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import com.giftcondoctor.app.ui.theme.GDSkeletonDark
import com.giftcondoctor.app.ui.theme.GDSkeletonLight
import com.giftcondoctor.app.ui.theme.LocalGDDarkTheme
import androidx.compose.material3.TextButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GDScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(title, modifier = Modifier.gdHeading()) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                        }
                    }
                },
                actions = actions
            )
        }
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
fun LoadingState(message: String = "불러오는 중입니다") {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text(message, modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
fun EmptyState(
    message: String,
    title: String? = null,
    icon: ImageVector? = null,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tertiaryActionLabel: String? = null,
    onTertiaryAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            }
        }
        if (title != null) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = if (icon == null) 0.dp else 16.dp)
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = if (title == null && icon == null) 0.dp else 6.dp)
        )
        if (primaryActionLabel != null && onPrimaryAction != null) {
            Button(onClick = onPrimaryAction, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Text(primaryActionLabel)
            }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            OutlinedButton(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(secondaryActionLabel)
            }
        }
        // 3순위 동작은 TextButton 으로 둔다. 버튼 3개가 모두 같은 무게를 가지면
        // 사용자는 무엇을 먼저 눌러야 할지 판단해야 하고, 그 판단 자체가 비용이다.
        if (tertiaryActionLabel != null && onTertiaryAction != null) {
            TextButton(onClick = onTertiaryAction, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(tertiaryActionLabel)
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun InlineMessage(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
fun ButtonProgressIndicator(color: Color = MaterialTheme.colorScheme.onPrimary) {
    CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = color
    )
    Spacer(Modifier.width(8.dp))
}

/**
 * 설치된 앱의 버전 문구.
 *
 * debug 빌드에서는 뒤에 "· 개발 빌드" 를 붙인다. 2026-08-28 에 같은 versionName
 * 을 가진 debug 와 release 를 번갈아 설치하다가 Google 로그인 가능 여부가 갈렸는데,
 * 화면만 봐서는 어느 쪽이 깔려 있는지 구분할 방법이 없었다. 서명 인증서가 달라
 * 동작이 실제로 달라지므로 화면에서 구분되어야 한다.
 */
@Composable
fun appVersionLabel(): String {
    val suffix = if (BuildConfig.DEBUG) " · 개발 빌드" else ""
    return "버전 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$suffix"
}

@Composable
fun AppVersionText(modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text = appVersionLabel(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
fun GDInfoBanner(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Info
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ReminderTimeBanner(modifier: Modifier = Modifier) {
    GDInfoBanner(
        title = "푸시 알림은 매일 오전 9시에 와요",
        body = "한국시간 기준으로 만료 D-7, D-3, D-1, 당일처럼 선택한 시점에만 알려드립니다.",
        modifier = modifier,
        icon = Icons.Default.Schedule
    )
}

@Composable
fun GDBadge(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    Box(
        modifier = modifier
            .background(containerColor, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GDStatCard(label: String, value: String, modifier: Modifier = Modifier, accent: Color = MaterialTheme.colorScheme.primary) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * 만료 긴급도 배지. 색·아이콘·텍스트 3중으로 인코딩해 색각 이상 사용자도 구분할 수 있게 한다.
 * 라이트·다크 8개 조합 모두 WCAG AA(4.5:1) 이상이다.
 * 라이트 — 긴급 5.84, 임박 6.07, 여유 5.83, 종료 5.69
 * 다크 — 긴급 9.53, 임박 10.04, 여유 8.34, 종료 7.84
 */
@Composable
fun GDExpiryBadge(
    urgency: ExpiryUrgency,
    text: String,
    modifier: Modifier = Modifier
) {
    val dark = LocalGDDarkTheme.current
    val container: Color
    val content: Color
    val icon: ImageVector
    when (urgency) {
        ExpiryUrgency.Critical -> {
            container = if (dark) Color(0xFF3A1416) else Color(0xFFFDECEC)
            content = if (dark) Color(0xFFFFB3AE) else Color(0xFFB32025)
            icon = Icons.Default.ErrorOutline
        }
        ExpiryUrgency.Soon -> {
            container = if (dark) Color(0xFF332401) else Color(0xFFFFF4D6)
            content = if (dark) Color(0xFFF5CE7A) else Color(0xFF7A5600)
            icon = Icons.Default.WarningAmber
        }
        ExpiryUrgency.Relaxed -> {
            container = if (dark) Color(0xFF0B3B36) else Color(0xFFE7F8F4)
            content = if (dark) Color(0xFF8FE3D6) else Color(0xFF006B63)
            icon = Icons.Default.Schedule
        }
        ExpiryUrgency.Ended -> {
            container = if (dark) Color(0xFF22272A) else Color(0xFFF4F7F9)
            content = if (dark) Color(0xFFB4BCC1) else Color(0xFF5A636B)
            icon = Icons.Default.CheckCircle
        }
        // Distant 는 호출부에서 shouldShowExpiryBadge 로 걸러지지만, when 을 완전하게
        // 두어 새 계층이 추가될 때 컴파일러가 누락을 잡게 한다. 값은 Relaxed 와 같다.
        ExpiryUrgency.Distant -> {
            container = if (dark) Color(0xFF0B3B36) else Color(0xFFE7F8F4)
            content = if (dark) Color(0xFF8FE3D6) else Color(0xFF006B63)
            icon = Icons.Default.Schedule
        }
    }
    Row(
        modifier = modifier
            .background(container, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * TalkBack 제목 단위 탐색용 heading 표식.
 *
 * 스크린리더 사용자는 화면 전체를 순차 탐색하지 않고 제목만 건너뛰며 구조를 파악한다.
 * heading() 이 없으면 이 탐색이 불가능해 긴 설정 화면에서 원하는 섹션까지 모든 요소를
 * 하나씩 지나가야 한다.
 *
 * 적용 대상은 화면 제목과 섹션 제목뿐이다. 목록 항목의 제목(쿠폰 이름, 방 이름)에는
 * 붙이지 않는다. 항목이 100개면 heading 이 100개가 되어 제목 탐색 자체가 무의미해진다.
 */
fun Modifier.gdHeading(): Modifier = semantics { heading() }

@Composable
private fun skeletonColor(): Color =
    if (LocalGDDarkTheme.current) GDSkeletonDark else GDSkeletonLight

/**
 * 스켈레톤 플레이스홀더 한 조각.
 *
 * 전체화면 스피너는 화면이 어떤 구조인지 알려주지 않아서, 데이터가 도착하는 순간
 * 레이아웃이 통째로 나타난다. 스켈레톤은 최종 레이아웃과 같은 자리를 미리 차지해
 * 그 급격한 전환을 없앤다.
 *
 * 색은 surfaceVariant 를 쓰지 않는다. #F4F7F9 는 배경 #FBFCFE 대비 1.048 로 사실상
 * 보이지 않아서, 스켈레톤이 보이지 않는 스켈레톤이 된다. GDSkeletonLight/Dark 를
 * 알파 0.7~1.0 으로 왕복시켜 라이트 1.17~1.26, 다크 1.27~1.45 구간을 유지한다.
 *
 * 애니메이션은 알파 왕복 정도로만 둔다. 강한 shimmer 는 시선을 끌어 오히려 대기
 * 시간을 길게 느끼게 하고, 전정기관 민감 사용자에게 부담이 된다.
 */
@Composable
fun GDSkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.extraSmall
) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeleton-alpha"
    )
    Box(
        modifier = modifier
            .height(height)
            .background(skeletonColor().copy(alpha = alpha), shape)
    )
}

/**
 * 쿠폰 목록 초기 로딩 스켈레톤.
 *
 * 실제 RoomDashboard 의 구조(현황 카드 -> 목록 제목 -> 썸네일 + 2줄 텍스트 + 배지 행)를
 * 그대로 흉내낸다. 구조가 어긋나면 스켈레톤의 존재 이유인 "전환의 매끄러움" 이 사라진다.
 *
 * 스크린리더에는 개별 조각을 노출하지 않고 컨테이너 하나로만 안내한다. 의미 없는
 * 회색 상자 여러 개를 순차 탐색하게 만들면 스피너보다 나쁘다.
 */
@Composable
fun CouponListSkeleton(modifier: Modifier = Modifier, rowCount: Int = 4) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "쿠폰 목록을 불러오는 중입니다"
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GDSkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 18.dp)
                GDSkeletonBlock(modifier = Modifier.fillMaxWidth(0.8f), height = 14.dp)
            }
        }
        GDSkeletonBlock(modifier = Modifier.fillMaxWidth(0.3f), height = 18.dp)
        repeat(rowCount) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GDSkeletonBlock(
                        modifier = Modifier.width(56.dp),
                        height = 56.dp,
                        shape = MaterialTheme.shapes.small
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        GDSkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f), height = 16.dp)
                        GDSkeletonBlock(modifier = Modifier.fillMaxWidth(0.85f), height = 13.dp)
                    }
                    GDSkeletonBlock(
                        modifier = Modifier.width(52.dp),
                        height = 24.dp,
                        shape = MaterialTheme.shapes.small
                    )
                }
            }
        }
    }
}
