package com.giftcondoctor.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.components.rememberNotificationPermissionState
import com.giftcondoctor.app.ui.viewmodel.MemberListViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomDetailViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomListViewModel
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel
import com.giftcondoctor.app.ui.viewmodel.SettingsViewModel
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RoomListScreen(
    sessionViewModel: SessionViewModel,
    onOpenRoom: (String) -> Unit,
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onOpenNotifications: () -> Unit,
    viewModel: RoomListViewModel = viewModel()
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()

    GDScaffold(
        title = "쿠폰방",
        actions = {
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = "알림 설정")
            }
            IconButton(onClick = { sessionViewModel.signOut() }) {
                Icon(Icons.Default.Logout, contentDescription = "로그아웃")
            }
        }
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateRoom, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("방 만들기", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onJoinRoom, modifier = Modifier.weight(1f)) {
                    Text("초대코드 입장")
                }
            }
            when (val state = rooms) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(state.message)
                is UiState.Success -> RoomList(state.data, onOpenRoom)
            }
        }
    }
}

@Composable
private fun RoomList(rooms: List<RoomMembership>, onOpenRoom: (String) -> Unit) {
    if (rooms.isEmpty()) {
        EmptyState("아직 참여 중인 쿠폰방이 없습니다.")
        return
    }

    LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rooms, key = { it.roomId }) { room ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpenRoom(room.roomId) }) {
                ListItem(
                    headlineContent = { Text(room.name) },
                    supportingContent = { Text(if (room.role == "owner") "방장" else "멤버") }
                )
            }
        }
    }
}

@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: RoomListViewModel = viewModel()
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }

    GDScaffold(title = "방 만들기", onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("방 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            InlineMessage(message)
            Button(
                onClick = { viewModel.createRoom(name) { onCreated(it) } },
                enabled = !busy && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("생성하기")
            }
        }
    }
}

@Composable
fun JoinRoomScreen(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    viewModel: RoomListViewModel = viewModel()
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }

    GDScaffold(title = "초대코드 입장", onBack = onBack) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase() },
                label = { Text("초대코드") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            InlineMessage(message)
            Button(
                onClick = { viewModel.joinRoom(code) { onJoined(it) } },
                enabled = !busy && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) {
                Text("입장하기")
            }
        }
    }
}

@Composable
fun RoomDetailScreen(
    roomId: String,
    onBack: () -> Unit,
    onAddCoupon: () -> Unit,
    onOpenCoupon: (String) -> Unit,
    onOpenMembers: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RoomDetailViewModel = viewModel(key = "room-detail-$roomId")
) {
    LaunchedEffect(roomId) { viewModel.start(roomId) }
    val room by viewModel.room.collectAsStateWithLifecycle()
    val coupons by viewModel.coupons.collectAsStateWithLifecycle()
    val title = (room as? UiState.Success<Room>)?.data?.name ?: "쿠폰방"

    GDScaffold(
        title = title,
        onBack = onBack,
        actions = {
            IconButton(onClick = onOpenMembers) {
                Icon(Icons.Default.Group, contentDescription = "멤버")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "방 설정")
            }
            IconButton(onClick = onAddCoupon) {
                Icon(Icons.Default.Add, contentDescription = "쿠폰 추가")
            }
        }
    ) { modifier ->
        when (val state = coupons) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> CouponList(state.data, onOpenCoupon, modifier)
        }
    }
}

@Composable
private fun CouponList(coupons: List<Coupon>, onOpenCoupon: (String) -> Unit, modifier: Modifier) {
    if (coupons.isEmpty()) {
        EmptyState("아직 등록된 쿠폰이 없습니다.")
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(coupons, key = { it.id }) { coupon ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onOpenCoupon(coupon.id) }) {
                ListItem(
                    headlineContent = { Text(coupon.title) },
                    supportingContent = {
                        Text("${coupon.brand.ifBlank { "브랜드 없음" }} · ${coupon.expiresLocalDate} · ${statusLabel(coupon.status)}")
                    },
                    trailingContent = {
                        if (coupon.visibility == "private") Text("비공개", color = MaterialTheme.colorScheme.primary)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomSettingsScreen(
    roomId: String,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    roomViewModel: RoomDetailViewModel = viewModel(key = "room-settings-room-$roomId"),
    settingsViewModel: SettingsViewModel = viewModel(key = "room-settings-$roomId")
) {
    LaunchedEffect(roomId) { roomViewModel.start(roomId) }
    val roomState by roomViewModel.room.collectAsStateWithLifecycle()
    val message by settingsViewModel.message.collectAsStateWithLifecycle()
    val notificationPermission = rememberNotificationPermissionState()
    val canUsePush = notificationPermission.granted || !notificationPermission.runtimeRequired
    var roomMode by remember { mutableStateOf(NotificationMode.Basic) }
    var memberEnabled by remember { mutableStateOf(true) }
    var memberMode by remember { mutableStateOf(NotificationMode.Basic) }

    GDScaffold(title = "방 설정", onBack = onBack) { modifier ->
        when (val state = roomState) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> {
                val room = state.data
                LaunchedEffect(room.defaultNotificationMode) {
                    roomMode = NotificationMode.fromWire(room.defaultNotificationMode)
                }
                Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(room.name, style = MaterialTheme.typography.headlineSmall)
                    Text("초대코드: ${room.inviteCode ?: "없음"}")
                    Text("만료: ${room.inviteExpiresAt?.let { inviteFormatter.format(it) } ?: "없음"}")
                    Button(onClick = { settingsViewModel.regenerateInvite(roomId) }, modifier = Modifier.fillMaxWidth()) {
                        Text("초대코드 재발급")
                    }
                    HorizontalDivider()
                    Text("방 기본 알림")
                    ModeChips(selected = roomMode, onSelected = { roomMode = it })
                    Button(onClick = { settingsViewModel.updateRoom(roomId, roomMode) }, modifier = Modifier.fillMaxWidth()) {
                        Text("방 기본값 저장")
                    }
                    HorizontalDivider()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("이 방 알림 받기")
                        Switch(
                            checked = memberEnabled && canUsePush,
                            enabled = canUsePush,
                            onCheckedChange = { memberEnabled = it }
                        )
                    }
                    NotificationPermissionStatus(notificationPermission)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NotificationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = memberMode == mode,
                                onClick = { memberMode = mode },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                    Button(onClick = { settingsViewModel.updateMember(roomId, memberEnabled && canUsePush, memberMode) }, modifier = Modifier.fillMaxWidth()) {
                        Text("내 방 알림 저장")
                    }
                    OutlinedButton(onClick = { settingsViewModel.leaveRoom(roomId, onLeft) }, modifier = Modifier.fillMaxWidth()) {
                        Text("방 나가기")
                    }
                    InlineMessage(message)
                }
            }
        }
    }
}

@Composable
fun MemberListScreen(
    roomId: String,
    currentUid: String?,
    onBack: () -> Unit,
    viewModel: MemberListViewModel = viewModel(key = "members-$roomId")
) {
    LaunchedEffect(roomId) { viewModel.start(roomId) }
    val members by viewModel.members.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    GDScaffold(title = "멤버", onBack = onBack) { modifier ->
        when (val state = members) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> {
                val isOwner = state.data.any { it.uid == currentUid && it.role == "owner" }
                Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
                    InlineMessage(message)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.data, key = { it.uid }) { member ->
                            MemberRow(
                                member = member,
                                canRemove = isOwner && member.role != "owner",
                                onRemove = { viewModel.removeMember(roomId, member.uid) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemberRow(member: RoomMember, canRemove: Boolean, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(member.displayName) },
            supportingContent = {
                Text(if (member.role == "owner") "방장" else "멤버")
            },
            trailingContent = {
                if (canRemove) {
                    OutlinedButton(onClick = onRemove) {
                        Text("제거")
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeChips(selected: NotificationMode, onSelected: (NotificationMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotificationMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text("${mode.label} ${mode.days}") }
            )
        }
    }
}

private val inviteFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(AppConstants.SEOUL_TIME_ZONE))
