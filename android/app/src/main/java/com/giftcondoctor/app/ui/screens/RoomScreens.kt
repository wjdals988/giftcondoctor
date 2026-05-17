package com.giftcondoctor.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.daysBeforeExpiry
import com.giftcondoctor.app.core.seoulToday
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.data.CouponRepository
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.PublicRoom
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import com.giftcondoctor.app.ui.components.EmptyState
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDBadge
import com.giftcondoctor.app.ui.components.GDInfoBanner
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.GDStatCard
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.components.NotificationPermissionStatus
import com.giftcondoctor.app.ui.components.ReminderTimeBanner
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.rememberNotificationPermissionState
import com.giftcondoctor.app.ui.viewmodel.MemberListViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomDetailViewModel
import com.giftcondoctor.app.ui.viewmodel.RoomListViewModel
import com.giftcondoctor.app.ui.viewmodel.SessionViewModel
import com.giftcondoctor.app.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    var showLogoutDialog by remember { mutableStateOf(false) }

    GDScaffold(
        title = "쿠폰방",
        actions = {
            IconButton(onClick = onOpenNotifications) {
                Icon(Icons.Default.Notifications, contentDescription = "알림 설정")
            }
            IconButton(onClick = { showLogoutDialog = true }) {
                Icon(Icons.Default.Logout, contentDescription = "로그아웃")
            }
        }
    ) { modifier ->
        Column(modifier = modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GDInfoBanner(
                title = "만료 알림을 켜두면 놓치지 않아요",
                body = "기프티콘닥터가 매일 오전 9시에 만료 예정 쿠폰을 확인해 알려드립니다.",
                icon = Icons.Default.Notifications
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onCreateRoom, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("방 만들기", modifier = Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = onJoinRoom, modifier = Modifier.weight(1f), shape = MaterialTheme.shapes.small) {
                    Text("방 입장")
                }
            }
            when (val state = rooms) {
                UiState.Loading -> LoadingState()
                is UiState.Error -> ErrorState(state.message)
                is UiState.Success -> RoomList(state.data, onOpenRoom)
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
private fun RoomList(rooms: List<RoomMembership>, onOpenRoom: (String) -> Unit) {
    if (rooms.isEmpty()) {
        EmptyState("아직 참여 중인 쿠폰방이 없습니다.")
        return
    }

    LazyColumn(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rooms, key = { it.roomId }) { room ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenRoom(room.roomId) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(room.name, fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text(if (room.role == "owner") "내가 만든 방" else "참여 중") },
                    leadingContent = {
                        Box(
                            modifier = Modifier.size(44.dp),
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
    var code by remember { mutableStateOf("") }
    var selectedRoom by remember { mutableStateOf<PublicRoom?>(null) }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { viewModel.refreshPublicRooms() }

    GDScaffold(title = "방 입장", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("초대코드로 입장", style = MaterialTheme.typography.titleMedium)
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
                Text("공개 방", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.refreshPublicRooms() }) {
                    Text("새로고침")
                }
            }
            Text(
                "공개 방은 목록에서 선택한 뒤 방 비밀번호를 입력해 입장합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        Text("아직 공개 방이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            is UiState.Success -> RoomDashboard(roomId, state.data, onOpenCoupon, modifier)
        }
    }
}

@Composable
private fun RoomDashboard(roomId: String, coupons: List<Coupon>, onOpenCoupon: (String) -> Unit, modifier: Modifier) {
    val today = seoulToday()
    val actionable = coupons.filter { it.status == "active" || it.status == "reserved" }
    val todayCount = actionable.count { daysBeforeExpiry(today, it.expiresLocalDate) == 0 }
    val soonCount = actionable.count { daysBeforeExpiry(today, it.expiresLocalDate) in 0..3 }
    val activeCount = actionable.count()
    val usedCount = coupons.count { it.status == "used" }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            ReminderTimeBanner()
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GDStatCard("오늘 만료", "${todayCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.error)
                GDStatCard("3일 이내", "${soonCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.tertiary)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GDStatCard("사용 가능", "${activeCount}개", Modifier.weight(1f))
                GDStatCard("사용 완료", "${usedCount}개", Modifier.weight(1f), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Text("쿠폰 목록", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        }
        if (coupons.isEmpty()) {
            item {
                EmptyState("아직 등록된 쿠폰이 없습니다.")
            }
        }
        items(coupons, key = { it.id }) { coupon ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onOpenCoupon(coupon.id) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                ListItem(
                    headlineContent = { Text(coupon.title, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text("${coupon.brand.ifBlank { "브랜드 없음" }} · ${coupon.expiresLocalDate} · ${statusLabel(coupon.status)}")
                    },
                    leadingContent = { CouponListThumbnail(roomId, coupon) },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            GDBadge(couponDdayText(coupon))
                            if (coupon.visibility == "private") {
                                Text("비공개", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CouponListThumbnail(roomId: String, coupon: Coupon) {
    val repository = remember { CouponRepository() }
    var image by remember(coupon.id, coupon.imageBlobPath) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(coupon.id, coupon.imageBlobPath) { mutableStateOf(false) }

    LaunchedEffect(roomId, coupon.id, coupon.imageBlobPath) {
        image = null
        if (coupon.imageBlobPath.isBlank()) return@LaunchedEffect
        loading = true
        runCatching {
            withContext(Dispatchers.IO) {
                val bytes = repository.fetchImage(roomId, coupon.id)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
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
    val category = couponCategory(coupon)
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

private fun couponCategory(coupon: Coupon): CouponCategory {
    val source = "${coupon.title} ${coupon.brand}".lowercase()
    fun containsAny(vararg keywords: String): Boolean = keywords.any { source.contains(it.lowercase()) }

    return when {
        containsAny("스타벅스", "커피", "카페", "투썸", "이디야", "메가커피", "컴포즈", "빽다방", "할리스") ->
            CouponCategory("카페", Icons.Default.LocalCafe, Color(0xFFE3F7F2), Color(0xFF008E85))
        containsAny("치킨", "피자", "버거", "맥도날드", "버거킹", "롯데리아", "교촌", "bbq", "bhc", "도미노", "배민", "요기요") ->
            CouponCategory("음식", Icons.Default.Restaurant, Color(0xFFFFF0E7), Color(0xFFE86E2F))
        containsAny("cu", "gs25", "세븐", "이마트24", "편의점") ->
            CouponCategory("편의점", Icons.Default.Store, Color(0xFFE8F2FF), Color(0xFF2878D8))
        containsAny("cgv", "메가박스", "롯데시네마", "영화", "시네마") ->
            CouponCategory("영화", Icons.Default.Theaters, Color(0xFFF1EAFF), Color(0xFF7B52CC))
        containsAny("항공", "호텔", "여행", "야놀자", "여기어때", "숙박") ->
            CouponCategory("여행", Icons.Default.Flight, Color(0xFFE7F7FF), Color(0xFF0095D6))
        containsAny("쿠팡", "네이버", "백화점", "올리브영", "상품권", "쇼핑", "마트") ->
            CouponCategory("쇼핑", Icons.Default.ShoppingBag, Color(0xFFFFF7D9), Color(0xFFC28A00))
        else ->
            CouponCategory("쿠폰", Icons.Default.CardGiftcard, Color(0xFFEAFBF6), Color(0xFF00A89C))
    }
}

private fun couponDdayText(coupon: Coupon): String {
    if (coupon.status == "used") return "사용 완료"
    if (coupon.status == "expired") return "만료"
    val days = daysBeforeExpiry(seoulToday(), coupon.expiresLocalDate)
    return when {
        days < 0 -> "만료"
        days == 0 -> "오늘"
        else -> "D-$days"
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
    val busy by settingsViewModel.busy.collectAsStateWithLifecycle()
    val busyAction by settingsViewModel.busyAction.collectAsStateWithLifecycle()
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
                    Button(
                        onClick = { settingsViewModel.regenerateInvite(roomId) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        val loading = busyAction == "invite"
                        if (loading) ButtonProgressIndicator()
                        Text(if (loading) "처리 중..." else "초대코드 재발급")
                    }
                    HorizontalDivider()
                    Text("방 기본 알림", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    ReminderTimeBanner()
                    Text("방 기본값은 이 방의 멤버 알림 설정이 비어 있을 때 적용됩니다.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ModeChips(selected = roomMode, onSelected = { roomMode = it })
                    Button(
                        onClick = { settingsViewModel.updateRoom(roomId, roomMode) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        val saving = busyAction == "room"
                        if (saving) ButtonProgressIndicator()
                        Text(if (saving) "저장 중..." else "방 기본값 저장")
                    }
                    HorizontalDivider()
                    Text(
                        "내 방 알림은 방 기본값보다 우선합니다. Android 알림 권한이 꺼져 있으면 저장해도 푸시를 받을 수 없습니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                    Button(
                        onClick = { settingsViewModel.updateMember(roomId, memberEnabled && canUsePush, memberMode) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        val saving = busyAction == "member"
                        if (saving) ButtonProgressIndicator()
                        Text(if (saving) "저장 중..." else "내 방 알림 저장")
                    }
                    OutlinedButton(
                        onClick = { settingsViewModel.leaveRoom(roomId, onLeft) },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.small
                    ) {
                        val leaving = busyAction == "leave"
                        if (leaving) ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(if (leaving) "처리 중..." else "방 나가기")
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
                label = { Text("${mode.label} · ${reminderDaysLabel(mode)}") }
            )
        }
    }
}

private val inviteFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(AppConstants.SEOUL_TIME_ZONE))

private fun reminderDaysLabel(mode: NotificationMode): String =
    mode.days.joinToString(" / ") { if (it == 0) "당일" else "${it}일 전" }
