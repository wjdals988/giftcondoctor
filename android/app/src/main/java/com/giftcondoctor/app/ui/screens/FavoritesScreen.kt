package com.giftcondoctor.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.couponDdayLabel
import com.giftcondoctor.app.core.expiryUrgency
import com.giftcondoctor.app.core.seoulToday
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDBottomBar
import com.giftcondoctor.app.ui.components.GDDestination
import com.giftcondoctor.app.ui.components.GDExpiryBadge
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.components.gdHeading
import com.giftcondoctor.app.ui.viewmodel.FavoriteCoupon
import com.giftcondoctor.app.ui.viewmodel.FavoritesViewModel

/**
 * 방을 가리지 않는 즐겨찾기 목록.
 *
 * 즐겨찾기를 방 단위로만 두면 방이 여러 개일 때 즐겨찾기도 방마다 흩어진다.
 * 즐겨찾기를 만든 이유가 "자주 쓰는 쿠폰을 빨리 꺼내는 것" 인데, 그러려면 어느
 * 방에 넣었는지 기억하지 않아도 돼야 한다.
 */
@Composable
fun FavoritesScreen(
    onOpenCoupon: (String, String) -> Unit,
    onSelectDestination: (GDDestination) -> Unit,
    viewModel: FavoritesViewModel = viewModel()
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.coupons.collectAsStateWithLifecycle()
    val missingCount by viewModel.missingCount.collectAsStateWithLifecycle()

    GDScaffold(
        title = "즐겨찾기",
        bottomBar = { GDBottomBar(current = GDDestination.Favorites, onSelect = onSelectDestination) }
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (val current = state) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(current.message)
                is UiState.Success -> {
                    if (current.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            EmptyState(
                                title = "즐겨찾기가 비어 있어요",
                                message = "쿠폰 목록에서 별표를 누르면 여기에 모입니다. 자주 쓰는 쿠폰을 방마다 찾아다니지 않아도 돼요.",
                                icon = Icons.Default.Star
                            )
                        }
                    } else {
                        // 참조는 있는데 쿠폰을 읽지 못한 건수를 숨기면
                        // "즐겨찾기가 사라졌다" 로 보인다.
                        if (missingCount > 0) {
                            Text(
                                "${missingCount}개는 표시할 수 없어요. 쿠폰이 삭제됐거나 방에서 나갔을 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().testTag("favorites-list"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
                        ) {
                            items(
                                current.data,
                                key = { "${it.coupon.roomId}/${it.coupon.id}" }
                            ) { item ->
                                FavoriteCouponCard(
                                    item = item,
                                    onOpen = { onOpenCoupon(item.coupon.roomId, item.coupon.id) },
                                    onRemove = { viewModel.removeFavorite(item.coupon.roomId, item.coupon.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FavoriteCouponCard(
    item: FavoriteCoupon,
    onOpen: () -> Unit,
    onRemove: () -> Unit
) {
    val today = remember { seoulToday() }
    val coupon = item.coupon
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("favorite-item-${coupon.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(coupon.title, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.gdHeading())
                Text(
                    listOf(item.roomName, coupon.brand).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            GDExpiryBadge(
                urgency = expiryUrgency(coupon.status, today, coupon.expiresLocalDate),
                text = couponDdayLabel(coupon.status, today, coupon.expiresLocalDate)
            )
            // 해제를 여기 두는 이유는, 끝난 쿠폰을 목록에서 지우지 않기 때문이다.
            // 다 쓴 즐겨찾기를 정리할 곳이 없으면 목록이 계속 길어진다.
            IconButton(onClick = onRemove, modifier = Modifier.testTag("favorite-remove-${coupon.id}")) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "즐겨찾기 해제",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
