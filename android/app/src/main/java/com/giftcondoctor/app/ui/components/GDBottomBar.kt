package com.giftcondoctor.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/**
 * 최상위 목적지.
 *
 * `CONCEPT_REVIEW` 3절은 탭 도입을 미뤄 두라고 했다. 당시 최상위 목적지가 사실상
 * "방 목록" 하나였고, 목적지가 없는 상태에서 탭부터 넣으면 빈 서랍을 만드는
 * 셈이기 때문이다. 검색과 즐겨찾기가 생겨 이제 셋이 됐다.
 */
enum class GDDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    Rooms("rooms", "쿠폰방", Icons.Default.Home),
    Search("coupons/search", "검색", Icons.Default.Search),
    Favorites("coupons/favorites", "즐겨찾기", Icons.Default.Star)
}

/**
 * 하단 탭.
 *
 * 세 화면이 각자 GDScaffold 를 쓰므로 탭도 화면마다 넘긴다. NavHost 바깥에 하나만
 * 두는 대안은 각 화면의 인셋 처리와 어긋나 스크롤 끝이 탭에 가려진다.
 */
@Composable
fun GDBottomBar(current: GDDestination, onSelect: (GDDestination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        GDDestination.entries.forEach { destination ->
            val selected = destination == current
            NavigationBarItem(
                selected = selected,
                onClick = { if (!selected) onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label) },
                modifier = Modifier
                    .testTag("tab-${destination.name.lowercase()}")
                    // 선택 상태를 색으로만 알리면 화면을 못 보는 사용자에게는
                    // 전달되지 않는다.
                    .semantics { stateDescription = if (selected) "선택됨" else "선택 안 됨" }
            )
        }
    }
}
