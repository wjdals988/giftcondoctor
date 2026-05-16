package com.giftcondoctor.app.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.viewmodel.AddCouponViewModel
import com.giftcondoctor.app.ui.viewmodel.CouponDetailViewModel
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun AddCouponScreen(
    roomId: String,
    onBack: () -> Unit,
    onAdded: (String) -> Unit,
    viewModel: AddCouponViewModel = viewModel(key = "add-coupon-$roomId")
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var expires by remember {
        mutableStateOf(LocalDate.now(ZoneId.of(AppConstants.SEOUL_TIME_ZONE)).plusDays(7).toString())
    }
    var privateCoupon by remember { mutableStateOf(false) }
    var ownerOnly by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        imageUri = it
    }

    GDScaffold(title = "쿠폰 추가", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (imageUri == null) "이미지 선택" else "이미지 다시 선택")
            }
            Text("이미지는 서버 API를 통해 private Blob으로 업로드됩니다.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("쿠폰 이름") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("브랜드") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = expires,
                onValueChange = { expires = it },
                label = { Text("만료일 (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("비공개 쿠폰")
                Switch(checked = privateCoupon, onCheckedChange = { privateCoupon = it })
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("등록자에게만 알림")
                Switch(checked = ownerOnly || privateCoupon, enabled = !privateCoupon, onCheckedChange = { ownerOnly = it })
            }
            InlineMessage(message)
            Button(
                enabled = !busy,
                onClick = {
                    viewModel.addCoupon(
                        context = context,
                        roomId = roomId,
                        imageUri = imageUri,
                        title = title,
                        brand = brand,
                        expiresLocalDate = expires,
                        visibility = if (privateCoupon) "private" else "room",
                        notifyTarget = if (privateCoupon || ownerOnly) "ownerOnly" else "allMembers",
                        onAdded = onAdded
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "추가 중..." else "쿠폰 추가")
            }
        }
    }
}

@Composable
fun CouponDetailScreen(
    roomId: String,
    couponId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: CouponDetailViewModel = viewModel(key = "coupon-$roomId-$couponId")
) {
    LaunchedEffect(roomId, couponId) { viewModel.start(roomId, couponId) }
    val couponState by viewModel.coupon.collectAsStateWithLifecycle()
    val imageState by viewModel.imageBytes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    GDScaffold(title = "쿠폰 상세", onBack = onBack) { modifier ->
        when (val state = couponState) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> CouponDetailContent(
                modifier = modifier,
                coupon = state.data,
                imageState = imageState,
                message = message,
                onReserve = { viewModel.reserve(roomId, couponId) },
                onCancelReservation = { viewModel.cancelReservation(roomId, couponId) },
                onUsed = { viewModel.markUsed(roomId, couponId) },
                onDelete = { viewModel.delete(roomId, couponId, onDeleted) },
                onEdit = { title, brand, expires, visibility, notifyTarget ->
                    viewModel.edit(roomId, couponId, title, brand, expires, visibility, notifyTarget)
                }
            )
        }
    }
}

@Composable
private fun CouponDetailContent(
    modifier: Modifier,
    coupon: Coupon,
    imageState: UiState<ByteArray>,
    message: String?,
    onReserve: () -> Unit,
    onCancelReservation: () -> Unit,
    onUsed: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (String, String, String, String, String) -> Unit
) {
    var editMode by remember(coupon.id) { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CouponImage(imageState)
        if (editMode) {
            EditCouponForm(coupon = coupon, onSave = { title, brand, expires, visibility, notifyTarget ->
                onEdit(title, brand, expires, visibility, notifyTarget)
                editMode = false
            })
        } else {
            Text(coupon.title, style = MaterialTheme.typography.headlineSmall)
            Text(coupon.brand.ifBlank { "브랜드 없음" })
            Text("만료일: ${coupon.expiresLocalDate} (${coupon.timezone})")
            Text("상태: ${statusLabel(coupon.status)}")
            Text("공개범위: ${if (coupon.visibility == "private") "비공개" else "방 공개"}")
            Text("알림대상: ${if (coupon.notifyTarget == "ownerOnly") "등록자" else "전체 멤버"}")
        }
        InlineMessage(message)
        HorizontalDivider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (coupon.status == "active") {
                Button(onClick = onReserve, modifier = Modifier.weight(1f)) { Text("예약") }
            }
            if (coupon.status == "reserved") {
                OutlinedButton(onClick = onCancelReservation, modifier = Modifier.weight(1f)) { Text("예약 취소") }
            }
            if (coupon.status == "active" || coupon.status == "reserved") {
                Button(onClick = onUsed, modifier = Modifier.weight(1f)) { Text("사용 완료") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { editMode = !editMode }, modifier = Modifier.weight(1f)) {
                Text(if (editMode) "수정 취소" else "수정")
            }
            OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                Text("삭제")
            }
        }
    }
}

@Composable
private fun CouponImage(imageState: UiState<ByteArray>) {
    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (imageState) {
                UiState.Loading -> Text("이미지를 불러오는 중입니다")
                is UiState.Error -> Text(imageState.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val bitmap = remember(imageState.data) {
                        BitmapFactory.decodeByteArray(imageState.data, 0, imageState.data.size)?.asImageBitmap()
                    }
                    if (bitmap == null) {
                        Text("이미지를 표시할 수 없습니다.")
                    } else {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "쿠폰 이미지",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditCouponForm(
    coupon: Coupon,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember(coupon.id) { mutableStateOf(coupon.title) }
    var brand by remember(coupon.id) { mutableStateOf(coupon.brand) }
    var expires by remember(coupon.id) { mutableStateOf(coupon.expiresLocalDate.toString()) }
    var privateCoupon by remember(coupon.id) { mutableStateOf(coupon.visibility == "private") }
    var ownerOnly by remember(coupon.id) { mutableStateOf(coupon.notifyTarget == "ownerOnly") }

    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("쿠폰 이름") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("브랜드") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = expires, onValueChange = { expires = it }, label = { Text("만료일") }, modifier = Modifier.fillMaxWidth())
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("비공개 쿠폰")
        Switch(checked = privateCoupon, onCheckedChange = { privateCoupon = it })
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("등록자에게만 알림")
        Switch(checked = ownerOnly || privateCoupon, enabled = !privateCoupon, onCheckedChange = { ownerOnly = it })
    }
    Button(
        onClick = {
            onSave(
                title,
                brand,
                expires,
                if (privateCoupon) "private" else "room",
                if (privateCoupon || ownerOnly) "ownerOnly" else "allMembers"
            )
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("수정 저장")
    }
}
