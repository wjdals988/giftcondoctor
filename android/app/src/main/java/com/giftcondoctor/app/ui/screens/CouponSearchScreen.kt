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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.couponDdayLabel
import com.giftcondoctor.app.core.expiryUrgency
import com.giftcondoctor.app.core.seoulToday
import com.giftcondoctor.app.data.model.CouponSearchHit
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.components.GDBottomBar
import com.giftcondoctor.app.ui.components.GDDestination
import com.giftcondoctor.app.ui.components.GDExpiryBadge
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.gdHeading
import com.giftcondoctor.app.ui.viewmodel.CouponSearchViewModel
import java.time.LocalDate

/**
 * 방을 가로지르는 쿠폰 검색.
 *
 * 지금까지 검색창은 방 안에만 있었다. 방이 3개면 "커피 쿠폰 있었나?" 를 확인하려고
 * 방 3개를 각각 열어 각각 검색해야 했다. 계산대 앞에서 할 수 있는 동작이 아니다.
 *
 * 방 안 검색과 달리 서버 질의다. 입력할 때마다 던지면 그대로 읽기 비용이 되므로
 * 확정(키보드 검색 키 또는 검색 버튼) 시점에만 보낸다. 자동완성처럼 느껴지지 않는
 * 대신, 무엇을 찾는지 사용자가 다 적고 나서 한 번에 답한다.
 */
@Composable
fun CouponSearchScreen(
    onSelectDestination: (GDDestination) -> Unit,
    onOpenCoupon: (String, String) -> Unit,
    viewModel: CouponSearchViewModel = viewModel()
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results by viewModel.results.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    // 이 화면의 목적은 검색 하나뿐이다. 들어오자마자 입력할 수 있어야 한다.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val submit = {
        keyboard?.hide()
        viewModel.search(query)
    }

    GDScaffold(
        title = "쿠폰 검색",
        bottomBar = { GDBottomBar(current = GDDestination.Search, onSelect = onSelectDestination) }
    ) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("쿠폰 이름 또는 브랜드") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            viewModel.clear()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "검색어 지우기")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("cross-room-search-field")
            )
            InlineMessage(message)
            if (searching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("cross-room-search-progress"))
            }

            val current = results
            when {
                current == null && query.trim().length < CouponSearchViewModel.MIN_SEARCH_QUERY_LENGTH -> {
                    // 두 글자를 채우는 중인 상태는 실수가 아니다. 오류가 아니라 안내로 둔다.
                    Text(
                        "두 글자 이상 입력하면 모든 쿠폰방에서 한 번에 찾아요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                current == null -> {
                    Text(
                        "검색 키를 누르면 찾기 시작해요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                current.coupons.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        EmptyState(
                            title = "\"${current.query}\" 결과가 없어요",
                            message = "쿠폰방 ${current.roomCount}개를 모두 찾아봤어요. 다른 이름이나 브랜드로 검색해 보세요.",
                            icon = Icons.Default.Search
                        )
                    }
                }
                else -> {
                    Text(
                        "쿠폰방 ${current.roomCount}개에서 ${current.coupons.size}개 찾았어요",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.gdHeading()
                    )
                    // 잘린 사실을 숨기면 "그런 쿠폰은 없다" 는 정반대의 결론을 만든다.
                    if (current.truncated) {
                        Text(
                            "일부만 표시했어요. 더 좁은 검색어를 쓰면 정확해집니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f).testTag("cross-room-search-results"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(current.coupons, key = { "${it.roomId}/${it.couponId}" }) { hit ->
                            CouponSearchHitCard(hit = hit, onOpen = { onOpenCoupon(hit.roomId, hit.couponId) })
                        }
                    }
                }
            }
        }
    }
}

/**
 * 결과 한 건.
 *
 * 방 안 목록과 달리 **어느 방인지**를 반드시 함께 보인다. 방 밖에서 찾았으니 어디에
 * 있는지를 알려줘야 사용자가 다음 행동(가족에게 말할지, 그냥 쓸지)을 정할 수 있다.
 */
@Composable
internal fun CouponSearchHitCard(hit: CouponSearchHit, onOpen: () -> Unit) {
    val today = remember { seoulToday() }
    val expires = remember(hit.expiresLocalDate) {
        runCatching { LocalDate.parse(hit.expiresLocalDate) }.getOrNull()
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .testTag("cross-room-search-item-${hit.couponId}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(hit.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    listOf(hit.roomName, hit.brand).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            if (expires != null) {
                GDExpiryBadge(
                    urgency = expiryUrgency(hit.status, today, expires),
                    text = couponDdayLabel(hit.status, today, expires)
                )
            }
        }
    }
}
