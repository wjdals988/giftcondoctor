package com.giftcondoctor.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.CouponListFilter
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.SharedImageImportState
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.daysBeforeExpiry
import com.giftcondoctor.app.core.filterAndSortCoupons
import com.giftcondoctor.app.core.seoulToday
import com.giftcondoctor.app.core.shouldLoadNextPage
import com.giftcondoctor.app.core.couponDdayLabel
import com.giftcondoctor.app.core.expiryDateLabel
import com.giftcondoctor.app.core.expiryUrgency
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.data.CouponImageLoader
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.DeletedCoupon
import com.giftcondoctor.app.data.model.PublicRoom
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDBadge
import com.giftcondoctor.app.ui.components.GDBottomBar
import com.giftcondoctor.app.ui.components.GDDestination
import com.giftcondoctor.app.ui.components.GDExpiryBadge
import com.giftcondoctor.app.ui.components.GDInfoBanner
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.GDStatCard
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.components.ReminderTimeBanner
import com.giftcondoctor.app.ui.components.CouponListSkeleton
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.rememberNotificationPermissionState
import com.giftcondoctor.app.ui.components.gdHeading
import com.giftcondoctor.app.ui.viewmodel.MemberListViewModel
import com.giftcondoctor.app.ui.viewmodel.CouponTrashViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomDetailViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomListViewModel
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel
import com.giftcondoctor.app.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.giftcondoctor.app.core.couponListSupportingText
import com.giftcondoctor.app.ui.theme.LocalGDDarkTheme
import com.giftcondoctor.app.core.shouldShowExpiryBadge
import com.giftcondoctor.app.ui.components.AppVersionText
import androidx.compose.ui.text.style.TextAlign
import com.giftcondoctor.app.data.model.ExpiringCoupons
import com.giftcondoctor.app.data.RecentCouponShortcuts
import com.giftcondoctor.app.core.inviteShareMessage
import com.giftcondoctor.app.core.formatInviteCodeForDisplay
import com.giftcondoctor.app.core.isInviteCodeUsable
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import android.content.Intent

@Composable
fun RoomListScreen(
    sessionViewModel: SessionViewModel,
    onOpenRoom: (String) -> Unit,
    onOpenCoupon: (String, String) -> Unit = { _, _ -> },
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onSelectDestination: (GDDestination) -> Unit,
    onOpenNotifications: () -> Unit,
    sharedImageImport: SharedImageImportState = SharedImageImportState.None,
    onDismissSharedImage: () -> Unit = {},
    viewModel: RoomListViewModel = viewModel()
) {
    val rooms by viewModel.rooms.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val notificationPermission = rememberNotificationPermissionState()
    var creatingPersonalRoom by rememberSaveable { mutableStateOf(false) }
    // 생성이 끝나면 화면이 방으로 이동하지만, 실패하면 이 화면에 남는다.
    // busy 가 내려갔는데 아직 이동하지 않았다면 다시 누를 수 있게 되돌린다.
    LaunchedEffect(busy) { if (!busy) creatingPersonalRoom = false }
    val expiringSoon by viewModel.expiringSoon.collectAsStateWithLifecycle()
    // 방 목록은 실시간이지만 이 요약은 서버 집계라 스냅샷이다. 화면에 들어올 때 받는다.
    LaunchedEffect(Unit) { viewModel.refreshExpiringSoon() }
    val onStartPersonalRoom = {
        creatingPersonalRoom = true
        viewModel.createPersonalRoom(onOpenRoom)
    }
    val joinedTestRoom = when (val state = rooms) {
        is UiState.Success -> state.data.any { it.roomId == AppConstants.PUSH_TEST_ROOM_ID }
        else -> false
    }
    val roomSelectionEnabled = sharedImageImport !is SharedImageImportState.Copying
    var showLogoutDialog by remember { mutableStateOf(false) }
    val logoutContext = LocalContext.current

    GDScaffold(
        title = "내 쿠폰방",
        bottomBar = { GDBottomBar(current = GDDestination.Rooms, onSelect = onSelectDestination) },
        actions = {
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = "알림 설정")
            }
            IconButton(onClick = { showLogoutDialog = true }) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "로그아웃")
            }
        }
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InlineMessage(message)
            SharedImageImportBanner(sharedImageImport, onDismissSharedImage)
            when (val state = rooms) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(state.message)
                is UiState.Success -> {
                    if (state.data.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            // 첫 화면의 주인공을 "방" 에서 "쿠폰" 으로 바꾼다.
                            // 첫 사용자의 필요는 혼자 저장하고 알림 받는 것이고,
                            // 공유는 나중에 생긴다. 방 이름을 정하는 결정을 없앤다.
                            EmptyState(
                                title = "첫 쿠폰을 저장해 보세요",
                                message = "혼자 모아두고 만료 전에 알림을 받을 수 있어요. 가족·친구와 함께 쓰려면 방을 만들거나 초대코드로 입장하세요.",
                                icon = Icons.Default.CardGiftcard,
                                primaryActionLabel = if (creatingPersonalRoom) "만드는 중..." else "내 쿠폰 바로 시작",
                                onPrimaryAction = if (creatingPersonalRoom) null else onStartPersonalRoom,
                                secondaryActionLabel = "초대코드로 입장",
                                onSecondaryAction = onJoinRoom,
                                tertiaryActionLabel = "함께 쓸 방 만들기",
                                onTertiaryAction = onCreateRoom
                            )
                        }
                    } else {
                        expiringSoon?.let { summary ->
                            ExpiringSoonCard(summary = summary, onOpenCoupon = onOpenCoupon)
                        }
                        if (notificationPermission.runtimeRequired && !notificationPermission.granted) {
                            NotificationPermissionStatus(notificationPermission)
                        } else {
                            GDInfoBanner(
                                title = "만료 알림을 켜두면 놓치지 않아요",
                                body = "매일 오전 9시에 만료 예정 쿠폰만 간결하게 알려드립니다.",
                                icon = Icons.Default.Notifications
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onCreateRoom, enabled = roomSelectionEnabled, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Text("방 만들기", modifier = Modifier.padding(start = 6.dp))
                            }
                            OutlinedButton(onClick = onJoinRoom, enabled = roomSelectionEnabled, modifier = Modifier.weight(1f)) {
                                Text("방 입장")
                            }
                        }
                        if (joinedTestRoom) {
                            Text(
                                "푸시 테스트방 참여 중 · 매일 오전 9시",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        RoomList(state.data, onOpenRoom, Modifier.weight(1f), roomSelectionEnabled)
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("로그아웃 진행하겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        // 다음 사용자에게 이전 계정의 쿠폰 이름이 바로가기로 남으면 안 된다.
                        RecentCouponShortcuts.clear(logoutContext)
                        sessionViewModel.signOut()
                    }
                ) {
                    Text("예")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("아니오")
                }
            }
        )
    }
}

@Composable
internal fun SharedImageImportBanner(
    state: SharedImageImportState,
    onDismiss: () -> Unit,
    canSelectRoom: Boolean = true
) {
    when (state) {
        SharedImageImportState.None -> Unit
        is SharedImageImportState.Copying -> Card(
            modifier = Modifier.fillMaxWidth().testTag("shared-image-copying"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "공유 이미지 ${state.completed}/${state.total}장 준비 중",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "준비가 끝나면 등록할 쿠폰방을 선택할 수 있어요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("cancel-shared-image-copy")) {
                    Text("가져오기 취소")
                }
            }
        }
        is SharedImageImportState.Ready -> Card(
            modifier = Modifier.fillMaxWidth().testTag("shared-image-room-picker"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (canSelectRoom) {
                        "${state.uris.size}장을 등록할 쿠폰방을 선택하세요"
                    } else {
                        "공유 이미지 ${state.uris.size}장을 안전하게 보관했어요"
                    },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (canSelectRoom) {
                        "방을 고른 뒤 한 장씩 내용을 확인하고 저장합니다."
                    } else {
                        "로그인하면 등록할 쿠폰방을 선택할 수 있어요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-shared-image")) {
                    Text("공유 등록 취소")
                }
            }
        }
        is SharedImageImportState.Error -> Card(
            modifier = Modifier.fillMaxWidth().testTag("shared-image-error"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("이미지를 가져오지 못했어요", fontWeight = FontWeight.SemiBold)
                Text(state.message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("dismiss-shared-image-error")) {
                    Text("확인")
                }
            }
        }
    }
}

@Composable
private fun RoomList(
    rooms: List<RoomMembership>,
    onOpenRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rooms, key = { it.roomId }) { room ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onOpenRoom(room.roomId) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(room.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            when {
                                room.roomId == AppConstants.PUSH_TEST_ROOM_ID -> "매일 오전 9시 테스트 푸시"
                                room.role == "owner" -> "내가 만든 방"
                                else -> "참여 중"
                            }
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    trailingContent = {
                        GDBadge(if (room.role == "owner") "방장" else "멤버")
                    }
                )
            }
        }
        // 로그인 이후에는 버전을 볼 곳이 알림 설정 아래 "앱 정보" 뿐이었다.
        // 문의·버그 신고에서 가장 먼저 필요한 값이므로 목록 끝에 상시 노출한다.
        item(key = "app-version-footer") {
            AppVersionText(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp)
                    .testTag("room-list-app-version"),
                textAlign = TextAlign.Center
            )
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
    var publicRoom by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    GDScaffold(title = "방 만들기", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GDInfoBanner(
                title = "방을 만들고 쿠폰을 함께 관리하세요",
                body = "공개 방은 목록에 보이고, 비공개로 만들면 초대코드로만 입장할 수 있습니다.",
                icon = Icons.Default.Group
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("방 이름") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("공개 방으로 표시")
                Switch(checked = publicRoom, onCheckedChange = { publicRoom = it })
            }
            Text(
                "공개 방은 로그인한 사용자가 목록에서 볼 수 있습니다. 입장하려면 아래 비밀번호가 필요합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (publicRoom) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("방 비밀번호") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation()
                )
                Text(
                    "비밀번호는 서버에 원문으로 저장하지 않고 해시로만 저장합니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            InlineMessage(message)
            Button(
                onClick = { viewModel.createRoom(name, publicRoom, password) { onCreated(it) } },
                enabled = !busy && name.isNotBlank() && (!publicRoom || password.length >= 4),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                if (busy) ButtonProgressIndicator()
                Text(if (busy) "방 만드는 중..." else "방 만들기")
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
    val publicRooms by viewModel.publicRooms.collectAsStateWithLifecycle()
    val publicRoomsRefreshing by viewModel.publicRoomsRefreshing.collectAsStateWithLifecycle()
    val publicRoomsMessage by viewModel.publicRoomsMessage.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf<PublicRoom?>(null) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshPublicRooms() }

    GDScaffold(title = "방 입장", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("초대코드로 입장", style = MaterialTheme.typography.titleMedium, modifier = Modifier.gdHeading())
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
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small
            ) {
                if (busy) ButtonProgressIndicator()
                Text(if (busy) "입장 중..." else "입장하기")
            }
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("공개 방", style = MaterialTheme.typography.titleMedium, modifier = Modifier.gdHeading())
                TextButton(
                    onClick = { viewModel.refreshPublicRooms() },
                    enabled = !publicRoomsRefreshing,
                    modifier = Modifier.testTag("refresh-public-rooms")
                ) {
                    if (publicRoomsRefreshing) {
                        ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (publicRoomsRefreshing) "갱신 중..." else "새로고침")
                }
            }
            Text(
                "공개 방은 목록에서 선택한 뒤 방 비밀번호를 입력해 입장합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            publicRoomsMessage?.let { message ->
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("public-rooms-refresh-error")
                )
            }
            when (val state = publicRooms) {
                UiState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("공개 방 목록을 불러오는 중입니다.")
                }
                is UiState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> PublicRoomList(
                    rooms = state.data,
                    onSelect = {
                        if (it.alreadyJoined) onJoined(it.roomId) else {
                            selectedRoom = it
                            password = ""
                        }
                    }
                )
            }
        }
    }

    selectedRoom?.let { room ->
        AlertDialog(
            onDismissRequest = { selectedRoom = null },
            title = { Text(room.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 공개 방에 입장하려면 비밀번호가 필요합니다.")
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("방 비밀번호") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && password.isNotBlank(),
                    onClick = {
                        viewModel.joinPublicRoom(room.roomId, password) {
                            selectedRoom = null
                            onJoined(it)
                        }
                    }
                ) {
                    if (busy) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(if (busy) "입장 중..." else "입장")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRoom = null }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun PublicRoomList(rooms: List<PublicRoom>, onSelect: (PublicRoom) -> Unit) {
    if (rooms.isEmpty()) {
        Text("아직 공개 방이 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rooms, key = { it.roomId }) { room ->
            Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(room) }) {
                ListItem(
                    headlineContent = { Text(room.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text("멤버 ${room.memberCount}명" + if (room.alreadyJoined) " · 참여 중" else "")
                    },
                    trailingContent = {
                        GDBadge(if (room.alreadyJoined) "참여 중" else "입장")
                    }
                )
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
    deletedCouponFeedback: DeletedCouponFeedback? = null,
    onDeletedFeedbackConsumed: () -> Unit = {},
    viewModel: RoomDetailViewModel = viewModel(key = "room-detail-$roomId")
) {
    LaunchedEffect(roomId) { viewModel.start(roomId) }
    val room by viewModel.room.collectAsStateWithLifecycle()
    val coupons by viewModel.coupons.collectAsStateWithLifecycle()
    val favoriteCouponIds by viewModel.favoriteCouponIds.collectAsStateWithLifecycle()
    val title = (room as? UiState.Success<Room>)?.data?.name ?: "쿠폰방"
    val roomData = (room as? UiState.Success<Room>)?.data
    val isOwner = roomData?.ownerUid == viewModel.currentUid
    val snackbarHostState = remember { SnackbarHostState() }

    CouponDeletedFeedbackEffect(
        feedback = deletedCouponFeedback,
        snackbarHostState = snackbarHostState,
        onConsumed = onDeletedFeedbackConsumed,
        onUndo = { couponId -> viewModel.restoreDeletedCoupon(roomId, couponId) }
    )

    GDScaffold(
        title = title,
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (roomId != AppConstants.PUSH_TEST_ROOM_ID) {
                ExtendedFloatingActionButton(
                    onClick = onAddCoupon,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("쿠폰 등록") }
                )
            }
        },
        actions = {
            IconButton(onClick = onOpenMembers) {
                Icon(Icons.Default.Group, contentDescription = "멤버")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "방 설정")
            }
        }
    ) { modifier ->
        when {
            coupons.isInitialLoading && coupons.coupons.isEmpty() -> CouponListSkeleton(modifier)
            coupons.coupons.isEmpty() && coupons.errorMessage != null -> ErrorState(
                message = coupons.errorMessage ?: "쿠폰 목록을 불러오지 못했습니다.",
                actionLabel = "다시 시도",
                onAction = viewModel::retryCoupons
            )
            else -> RoomDashboard(
                roomId = roomId,
                coupons = coupons.coupons,
                isOwner = isOwner,
                hasMore = coupons.hasMore,
                isLoadingMore = coupons.isLoadingMore,
                pagingError = coupons.errorMessage,
                onLoadMore = viewModel::loadMoreCoupons,
                onRetry = viewModel::retryCoupons,
                onOpenCoupon = onOpenCoupon,
                onAddCoupon = onAddCoupon,
                favoriteCouponIds = favoriteCouponIds,
                onToggleFavorite = { couponId -> viewModel.toggleFavorite(roomId, couponId) },
                modifier = modifier
            )
        }
    }
}

data class DeletedCouponFeedback(
    val roomId: String,
    val couponId: String,
    val title: String
)

@Composable
internal fun CouponDeletedFeedbackEffect(
    feedback: DeletedCouponFeedback?,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
    onUndo: suspend (String) -> Result<Unit>
) {
    LaunchedEffect(feedback) {
        val deleted = feedback ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "‘${deleted.title}’ 쿠폰을 복구함으로 이동했어요.",
            actionLabel = "실행 취소",
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        onConsumed()
        if (result == SnackbarResult.ActionPerformed) {
            val restored = onUndo(deleted.couponId)
            snackbarHostState.showSnackbar(
                message = restored.fold(
                    onSuccess = { "쿠폰을 목록으로 복원했어요." },
                    onFailure = { it.localizedMessage ?: "쿠폰을 복원하지 못했어요." }
                ),
                withDismissAction = true,
                duration = SnackbarDuration.Short
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalComposeUiApi::class)
@Composable
internal fun RoomDashboard(
    roomId: String,
    coupons: List<Coupon>,
    isOwner: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    pagingError: String?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    onOpenCoupon: (String) -> Unit,
    onAddCoupon: () -> Unit,
    favoriteCouponIds: Set<String> = emptySet(),
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier,
    thumbnailLoader: suspend (String, Coupon, Int, Int) -> Bitmap? = { requestedRoomId, coupon, width, height ->
        CouponImageLoader.load(
            roomId = requestedRoomId,
            couponId = coupon.id,
            imageBlobPath = coupon.imageBlobPath,
            thumbnailBlobPath = coupon.thumbnailBlobPath,
            targetWidth = width,
            targetHeight = height
        )
    }
) {
    val today = seoulToday()
    val listState = rememberLazyListState()
    var overviewExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CouponListFilter.ALL) }
    val actionable = coupons.filter { it.status == "active" || it.status == "reserved" }
    val todayCount = actionable.count { daysBeforeExpiry(today, it.expiresLocalDate) == 0 }
    val soonCount = actionable.count { daysBeforeExpiry(today, it.expiresLocalDate) in 0..3 }
    val activeCount = actionable.count()
    val usedCount = coupons.count { it.status == "used" }
    val visibleCoupons = remember(coupons, searchQuery, selectedFilter, today, favoriteCouponIds) {
        filterAndSortCoupons(coupons, searchQuery, selectedFilter, today, favoriteCouponIds)
    }
    val shouldLoadMore by remember(listState, hasMore, isLoadingMore, pagingError) {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1
            shouldLoadNextPage(
                lastVisibleIndex = lastVisibleIndex,
                totalItems = layout.totalItemsCount,
                hasMore = hasMore && pagingError == null,
                isLoading = isLoadingMore
            )
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .semantics { testTagsAsResourceId = true }
            .testTag("coupon-list")
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            CouponOverviewCard(
                isOwner = isOwner,
                todayCount = todayCount,
                soonCount = soonCount,
                activeCount = activeCount,
                usedCount = usedCount,
                isExpanded = overviewExpanded,
                onToggle = { overviewExpanded = !overviewExpanded }
            )
        }
        if (hasMore) {
            item {
                Text(
                    "빠른 표시를 위해 ${coupons.size}개를 먼저 불러왔어요. 아래로 내리면 계속 이어집니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (roomId == AppConstants.PUSH_TEST_ROOM_ID) {
            item {
                GDInfoBanner(
                    title = "이 방은 푸시 확인 전용이에요",
                    body = "참여 중이면 쿠폰 만료 조건과 별개로 매일 오전 9시에 테스트 알림을 보냅니다.",
                    icon = Icons.Default.Notifications
                )
            }
        }
        item {
            Text("쿠폰 목록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp).gdHeading())
        }
        if (coupons.isNotEmpty()) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("쿠폰 이름·브랜드 검색") },
                    modifier = Modifier.fillMaxWidth().testTag("coupon-search"),
                    singleLine = true
                )
            }
            item {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CouponListFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(couponFilterLabel(filter)) },
                            modifier = Modifier.testTag("coupon-filter-${filter.name.lowercase()}")
                        )
                    }
                }
            }
            item {
                Text(
                    "불러온 ${coupons.size}개 중 ${visibleCoupons.size}개 · " +
                        if (favoriteCouponIds.isEmpty()) "만료 임박순" else "즐겨찾기 먼저, 만료 임박순",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (coupons.isEmpty()) {
            item {
                EmptyState(
                    title = if (roomId == AppConstants.PUSH_TEST_ROOM_ID) "푸시 확인 전용 방이에요" else "첫 쿠폰을 등록해 보세요",
                    message = if (roomId == AppConstants.PUSH_TEST_ROOM_ID) {
                        "쿠폰을 등록하지 않아도 매일 오전 9시에 테스트 알림을 보냅니다."
                    } else {
                        "갤러리에서 이미지를 고르면 이름과 만료일을 자동으로 찾아드려요."
                    },
                    icon = if (roomId == AppConstants.PUSH_TEST_ROOM_ID) Icons.Default.Notifications else Icons.Default.CardGiftcard,
                    primaryActionLabel = if (roomId == AppConstants.PUSH_TEST_ROOM_ID) null else "쿠폰 이미지 선택",
                    onPrimaryAction = if (roomId == AppConstants.PUSH_TEST_ROOM_ID) null else onAddCoupon
                )
            }
        } else if (visibleCoupons.isEmpty()) {
            item {
                EmptyState(
                    title = if (hasMore) "불러온 범위에는 일치하는 쿠폰이 없어요" else "조건에 맞는 쿠폰이 없어요",
                    message = if (hasMore) {
                        "다음 페이지를 이어서 찾고 있어요. 검색어나 상태 필터를 바꿔도 됩니다."
                    } else {
                        "검색어나 상태 필터를 바꾸면 다른 쿠폰을 확인할 수 있습니다."
                    },
                    icon = Icons.Default.CardGiftcard,
                    primaryActionLabel = "검색·필터 초기화",
                    onPrimaryAction = {
                        searchQuery = ""
                        selectedFilter = CouponListFilter.ALL
                    }
                )
            }
        }
        items(
            items = visibleCoupons,
            key = { it.id },
            contentType = { "coupon" }
        ) { coupon ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("coupon-item-${coupon.id}")
                    .clickable { onOpenCoupon(coupon.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                val urgency = expiryUrgency(coupon.status, today, coupon.expiresLocalDate)
                ListItem(
                    headlineContent = { Text(coupon.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(
                            couponListSupportingText(
                                brand = coupon.brand,
                                status = coupon.status,
                                urgency = urgency,
                                expiresLocalDate = coupon.expiresLocalDate
                            )
                        )
                    },
                    leadingContent = { CouponListThumbnail(roomId, coupon, thumbnailLoader) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (shouldShowExpiryBadge(urgency)) {
                                    GDExpiryBadge(
                                        urgency = urgency,
                                        text = couponDdayLabel(coupon.status, today, coupon.expiresLocalDate)
                                    )
                                }
                                if (coupon.visibility == "private") {
                                    Text("비공개", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                            val favorited = coupon.id in favoriteCouponIds
                            IconButton(
                                onClick = { onToggleFavorite(coupon.id) },
                                modifier = Modifier.testTag("coupon-favorite-${coupon.id}")
                            ) {
                                Icon(
                                    if (favorited) Icons.Default.Star else Icons.Default.StarBorder,
                                    // 상태를 아이콘 모양으로만 알리면 화면을 못 보는
                                    // 사용자에게는 전달되지 않는다. 설명에 상태를 담는다.
                                    contentDescription = if (favorited) "즐겨찾기 해제" else "즐겨찾기에 추가",
                                    tint = if (favorited) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                )
            }
        }
        item(key = "paging-footer") {
            CouponPagingFooter(
                hasCoupons = coupons.isNotEmpty(),
                hasMore = hasMore,
                isLoadingMore = isLoadingMore,
                errorMessage = pagingError,
                onLoadMore = onLoadMore,
                onRetry = onRetry
            )
        }
    }
}

private const val LARGE_FONT_SCALE = 1.5f

@Composable
private fun CouponOverviewCard(
    isOwner: Boolean,
    todayCount: Int,
    soonCount: Int,
    activeCount: Int,
    usedCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("coupon-overview")
            .semantics { stateDescription = if (isExpanded) "펼쳐짐" else "접힘" },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        ListItem(
            headlineContent = { Text("쿠폰 현황 · ${if (isOwner) "방장" else "멤버"}") },
            supportingContent = {
                Text("오늘 ${todayCount}개 · 3일 ${soonCount}개 · 사용 가능 ${activeCount}개 · 완료 ${usedCount}개")
            },
            leadingContent = {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "쿠폰 현황 접기" else "쿠폰 현황 펼치기"
                )
            }
        )
        if (isExpanded) CouponOverviewDetails(isOwner, todayCount, soonCount, activeCount, usedCount)
    }
}

@Composable
private fun CouponOverviewDetails(
    isOwner: Boolean,
    todayCount: Int,
    soonCount: Int,
    activeCount: Int,
    usedCount: Int
) {
    HorizontalDivider()
    Column(
        modifier = Modifier.padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            if (isOwner) {
                "멤버 관리와 방 설정을 변경할 수 있습니다."
            } else {
                "방장은 멤버와 방 설정을 관리할 수 있습니다."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ReminderTimeBanner()
        DashboardStats(todayCount, soonCount, activeCount, usedCount)
    }
}

@Composable
private fun DashboardStats(
    todayCount: Int,
    soonCount: Int,
    activeCount: Int,
    usedCount: Int
) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GDStatCard("오늘 만료", "${todayCount}개", Modifier.fillMaxWidth(), MaterialTheme.colorScheme.error)
            GDStatCard("3일 이내", "${soonCount}개", Modifier.fillMaxWidth(), MaterialTheme.colorScheme.tertiary)
            GDStatCard("사용 가능", "${activeCount}개", Modifier.fillMaxWidth())
            GDStatCard("사용 완료", "${usedCount}개", Modifier.fillMaxWidth(), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GDStatCard("오늘 만료", "${todayCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.error)
                GDStatCard("3일 이내", "${soonCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GDStatCard("사용 가능", "${activeCount}개", Modifier.weight(1f))
                GDStatCard("사용 완료", "${usedCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun CouponPagingFooter(
    hasCoupons: Boolean,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    errorMessage: String?,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    itemLabel: String = "쿠폰"
) {
    when {
        errorMessage != null -> Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("목록을 더 불러오지 못했어요") },
                supportingContent = { Text(errorMessage) },
                trailingContent = { TextButton(onClick = onRetry) { Text("다시 시도") } }
            )
        }
        isLoadingMore -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text("다음 ${itemLabel}을 불러오는 중...")
        }
        hasMore -> OutlinedButton(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) {
            Text("다음 ${itemLabel} 불러오기")
        }
        hasCoupons -> Text(
            "모든 ${itemLabel}을 확인했어요.",
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun couponFilterLabel(filter: CouponListFilter): String = when (filter) {
    CouponListFilter.ALL -> "전체"
    CouponListFilter.AVAILABLE -> "사용 가능"
    CouponListFilter.RESERVED -> "예약"
    CouponListFilter.USED -> "사용 완료"
    CouponListFilter.EXPIRED -> "만료"
}

@Composable
private fun CouponListThumbnail(
    roomId: String,
    coupon: Coupon,
    thumbnailLoader: suspend (String, Coupon, Int, Int) -> Bitmap?
) {
    if (coupon.imageBlobPath.isBlank()) {
        CouponCategoryThumbnail(coupon)
        return
    }
    var image by remember(coupon.id, coupon.imageBlobPath, coupon.thumbnailBlobPath) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(coupon.id, coupon.imageBlobPath, coupon.thumbnailBlobPath) { mutableStateOf(false) }
    val targetSize = with(LocalDensity.current) { 56.dp.roundToPx() }

    LaunchedEffect(roomId, coupon.id, coupon.imageBlobPath, coupon.thumbnailBlobPath, targetSize) {
        image = null
        loading = true
        runCatching {
            thumbnailLoader(roomId, coupon, targetSize, targetSize)?.asImageBitmap()
        }.onSuccess {
            image = it
        }
        loading = false
    }

    val thumbnail = image
    if (thumbnail != null) {
        Image(
            bitmap = thumbnail,
            contentDescription = "${coupon.title} 썸네일",
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
    } else {
        CouponCategoryThumbnail(coupon, loading)
    }
}

@Composable
private fun CouponCategoryThumbnail(coupon: Coupon, loading: Boolean = false) {
    val dark = LocalGDDarkTheme.current
    val category = remember(coupon.title, coupon.brand, dark) { couponCategory(coupon.title, coupon.brand, dark) }
    Box(
        modifier = Modifier
            .size(56.dp)
            .background(category.containerColor, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = category.icon,
            contentDescription = category.label,
            tint = category.contentColor,
            modifier = Modifier.size(28.dp)
        )
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = category.contentColor
            )
        }
    }
}

private data class CouponCategory(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color
)

/**
 * 방을 가로지르는 만료 임박 요약.
 *
 * 이 앱에는 방 무관 전체 쿠폰 화면이 없어서, 방이 여러 개면 지금 써야 할 쿠폰을 찾으려
 * 방을 순회해야 했다. 서버는 이미 cron 에서 같은 계산을 하고 있었으므로 그 결과를
 * 방 목록 맨 위에 드러낸다.
 *
 * 항목이 없으면 카드를 그리지 않는다. "0개" 를 위해 자리를 차지할 이유가 없다.
 */
@Composable
internal fun ExpiringSoonCard(
    summary: ExpiringCoupons,
    onOpenCoupon: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (summary.coupons.isEmpty()) return
    val today = remember { seoulToday() }
    Card(
        modifier = modifier.fillMaxWidth().testTag("expiring-soon-card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "${summary.days}일 안에 만료 ${summary.coupons.size}개",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.gdHeading()
            )
            // 상위 3개만 미리 보여준다. 방 목록이 주인공인 화면이므로 요약이
            // 화면을 밀어내면 안 된다.
            summary.coupons.take(3).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCoupon(item.roomId, item.couponId) }
                        .testTag("expiring-soon-item-${item.couponId}")
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            listOf(item.roomName, item.brand).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    val expires = runCatching { LocalDate.parse(item.expiresLocalDate) }.getOrNull()
                    if (expires != null) {
                        GDExpiryBadge(
                            urgency = expiryUrgency("active", today, expires),
                            text = couponDdayLabel("active", today, expires)
                        )
                    }
                }
            }
            if (summary.coupons.size > 3) {
                Text(
                    "외 ${summary.coupons.size - 3}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 잘린 사실을 숨기면 "전부 봤다" 는 오해를 만든다.
            if (summary.truncated) {
                Text(
                    "일부만 표시했어요. 방에 들어가면 전체를 볼 수 있습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun couponCategory(title: String, brand: String, dark: Boolean): CouponCategory {
    val source = "$title $brand".lowercase()
    fun containsAny(keywords: List<String>): Boolean = keywords.any(source::contains)

    return when {
        containsAny(CAFE_KEYWORDS) ->
            CouponCategory("카페", Icons.Default.LocalCafe, if (dark) Color(0xFF0E2F2C) else Color(0xFFE3F7F2), if (dark) Color(0xFF7FD8CE) else Color(0xFF008E85))
        containsAny(FOOD_KEYWORDS) ->
            CouponCategory("음식", Icons.Default.Restaurant, if (dark) Color(0xFF33190C) else Color(0xFFFFF0E7), if (dark) Color(0xFFF0A97F) else Color(0xFFB8491A))
        containsAny(CONVENIENCE_KEYWORDS) ->
            CouponCategory("편의점", Icons.Default.Store, if (dark) Color(0xFF0F2440) else Color(0xFFE8F2FF), if (dark) Color(0xFF9CC6F5) else Color(0xFF2878D8))
        containsAny(CINEMA_KEYWORDS) ->
            CouponCategory("영화", Icons.Default.Theaters, if (dark) Color(0xFF241A3D) else Color(0xFFF1EAFF), if (dark) Color(0xFFC4AEF0) else Color(0xFF7B52CC))
        containsAny(TRAVEL_KEYWORDS) ->
            CouponCategory("여행", Icons.Default.Flight, if (dark) Color(0xFF0A2A3D) else Color(0xFFE7F7FF), if (dark) Color(0xFF8FD0F0) else Color(0xFF0070A3))
        containsAny(SHOPPING_KEYWORDS) ->
            CouponCategory("쇼핑", Icons.Default.ShoppingBag, if (dark) Color(0xFF2E2405) else Color(0xFFFFF7D9), if (dark) Color(0xFFE8C878) else Color(0xFF7A5600))
        else ->
            CouponCategory("쿠폰", Icons.Default.CardGiftcard, if (dark) Color(0xFF0B3B36) else Color(0xFFEAFBF6), if (dark) Color(0xFF8FE3D6) else Color(0xFF00786F))
    }
}

private val CAFE_KEYWORDS = listOf("스타벅스", "커피", "카페", "투썸", "이디야", "메가커피", "컴포즈", "빽다방", "할리스")
private val FOOD_KEYWORDS = listOf("치킨", "피자", "버거", "맥도날드", "버거킹", "롯데리아", "교촌", "bbq", "bhc", "도미노", "배민", "요기요")
private val CONVENIENCE_KEYWORDS = listOf("cu", "gs25", "세븐", "이마트24", "편의점")
private val CINEMA_KEYWORDS = listOf("cgv", "메가박스", "롯데시네마", "영화", "시네마")
private val TRAVEL_KEYWORDS = listOf("항공", "호텔", "여행", "야놀자", "여기어때", "숙박")
private val SHOPPING_KEYWORDS = listOf("쿠팡", "네이버", "백화점", "올리브영", "상품권", "쇼핑", "마트")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoomSettingsScreen(
    roomId: String,
    onBack: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenTrash: () -> Unit,
    onLeft: () -> Unit,
    roomViewModel: RoomDetailViewModel = viewModel(key = "room-settings-room-$roomId"),
    settingsViewModel: SettingsViewModel = viewModel(key = "room-settings-$roomId")
) {
    LaunchedEffect(roomId) { roomViewModel.start(roomId) }
    val roomState by roomViewModel.room.collectAsStateWithLifecycle()
    val message by settingsViewModel.message.collectAsStateWithLifecycle()
    val busy by settingsViewModel.busy.collectAsStateWithLifecycle()
    val busyAction by settingsViewModel.busyAction.collectAsStateWithLifecycle()
    GDScaffold(title = "방 설정", onBack = onBack) { modifier ->
        when (val state = roomState) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> {
                val room = state.data
                val isOwner = room.ownerUid == roomViewModel.currentUid
                RoomSettingsContent(
                    state = RoomSettingsUiState(room, isOwner, busy, busyAction, message),
                    actions = RoomSettingsActions(
                        onRegenerateInvite = { settingsViewModel.regenerateInvite(roomId) },
                        onOpenNotifications = onOpenNotifications,
                        onOpenTrash = onOpenTrash,
                        onLeave = { settingsViewModel.leaveRoom(roomId, onLeft) },
                        onDelete = { settingsViewModel.deleteRoom(roomId, onLeft) }
                    ),
                    modifier = modifier
                )
            }
        }
    }
}

internal data class RoomSettingsUiState(
    val room: Room,
    val isOwner: Boolean,
    val busy: Boolean,
    val busyAction: String?,
    val message: String?
)

internal data class RoomSettingsActions(
    val onRegenerateInvite: () -> Unit,
    val onOpenNotifications: () -> Unit,
    val onOpenTrash: () -> Unit,
    val onLeave: () -> Unit,
    val onDelete: () -> Unit
)

@Composable
internal fun RoomSettingsContent(
    state: RoomSettingsUiState,
    actions: RoomSettingsActions,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RoomSettingsHeader(state.room.name, state.isOwner)
        RoomInviteSection(state, actions.onRegenerateInvite)
        HorizontalDivider()
        GDInfoBanner(
            title = "알림은 개인 설정으로 통합됐어요",
            body = "모든 방의 만료 푸시는 쿠폰방 목록 우측 상단 알림 설정에서 한 번에 관리합니다.",
            icon = Icons.Default.Notifications
        )
        RoomNavigationActions(actions)
        RoomExitActions(state, actions.onLeave, onDeleteClick = { showDeleteDialog = true })
        InlineMessage(state.message)
    }
    if (showDeleteDialog) RoomDeleteDialog(state.busy, onDismiss = { showDeleteDialog = false }) {
        showDeleteDialog = false
        actions.onDelete()
    }
}

@Composable
private fun RoomInviteSection(state: RoomSettingsUiState, onRegenerate: () -> Unit) {
    if (state.room.id == AppConstants.PUSH_TEST_ROOM_ID) {
        GDInfoBanner(
            title = "푸시 확인 전용 방",
            body = "전체 푸시 알림이 켜져 있으면 매일 오전 9시에 테스트 푸시가 옵니다.",
            icon = Icons.Default.Notifications
        )
        return
    }
    val inviteCode = state.room.inviteCode
    val inviteUsable = isInviteCodeUsable(state.room.inviteExpiresAt?.toEpochMilli(), System.currentTimeMillis())
    if (inviteCode.isNullOrBlank()) {
        Text("초대코드가 없습니다.")
    } else if (!inviteUsable) {
        // 서버는 입장 시 만료를 검사해 거부한다. 쓸 수 없는 코드를 공유하면 상대는
        // 입장에 실패하고 보낸 사람도 이유를 모른다. 복사·공유를 아예 제공하지 않고
        // 재발급으로 유도한다.
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.testTag("invite-expired-card")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "초대코드가 만료됐어요",
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    if (state.isOwner) {
                        "지금 공유해도 상대가 입장할 수 없습니다. 아래에서 새 코드를 발급해 주세요."
                    } else {
                        "지금 공유해도 상대가 입장할 수 없습니다. 방장에게 재발급을 요청해 주세요."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    } else {
        val context = LocalContext.current
        val clipboard = LocalClipboardManager.current
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("초대코드", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 표시용으로만 끊어 준다. 복사·공유에는 원본을 쓴다.
                Text(
                    formatInviteCodeForDisplay(inviteCode),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(inviteCode)) },
                        modifier = Modifier.weight(1f).testTag("copy-invite-code"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("코드 복사", modifier = Modifier.padding(start = 6.dp))
                    }
                    Button(
                        onClick = {
                            val message = inviteShareMessage(state.room.name, inviteCode)
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, message)
                            runCatching {
                                context.startActivity(Intent.createChooser(send, "초대코드 공유"))
                            }
                        },
                        modifier = Modifier.weight(1f).testTag("share-invite-code"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("공유", modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
    }
    Text("만료: ${state.room.inviteExpiresAt?.let { inviteFormatter.format(it) } ?: "없음"}")
    if (state.isOwner) {
        Button(
            onClick = onRegenerate,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag("regenerate-invite"),
            shape = MaterialTheme.shapes.small
        ) {
            val loading = state.busyAction == "invite"
            if (loading) ButtonProgressIndicator()
            Text(if (loading) "처리 중..." else "초대코드 재발급")
        }
    }
}

@Composable
private fun RoomNavigationActions(actions: RoomSettingsActions) {
    OutlinedButton(
        onClick = actions.onOpenNotifications,
        modifier = Modifier.fillMaxWidth().testTag("open-notification-settings"),
        shape = MaterialTheme.shapes.small
    ) { Text("전체 알림 설정 열기") }
    OutlinedButton(
        onClick = actions.onOpenTrash,
        modifier = Modifier.fillMaxWidth().testTag("open-trash"),
        shape = MaterialTheme.shapes.small
    ) {
        Icon(Icons.Default.RestoreFromTrash, contentDescription = null)
        Text("최근 삭제한 쿠폰", modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun RoomExitActions(
    state: RoomSettingsUiState,
    onLeave: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(
            onClick = onLeave,
            enabled = !state.busy && !state.isOwner,
            modifier = Modifier.fillMaxWidth().testTag("leave-room"),
            shape = MaterialTheme.shapes.small
        ) {
            val leaving = state.busyAction == "leave"
            if (leaving) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                when {
                    leaving -> "처리 중..."
                    state.isOwner -> "방장은 방 삭제를 사용해 주세요"
                    else -> "방 나가기"
                }
            )
        }
        if (state.isOwner && state.room.id != AppConstants.PUSH_TEST_ROOM_ID) {
            Button(
                onClick = onDeleteClick,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth().testTag("delete-room"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                val deleting = state.busyAction == "deleteRoom"
                if (deleting) ButtonProgressIndicator()
                Icon(Icons.Default.Delete, contentDescription = null)
                Text(if (deleting) "삭제 중..." else "방 삭제", modifier = Modifier.padding(start = 6.dp))
            }
            Text(
                "방을 삭제하면 멤버, 쿠폰, 댓글, 쿠폰 이미지가 함께 삭제됩니다. 이 작업은 되돌릴 수 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
private fun RoomDeleteDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방 삭제") },
        text = { Text("이 방과 등록된 쿠폰, 댓글, 이미지를 모두 삭제할까요? 이 작업은 되돌릴 수 없습니다.") },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
private fun RoomSettingsHeader(roomName: String, isOwner: Boolean) {
    if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(roomName, style = MaterialTheme.typography.headlineSmall)
            GDBadge(if (isOwner) "방장" else "멤버")
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(roomName, style = MaterialTheme.typography.headlineSmall)
            GDBadge(if (isOwner) "방장" else "멤버")
        }
    }
}

@Composable
fun CouponTrashScreen(
    roomId: String,
    onBack: () -> Unit,
    viewModel: CouponTrashViewModel = viewModel(key = "coupon-trash-$roomId")
) {
    LaunchedEffect(roomId) { viewModel.load(roomId) }
    val coupons by viewModel.coupons.collectAsStateWithLifecycle()
    val busyCouponId by viewModel.busyCouponId.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val pagingError by viewModel.pagingError.collectAsStateWithLifecycle()

    GDScaffold(title = "최근 삭제한 쿠폰", onBack = onBack) { modifier ->
        CouponTrashContent(
            coupons = coupons,
            busyCouponId = busyCouponId,
            busyAction = busyAction,
            message = message,
            onRetry = { viewModel.retry(roomId) },
            onRestore = { viewModel.restore(roomId, it) },
            onPermanentlyDelete = { viewModel.permanentlyDelete(roomId, it) },
            onLoadMore = { viewModel.loadMore(roomId) },
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            pagingError = pagingError,
            modifier = modifier
        )
    }
}

@Composable
internal fun CouponTrashContent(
    coupons: UiState<List<DeletedCoupon>>,
    busyCouponId: String?,
    busyAction: String?,
    message: String?,
    onRetry: () -> Unit,
    onRestore: (String) -> Unit,
    onPermanentlyDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    pagingError: String? = null,
    now: Instant = Instant.now()
) {
    var permanentDeleteTarget by remember { mutableStateOf<DeletedCoupon?>(null) }
    val listState = rememberLazyListState()
    val shouldLoadMore by remember(listState, hasMore, isLoadingMore, pagingError) {
        derivedStateOf {
            val layout = listState.layoutInfo
            shouldLoadNextPage(
                lastVisibleIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: -1,
                totalItems = layout.totalItemsCount,
                hasMore = hasMore && pagingError == null,
                isLoading = isLoadingMore
            )
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GDInfoBanner(
            title = "삭제 후 30일 동안 보관해요",
            body = "복원하면 이미지와 댓글도 원래 상태로 돌아옵니다. 기간이 지나면 자동으로 영구 삭제됩니다.",
            icon = Icons.Default.RestoreFromTrash
        )
        InlineMessage(message)
        when (coupons) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(coupons.message, actionLabel = "다시 시도", onAction = onRetry)
            is UiState.Success -> if (
                coupons.data.isEmpty() && !hasMore && !isLoadingMore && pagingError == null
            ) {
                EmptyState(
                    title = "복구함이 비어 있어요",
                    message = "삭제한 쿠폰은 여기에 30일 동안 보관해요.",
                    icon = Icons.Default.RestoreFromTrash
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).testTag("trash-list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(coupons.data, key = { it.couponId }) { coupon ->
                        val busy = busyCouponId != null
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(coupon.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOfNotNull(
                                        coupon.brand.takeIf { it.isNotBlank() },
                                        coupon.expiresLocalDate?.let { "만료 $it" }
                                    ).joinToString(" · ").ifBlank { "브랜드 정보 없음" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "영구 삭제까지 ${trashDaysRemaining(now, coupon.purgeAt)}일",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { onRestore(coupon.couponId) },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        if (busyCouponId == coupon.couponId && busyAction == "restore") ButtonProgressIndicator()
                                        Text(if (busyCouponId == coupon.couponId && busyAction == "restore") "복원 중..." else "복원")
                                    }
                                    OutlinedButton(
                                        onClick = { permanentDeleteTarget = coupon },
                                        enabled = !busy,
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        if (busyCouponId == coupon.couponId && busyAction == "delete") {
                                            ButtonProgressIndicator(color = MaterialTheme.colorScheme.error)
                                        }
                                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                                        Text(if (busyCouponId == coupon.couponId && busyAction == "delete") "삭제 중..." else "영구 삭제")
                                    }
                                }
                            }
                        }
                    }
                    item(key = "trash-paging-footer") {
                        CouponPagingFooter(
                            hasCoupons = coupons.data.isNotEmpty(),
                            hasMore = hasMore,
                            isLoadingMore = isLoadingMore,
                            errorMessage = pagingError,
                            onLoadMore = onLoadMore,
                            onRetry = onRetry,
                            itemLabel = "삭제 쿠폰"
                        )
                    }
                }
            }
        }
    }

    permanentDeleteTarget?.let { coupon ->
        AlertDialog(
            onDismissRequest = { if (busyCouponId == null) permanentDeleteTarget = null },
            title = { Text("영구 삭제할까요?") },
            text = { Text("${coupon.title}의 이미지와 댓글이 삭제되며 더 이상 복원할 수 없습니다.") },
            dismissButton = {
                TextButton(onClick = { permanentDeleteTarget = null }, enabled = busyCouponId == null) {
                    Text("취소")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        permanentDeleteTarget = null
                        onPermanentlyDelete(coupon.couponId)
                    },
                    enabled = busyCouponId == null,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("영구 삭제")
                }
            }
        )
    }
}

internal fun trashDaysRemaining(now: Instant, purgeAt: Instant): Long {
    val remainingSeconds = Duration.between(now, purgeAt).seconds.coerceAtLeast(0)
    return (remainingSeconds + 86_399) / 86_400
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
internal fun MemberRow(member: RoomMember, canRemove: Boolean, onRemove: () -> Unit) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(member.displayName) },
            supportingContent = {
                Text(if (member.role == "owner") "방장" else "멤버")
            },
            trailingContent = {
                if (canRemove) {
                    OutlinedButton(
                        onClick = { showRemoveDialog = true },
                        modifier = Modifier.testTag("remove-member-${member.uid}")
                    ) {
                        Text("제거")
                    }
                }
            }
        )
    }
    if (showRemoveDialog) MemberRemoveDialog(
        memberName = member.displayName,
        onDismiss = { showRemoveDialog = false },
        onConfirm = {
            showRemoveDialog = false
            onRemove()
        }
    )
}

@Composable
private fun MemberRemoveDialog(memberName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("멤버 제거") },
        text = { Text("${memberName}님을 이 방에서 제거할까요?") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm-remove-member")) {
                Text("제거")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeChips(selected: NotificationMode, onSelected: (NotificationMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NotificationMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelected(mode) },
                label = { Text("${mode.label} · ${reminderDaysLabel(mode)}") },
                modifier = Modifier.testTag("notification-mode-${mode.wire}")
            )
        }
    }
}

private val inviteFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(AppConstants.SEOUL_TIME_ZONE))

private fun reminderDaysLabel(mode: NotificationMode): String =
    mode.days.joinToString(" / ") { if (it == 0) "당일" else "${it}일 전" }
