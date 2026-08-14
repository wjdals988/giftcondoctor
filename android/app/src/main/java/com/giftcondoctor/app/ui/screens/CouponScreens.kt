package com.giftcondoctor.app.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.CouponComment
import com.giftcondoctor.app.data.CouponImageLoader
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDInfoBanner
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.viewmodel.AddCouponViewModel
import com.giftcondoctor.app.ui.viewmodel.CouponDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddCouponScreen(
    roomId: String,
    onBack: () -> Unit,
    onAdded: (String) -> Unit,
    viewModel: AddCouponViewModel = viewModel(key = "add-coupon-$roomId")
) {
    val context = LocalContext.current
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val analysisBusy by viewModel.analysisBusy.collectAsStateWithLifecycle()
    val analysisMessage by viewModel.analysisMessage.collectAsStateWithLifecycle()
    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var expires by remember {
        mutableStateOf(LocalDate.now(ZoneId.of(AppConstants.SEOUL_TIME_ZONE)).plusDays(7).toString())
    }
    var privateCoupon by remember { mutableStateOf(false) }
    var ownerOnly by remember { mutableStateOf(false) }
    var showSharingOptions by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now(ZoneId.of(AppConstants.SEOUL_TIME_ZONE)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        imageUri = uri
    }

    LaunchedEffect(imageUri) {
        imageUri?.let { viewModel.recognizeCouponImage(context, it) }
    }

    LaunchedEffect(suggestion) {
        val data = suggestion ?: return@LaunchedEffect
        data.title?.let { title = it }
        data.brand?.let { brand = it }
        data.expiresLocalDate?.let { expires = it.toString() }
    }

    GDScaffold(title = "쿠폰 등록", onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "1  이미지 선택   ·   2  정보 확인   ·   3  저장",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 148.dp)
                    .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            if (imageUri == null) "갤러리에서 쿠폰 선택" else "다른 이미지 선택",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text("이미지에서 이름과 만료일을 자동으로 찾아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (imageUri == null) {
                GDInfoBanner(
                    title = "사진 한 장이면 충분해요",
                    body = "기프티콘 이미지는 인증된 방 멤버에게만 보이며 최대 10MB까지 등록할 수 있어요."
                )
            } else {
                imageUri?.let { SelectedImagePreview(it) }
                if (analysisBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ButtonProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("쿠폰 정보를 찾는 중이에요", style = MaterialTheme.typography.bodySmall)
                    }
                }
                analysisMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("쿠폰 정보 확인", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("쿠폰 이름") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("브랜드 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExpiryDateField(value = expires, onValueChange = { expires = it })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "+7일", 30 to "+30일", 90 to "+90일").forEach { (days, label) ->
                        AssistChip(onClick = { expires = today.plusDays(days.toLong()).toString() }, label = { Text(label) })
                    }
                }
                Card(onClick = { showSharingOptions = !showSharingOptions }, modifier = Modifier.fillMaxWidth()) {
                    ListItem(
                        leadingContent = { Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text("공유·알림 설정") },
                        supportingContent = {
                            Text(if (privateCoupon) "나만 보기 · 나에게만 알림" else if (ownerOnly) "방에 공유 · 나에게만 알림" else "방에 공유 · 모든 멤버에게 알림")
                        },
                        trailingContent = { Icon(Icons.Default.ExpandMore, contentDescription = if (showSharingOptions) "설정 접기" else "설정 펼치기") }
                    )
                    AnimatedVisibility(showSharingOptions) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("나만 보기")
                                    Text("다른 방 멤버에게 쿠폰을 숨겨요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = privateCoupon,
                                    onCheckedChange = {
                                        privateCoupon = it
                                        if (it) ownerOnly = true
                                    }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("나에게만 알림")
                                    Text("방에 공유해도 만료 알림은 나만 받아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(checked = ownerOnly || privateCoupon, enabled = !privateCoupon, onCheckedChange = { ownerOnly = it })
                            }
                        }
                    }
                }
                InlineMessage(message)
                Button(
                    enabled = !busy && !analysisBusy,
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
                    if (busy) ButtonProgressIndicator()
                    Text(if (busy) "등록 중..." else "쿠폰 등록하기")
                }
            }
        }
    }
}

@Composable
private fun SelectedImagePreview(imageUri: Uri) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { 360.dp.roundToPx() }
    var bitmap by remember(imageUri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    LaunchedEffect(imageUri, targetWidth, targetHeight) {
        bitmap = withContext(Dispatchers.IO) {
            CouponImageLoader.decodeSampledBitmap(
                streamProvider = { context.contentResolver.openInputStream(imageUri) },
                targetWidth = targetWidth,
                targetHeight = targetHeight
            )?.asImageBitmap()
        }
    }

    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val preview = bitmap
            if (preview == null) {
                Text("선택한 이미지를 불러오는 중입니다")
            } else {
                Image(
                    bitmap = preview,
                    contentDescription = "선택한 쿠폰 이미지 미리보기",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
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
    val commentsState by viewModel.comments.collectAsStateWithLifecycle()
    val imageState by viewModel.imageBytes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val commentBusy by viewModel.commentBusy.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val roomOwnerUid by viewModel.roomOwnerUid.collectAsStateWithLifecycle()
    val currentUid = viewModel.currentUid

    GDScaffold(title = "쿠폰 상세", onBack = onBack) { modifier ->
        when (val state = couponState) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> CouponDetailContent(
                modifier = modifier,
                coupon = state.data,
                imageState = imageState,
                commentsState = commentsState,
                currentUid = currentUid,
                roomOwnerUid = roomOwnerUid,
                commentBusy = commentBusy,
                busyAction = busyAction,
                message = message,
                onReserve = { viewModel.reserve(roomId, couponId) },
                onCancelReservation = { viewModel.cancelReservation(roomId, couponId) },
                onUsed = { viewModel.markUsed(roomId, couponId) },
                onDelete = { viewModel.delete(roomId, couponId, onDeleted) },
                onAddComment = { body, onAdded -> viewModel.addComment(roomId, couponId, body, onAdded) },
                onDeleteComment = { commentId -> viewModel.deleteComment(roomId, couponId, commentId) },
                onEdit = { title, brand, expires, visibility, notifyTarget, onSaved ->
                    viewModel.edit(roomId, couponId, title, brand, expires, visibility, notifyTarget, onSaved)
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
    commentsState: UiState<List<CouponComment>>,
    currentUid: String?,
    roomOwnerUid: String?,
    commentBusy: Boolean,
    busyAction: String?,
    message: String?,
    onReserve: () -> Unit,
    onCancelReservation: () -> Unit,
    onUsed: () -> Unit,
    onDelete: () -> Unit,
    onAddComment: (String, () -> Unit) -> Unit,
    onDeleteComment: (String) -> Unit,
    onEdit: (String, String, String, String, String, () -> Unit) -> Unit
) {
    var editMode by remember(coupon.id) { mutableStateOf(false) }
    var expandedImage by remember(coupon.id) { mutableStateOf<ImageBitmap?>(null) }
    var showMarkUsedDialog by remember(coupon.id) { mutableStateOf(false) }
    var showDeleteDialog by remember(coupon.id) { mutableStateOf(false) }
    val actionBusy = busyAction != null

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CouponImage(imageState, onOpenImage = { expandedImage = it })
        if (editMode) {
            EditCouponForm(
                coupon = coupon,
                saving = busyAction == "edit",
                onSave = { title, brand, expires, visibility, notifyTarget ->
                    onEdit(title, brand, expires, visibility, notifyTarget) { editMode = false }
                }
            )
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
                Button(onClick = onReserve, enabled = !actionBusy, modifier = Modifier.weight(1f)) { Text("예약") }
            }
            if (coupon.status == "reserved" &&
                (coupon.reservedByUid == currentUid || coupon.ownerUid == currentUid)
            ) {
                OutlinedButton(onClick = onCancelReservation, enabled = !actionBusy, modifier = Modifier.weight(1f)) {
                    Text("예약 취소")
                }
            }
            if (coupon.status == "active" ||
                (coupon.status == "reserved" && coupon.reservedByUid == currentUid)
            ) {
                Button(
                    onClick = { showMarkUsedDialog = true },
                    enabled = !actionBusy,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("사용 완료")
                }
            }
        }
        if (coupon.ownerUid == currentUid || roomOwnerUid == currentUid) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (coupon.ownerUid == currentUid) {
                    OutlinedButton(
                        onClick = { editMode = !editMode },
                        enabled = !actionBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (editMode) "수정 취소" else "수정")
                    }
                }
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    enabled = !actionBusy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("삭제")
                }
            }
        }
        HorizontalDivider()
        CouponCommentsSection(
            commentsState = commentsState,
            currentUid = currentUid,
            commentBusy = commentBusy,
            onAddComment = onAddComment,
            onDeleteComment = onDeleteComment
        )
    }

    expandedImage?.let { bitmap ->
        CouponImageDialog(bitmap = bitmap, onDismiss = { expandedImage = null })
    }
    if (showMarkUsedDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) showMarkUsedDialog = false },
            title = { Text("사용 완료로 변경할까요?") },
            text = { Text("완료한 쿠폰은 사용 가능 목록에서 제외됩니다.") },
            dismissButton = {
                TextButton(onClick = { showMarkUsedDialog = false }, enabled = !actionBusy) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showMarkUsedDialog = false
                        onUsed()
                    },
                    enabled = !actionBusy
                ) {
                    Text("사용 완료")
                }
            }
        )
    }
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { if (!actionBusy) showDeleteDialog = false },
            title = { Text("쿠폰을 삭제할까요?") },
            text = { Text("쿠폰 이미지와 댓글도 함께 삭제되며 되돌릴 수 없습니다.") },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }, enabled = !actionBusy) { Text("취소") }
            },
            confirmButton = {
                Button(
                    onClick = { onDelete() },
                    enabled = !actionBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    if (busyAction == "delete") ButtonProgressIndicator()
                    Text(if (busyAction == "delete") "삭제 중..." else "삭제")
                }
            }
        )
    }
}

@Composable
private fun CouponCommentsSection(
    commentsState: UiState<List<CouponComment>>,
    currentUid: String?,
    commentBusy: Boolean,
    onAddComment: (String, () -> Unit) -> Unit,
    onDeleteComment: (String) -> Unit
) {
    var body by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("댓글", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "방 멤버들이 이 쿠폰에 대해 메모를 남길 수 있습니다.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = body,
            onValueChange = { if (it.length <= 500) body = it },
            label = { Text("댓글 입력") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            supportingText = {
                Text("${body.length}/500")
            }
        )
        Button(
            onClick = {
                val text = body
                onAddComment(text) { body = "" }
            },
            enabled = !commentBusy && body.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small
        ) {
            if (commentBusy) ButtonProgressIndicator()
            Text(if (commentBusy) "등록 중..." else "댓글 등록")
        }

        when (commentsState) {
            UiState.Loading -> Text("댓글을 불러오는 중입니다", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is UiState.Error -> Text(commentsState.message, color = MaterialTheme.colorScheme.error)
            is UiState.Success -> {
                if (commentsState.data.isEmpty()) {
                    Text("아직 댓글이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    commentsState.data.forEach { comment ->
                        CommentRow(
                            comment = comment,
                            canDelete = comment.authorUid == currentUid,
                            onDelete = { onDeleteComment(comment.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(comment: CouponComment, canDelete: Boolean, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(comment.authorName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatCommentTime(comment.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (canDelete) {
                    TextButton(onClick = onDelete) {
                        Text("삭제")
                    }
                }
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CouponImage(imageState: UiState<ByteArray>, onOpenImage: (ImageBitmap) -> Unit) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { 480.dp.roundToPx() }
    val imageBytes = (imageState as? UiState.Success)?.data
    var bitmap by remember(imageBytes, targetWidth, targetHeight) { mutableStateOf<ImageBitmap?>(null) }
    var decodeFinished by remember(imageBytes, targetWidth, targetHeight) { mutableStateOf(false) }

    LaunchedEffect(imageBytes, targetWidth, targetHeight) {
        bitmap = null
        decodeFinished = false
        if (imageBytes != null) {
            bitmap = withContext(Dispatchers.IO) {
                CouponImageLoader.decodeSampledBitmap(imageBytes, targetWidth, targetHeight)?.asImageBitmap()
            }
            decodeFinished = true
        }
    }

    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (imageState) {
                UiState.Loading -> Text("이미지를 불러오는 중입니다")
                is UiState.Error -> Text(imageState.message, color = MaterialTheme.colorScheme.error)
                is UiState.Success -> {
                    val decodedBitmap = bitmap
                    if (!decodeFinished) {
                        Text("이미지를 최적화하는 중입니다")
                    } else if (decodedBitmap == null) {
                        Text("이미지를 표시할 수 없습니다.")
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().clickable { onOpenImage(decodedBitmap) },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = decodedBitmap,
                                contentDescription = "쿠폰 이미지",
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                "탭해서 크게 보기",
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .background(Color.Black.copy(alpha = 0.58f), MaterialTheme.shapes.small)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CouponImageDialog(bitmap: ImageBitmap, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = "확대된 쿠폰 이미지",
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
            }
        }
    }
}

private val commentTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of(AppConstants.SEOUL_TIME_ZONE))

private fun formatCommentTime(createdAt: Instant?): String =
    createdAt?.let { commentTimeFormatter.format(it) } ?: "방금 전"

@Composable
private fun EditCouponForm(
    coupon: Coupon,
    saving: Boolean,
    onSave: (String, String, String, String, String) -> Unit
) {
    var title by remember(coupon.id) { mutableStateOf(coupon.title) }
    var brand by remember(coupon.id) { mutableStateOf(coupon.brand) }
    var expires by remember(coupon.id) { mutableStateOf(coupon.expiresLocalDate.toString()) }
    var privateCoupon by remember(coupon.id) { mutableStateOf(coupon.visibility == "private") }
    var ownerOnly by remember(coupon.id) { mutableStateOf(coupon.notifyTarget == "ownerOnly") }

    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("쿠폰 이름") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("브랜드") }, modifier = Modifier.fillMaxWidth())
    ExpiryDateField(value = expires, onValueChange = { expires = it }, enabled = !saving)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("비공개 쿠폰")
        Switch(
            checked = privateCoupon,
            onCheckedChange = {
                privateCoupon = it
                if (it) ownerOnly = true
            }
        )
    }
    Text(
        "비공개 쿠폰은 등록자 본인만 볼 수 있습니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("등록자에게만 알림")
        Switch(checked = ownerOnly || privateCoupon, enabled = !privateCoupon, onCheckedChange = { ownerOnly = it })
    }
    Text(
        "방에는 공유하되 만료 알림은 등록자에게만 보내고 싶을 때 사용합니다.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
        enabled = !saving,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (saving) ButtonProgressIndicator()
        Text(if (saving) "저장 중..." else "수정 저장")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDateField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean = true
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("만료일") },
        supportingText = { Text("YYYY-MM-DD 형식으로 입력하거나 달력에서 선택하세요.") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        trailingIcon = {
            IconButton(onClick = { showDatePicker = true }, enabled = enabled) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "만료일 달력 열기")
            }
        }
    )

    if (showDatePicker) {
        val selectedDateMillis = runCatching {
            LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("취소") }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            onValueChange(selectedDate.toString())
                        }
                        showDatePicker = false
                    },
                    enabled = datePickerState.selectedDateMillis != null
                ) {
                    Text("선택")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
