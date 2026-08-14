package com.giftcondoctor.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.barcodeValuePreview
import com.giftcondoctor.app.core.renderCouponBarcode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.clampZoomOffset
import com.giftcondoctor.app.core.statusLabel
import com.giftcondoctor.app.core.shouldLoadOriginalImage
import com.giftcondoctor.app.core.zoomOffsetForDoubleTap
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.CouponComment
import com.giftcondoctor.app.data.CouponImageLoader
import com.giftcondoctor.app.ui.components.ButtonProgressIndicator
import com.giftcondoctor.app.ui.components.ErrorState
import com.giftcondoctor.app.ui.components.GDInfoBanner
import com.giftcondoctor.app.ui.components.GDScaffold
import com.giftcondoctor.app.ui.components.InlineMessage
import com.giftcondoctor.app.ui.components.KeepScreenBrightWhileVisible
import com.giftcondoctor.app.ui.components.LoadingState
import com.giftcondoctor.app.ui.viewmodel.AddCouponViewModel
import com.giftcondoctor.app.ui.viewmodel.CouponUploadStage
import com.giftcondoctor.app.ui.viewmodel.CouponUploadState
import com.giftcondoctor.app.ui.viewmodel.CouponDetailViewModel
import com.giftcondoctor.app.ui.viewmodel.CouponOriginalImageState
import com.giftcondoctor.app.ui.viewmodel.shouldCancelOriginalImageLoad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val manualBarcodeFormats = listOf(
    "CODE_128" to "CODE 128",
    "QR_CODE" to "QR",
    "EAN_13" to "EAN-13",
    "CODE_39" to "CODE 39",
    "PDF_417" to "PDF417",
    "DATA_MATRIX" to "Data Matrix"
)

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
    val barcode by viewModel.barcode.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var title by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var barcodeValue by remember { mutableStateOf("") }
    var barcodeFormat by remember { mutableStateOf<String?>(null) }
    var manualBarcodeEntry by remember { mutableStateOf(false) }
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

    LaunchedEffect(barcode, imageUri) {
        barcodeValue = barcode?.value.orEmpty()
        barcodeFormat = barcode?.format
        manualBarcodeEntry = false
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
                    .clickable(enabled = !busy) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
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
                val detectedBarcodeFormat = barcodeFormat
                if (detectedBarcodeFormat != null) {
                    GDInfoBanner(
                        title = if (manualBarcodeEntry) {
                            "바코드 직접 입력 · $detectedBarcodeFormat"
                        } else {
                            "바코드 감지 · $detectedBarcodeFormat"
                        },
                        body = if (barcodeValue.isBlank()) {
                            "이미지에 표시된 바코드 값을 입력해 주세요."
                        } else {
                            "${barcodeValuePreview(barcodeValue)} · 저장 후 계산대용 큰 화면으로 열 수 있어요."
                        }
                    )
                    if (manualBarcodeEntry) {
                        Text("바코드 형식", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            manualBarcodeFormats.forEach { (format, label) ->
                                FilterChip(
                                    selected = detectedBarcodeFormat == format,
                                    onClick = { barcodeFormat = format },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = barcodeValue,
                        onValueChange = { value -> if (value.length <= 2048) barcodeValue = value },
                        label = { Text(if (manualBarcodeEntry) "바코드 값" else "바코드 값 확인") },
                        supportingText = { Text("이미지에 적힌 값과 같은지 확인해 주세요.") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    TextButton(
                        onClick = {
                            barcodeValue = ""
                            barcodeFormat = null
                            manualBarcodeEntry = false
                        }
                    ) {
                        Text("바코드 저장 안 함")
                    }
                } else if (!analysisBusy) {
                    OutlinedButton(
                        onClick = {
                            barcodeValue = ""
                            barcodeFormat = "CODE_128"
                            manualBarcodeEntry = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.QrCode2, contentDescription = null)
                        Text("바코드 직접 입력")
                    }
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
                if (busy) {
                    CouponUploadProgress(uploadState, viewModel::cancelUpload)
                }
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
                            barcodeValue = barcodeValue.takeIf { it.isNotBlank() },
                            barcodeFormat = barcodeFormat,
                            onAdded = onAdded
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (busy) ButtonProgressIndicator()
                    Text(
                        when {
                            !busy -> "쿠폰 등록하기"
                            uploadState.stage == CouponUploadStage.Preparing -> "이미지 최적화 중..."
                            uploadState.stage == CouponUploadStage.Cancelling -> "정리 중..."
                            uploadState.stage == CouponUploadStage.Saving -> "저장 중..."
                            uploadState.percent != null -> "업로드 ${uploadState.percent}%"
                            else -> "업로드 중..."
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun CouponUploadProgress(uploadState: CouponUploadState, onCancel: () -> Unit) {
    val uploadPercent = uploadState.percent
    val optimizationSummary = uploadState.optimizationSummary()
    val statusText = when (uploadState.stage) {
        CouponUploadStage.Preparing -> "빠른 업로드를 위해 이미지를 최적화하는 중이에요"
        CouponUploadStage.Uploading -> listOfNotNull(
            optimizationSummary,
            uploadPercent?.let { "업로드 $it%" } ?: "업로드 중"
        ).joinToString(" · ")
        CouponUploadStage.Cancelling -> "업로드를 중단하고 임시 파일을 정리하는 중이에요"
        CouponUploadStage.Saving -> "쿠폰 정보를 안전하게 저장하는 중이에요"
        CouponUploadStage.Idle -> "쿠폰 등록을 준비하는 중이에요"
    }
    if (uploadPercent != null && uploadState.stage == CouponUploadStage.Uploading) {
        LinearProgressIndicator(
            progress = { uploadPercent.coerceIn(0, 100) / 100f },
            modifier = Modifier.fillMaxWidth()
        )
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    Text(
        statusText,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (uploadState.stage in setOf(CouponUploadStage.Preparing, CouponUploadStage.Uploading)) {
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("업로드 취소")
        }
    }
}

private fun CouponUploadState.optimizationSummary(): String? {
    val original = originalBytes ?: return null
    val upload = uploadBytes ?: return null
    if (upload >= original) return null
    return "이미지 ${formatImageBytes(original)} → ${formatImageBytes(upload)}"
}

private fun formatImageBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.KOREA, "%.1fMB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(Locale.KOREA, "%.0fKB", bytes / 1024.0)
    else -> "${bytes}B"
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
    showAddedFeedback: Boolean = false,
    onAddedFeedbackConsumed: () -> Unit = {},
    onAddAnother: () -> Unit = {},
    viewModel: CouponDetailViewModel = viewModel(key = "coupon-$roomId-$couponId")
) {
    val context = LocalContext.current
    LaunchedEffect(roomId, couponId) { viewModel.start(roomId, couponId) }
    val couponState by viewModel.coupon.collectAsStateWithLifecycle()
    val commentsState by viewModel.comments.collectAsStateWithLifecycle()
    val imageState by viewModel.originalImage.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val commentBusy by viewModel.commentBusy.collectAsStateWithLifecycle()
    val busyAction by viewModel.busyAction.collectAsStateWithLifecycle()
    val imageReplaceState by viewModel.imageReplaceState.collectAsStateWithLifecycle()
    val roomOwnerUid by viewModel.roomOwnerUid.collectAsStateWithLifecycle()
    val currentUid = viewModel.currentUid
    val snackbarHostState = remember { SnackbarHostState() }
    var usedFeedbackVersion by rememberSaveable(couponId) { mutableIntStateOf(0) }

    CouponAddedFeedbackEffect(
        showAddedFeedback = showAddedFeedback,
        couponId = couponId,
        snackbarHostState = snackbarHostState,
        onConsumed = onAddedFeedbackConsumed,
        onAddAnother = onAddAnother
    )
    CouponUsedFeedbackEffect(
        feedbackVersion = usedFeedbackVersion,
        couponId = couponId,
        snackbarHostState = snackbarHostState,
        onUndo = { viewModel.undoMarkUsed(roomId, couponId) }
    )

    GDScaffold(
        title = "쿠폰 상세",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { modifier ->
        when (val state = couponState) {
            UiState.Loading -> LoadingState()
            is UiState.Error -> ErrorState(state.message)
            is UiState.Success -> {
                LaunchedEffect(state.data.id, state.data.thumbnailBlobPath) {
                    if (shouldLoadOriginalImage(hasThumbnail = state.data.thumbnailBlobPath != null)) {
                        viewModel.loadOriginalImage(context, roomId, couponId)
                    }
                }
                CouponDetailContent(
                    modifier = modifier,
                    roomId = roomId,
                    coupon = state.data,
                    imageState = imageState,
                    commentsState = commentsState,
                    currentUid = currentUid,
                    roomOwnerUid = roomOwnerUid,
                    commentBusy = commentBusy,
                    busyAction = busyAction,
                    imageReplaceState = imageReplaceState,
                    message = message,
                    onReserve = { viewModel.reserve(roomId, couponId) },
                    onCancelReservation = { viewModel.cancelReservation(roomId, couponId) },
                    onUsed = {
                        viewModel.markUsed(roomId, couponId) {
                            usedFeedbackVersion += 1
                        }
                    },
                    onDelete = { viewModel.delete(roomId, couponId, onDeleted) },
                    onAddComment = { body, onAdded -> viewModel.addComment(roomId, couponId, body, onAdded) },
                    onDeleteComment = { commentId -> viewModel.deleteComment(roomId, couponId, commentId) },
                    onReplaceImage = { uri, onReplaced ->
                        viewModel.replaceImage(context, roomId, couponId, uri, onReplaced)
                    },
                    onPrepareReplacementImage = { uri ->
                        viewModel.prepareReplacementImage(context, uri)
                    },
                    onDiscardReplacementImage = viewModel::discardReplacementImage,
                    onRequestImage = { viewModel.loadOriginalImage(context, roomId, couponId) },
                    onCancelImageRequest = viewModel::cancelOriginalImageLoad,
                    onRetryImage = { viewModel.loadOriginalImage(context, roomId, couponId, force = true) },
                    onEdit = { title, brand, expires, visibility, notifyTarget, onSaved ->
                        viewModel.edit(roomId, couponId, title, brand, expires, visibility, notifyTarget, onSaved)
                    }
                )
            }
        }
    }
}

@Composable
internal fun CouponAddedFeedbackEffect(
    showAddedFeedback: Boolean,
    couponId: String,
    snackbarHostState: SnackbarHostState,
    onConsumed: () -> Unit,
    onAddAnother: () -> Unit
) {
    LaunchedEffect(showAddedFeedback, couponId) {
        if (!showAddedFeedback) return@LaunchedEffect
        var result: SnackbarResult? = null
        try {
            result = snackbarHostState.showSnackbar(
                message = "쿠폰을 등록했어요. 상세 정보를 확인해 주세요.",
                actionLabel = "하나 더 등록",
                withDismissAction = true
            )
        } finally {
            onConsumed()
        }
        if (result == SnackbarResult.ActionPerformed) onAddAnother()
    }
}

@Composable
internal fun CouponUsedFeedbackEffect(
    feedbackVersion: Int,
    couponId: String,
    snackbarHostState: SnackbarHostState,
    onUndo: () -> Unit
) {
    LaunchedEffect(feedbackVersion, couponId) {
        if (feedbackVersion <= 0) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "사용 완료로 변경했어요.",
            actionLabel = "실행 취소",
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) onUndo()
    }
}

@Composable
private fun CouponDetailContent(
    modifier: Modifier,
    roomId: String,
    coupon: Coupon,
    imageState: CouponOriginalImageState,
    commentsState: UiState<List<CouponComment>>,
    currentUid: String?,
    roomOwnerUid: String?,
    commentBusy: Boolean,
    busyAction: String?,
    imageReplaceState: com.giftcondoctor.app.ui.viewmodel.CouponUploadState,
    message: String?,
    onReserve: () -> Unit,
    onCancelReservation: () -> Unit,
    onUsed: () -> Unit,
    onDelete: () -> Unit,
    onAddComment: (String, () -> Unit) -> Unit,
    onDeleteComment: (String) -> Unit,
    onReplaceImage: (Uri, () -> Unit) -> Unit,
    onPrepareReplacementImage: (Uri) -> Unit,
    onDiscardReplacementImage: () -> Unit,
    onRequestImage: () -> Unit,
    onCancelImageRequest: () -> Unit,
    onRetryImage: () -> Unit,
    onEdit: (String, String, String, String, String, () -> Unit) -> Unit
) {
    var editMode by remember(coupon.id) { mutableStateOf(false) }
    var expandedImage by remember(coupon.id) { mutableStateOf<ImageBitmap?>(null) }
    var showMarkUsedDialog by remember(coupon.id) { mutableStateOf(false) }
    var showDeleteDialog by remember(coupon.id) { mutableStateOf(false) }
    var showBarcodeDialog by remember(coupon.id) { mutableStateOf(false) }
    var replacementImageUri by remember(coupon.id) { mutableStateOf<Uri?>(null) }
    val replacementPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            replacementImageUri = it
            onPrepareReplacementImage(it)
        }
    }
    val actionBusy = busyAction != null

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CouponImage(
            roomId = roomId,
            coupon = coupon,
            imageState = imageState,
            onRequestImage = onRequestImage,
            onOpenImage = { expandedImage = it }
        )
        val barcodeValue = coupon.barcodeValue
        val barcodeFormat = coupon.barcodeFormat
        if (barcodeValue != null && barcodeFormat != null) {
            Card(onClick = { showBarcodeDialog = true }, modifier = Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = {
                        Icon(Icons.Default.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    headlineContent = { Text("계산대용 바코드 크게 보기") },
                    supportingContent = {
                        Text("$barcodeFormat · ${barcodeValuePreview(barcodeValue)} · 최대 밝기로 표시")
                    }
                )
            }
        }
        if (coupon.ownerUid == currentUid) {
            val replacement = replacementImageUri
            if (replacement == null) {
                OutlinedButton(
                    onClick = {
                        replacementPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !actionBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Text("이미지 교체")
                }
            } else {
                Text("새 이미지 미리보기", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                SelectedImagePreview(replacement)
                GDInfoBanner(
                    title = "확인 후 교체해 주세요",
                    body = "쿠폰 정보와 예약 상태는 유지됩니다. 잘못된 코드 표시를 막기 위해 기존 자동 감지 바코드는 해제됩니다."
                )
                if (busyAction == null && imageReplaceState.stage == CouponUploadStage.Preparing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        "새 이미지를 빠르게 올릴 수 있도록 미리 준비하는 중이에요",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (
                    busyAction == null &&
                    imageReplaceState.stage == CouponUploadStage.Idle &&
                    imageReplaceState.originalBytes != null
                ) {
                    GDInfoBanner(
                        title = "업로드 준비 완료",
                        body = imageReplaceState.optimizationSummary()
                            ?: "화질과 용량을 비교해 원본을 그대로 사용할 준비를 마쳤어요."
                    )
                }
                if (busyAction == "replaceImage") {
                    val percent = imageReplaceState.percent
                    if (percent != null && imageReplaceState.stage == CouponUploadStage.Uploading) {
                        LinearProgressIndicator(
                            progress = { percent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(
                        when (imageReplaceState.stage) {
                            CouponUploadStage.Preparing -> "빠른 업로드를 위해 새 이미지를 최적화하는 중이에요"
                            CouponUploadStage.Saving -> "새 이미지 적용과 이전 파일 정리 중이에요"
                            CouponUploadStage.Uploading -> listOfNotNull(
                                imageReplaceState.optimizationSummary(),
                                percent?.let { "업로드 $it%" } ?: "업로드 중"
                            ).joinToString(" · ")
                            CouponUploadStage.Cancelling -> "새 이미지 업로드를 정리하는 중이에요"
                            CouponUploadStage.Idle -> "새 이미지 업로드를 준비하는 중이에요"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onDiscardReplacementImage()
                            replacementImageUri = null
                        },
                        enabled = !actionBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("취소")
                    }
                    Button(
                        onClick = { onReplaceImage(replacement) { replacementImageUri = null } },
                        enabled = !actionBusy && imageReplaceState.stage != CouponUploadStage.Preparing,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (busyAction == "replaceImage") ButtonProgressIndicator()
                        Text(
                            when {
                                busyAction == "replaceImage" -> "교체 중..."
                                imageReplaceState.stage == CouponUploadStage.Preparing -> "준비 중..."
                                else -> "이 이미지로 교체"
                            }
                        )
                    }
                }
            }
        }
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
        CouponImageDialog(
            previewBitmap = bitmap,
            imageState = imageState,
            onRetry = onRetryImage,
            onDismiss = {
                if (shouldCancelOriginalImageLoad(imageState)) onCancelImageRequest()
                expandedImage = null
            }
        )
    }
    if (showBarcodeDialog && coupon.barcodeValue != null && coupon.barcodeFormat != null) {
        CouponBarcodeDialog(
            value = coupon.barcodeValue,
            format = coupon.barcodeFormat,
            onDismiss = { showBarcodeDialog = false }
        )
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
private fun CouponBarcodeDialog(value: String, format: String, onDismiss: () -> Unit) {
    KeepScreenBrightWhileVisible()
    var bitmap by remember(value, format) { mutableStateOf<Bitmap?>(null) }
    var renderFinished by remember(value, format) { mutableStateOf(false) }
    DisposableEffect(value, format) {
        onDispose { bitmap?.recycle() }
    }
    LaunchedEffect(value, format) {
        bitmap = withContext(Dispatchers.Default) { renderCouponBarcode(value, format) }
        renderFinished = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.White).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("계산대용 바코드", color = Color.Black, style = MaterialTheme.typography.titleLarge)
                    Text("최대 밝기 · 화면 켜짐", color = Color.DarkGray, style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "바코드 닫기", tint = Color.Black)
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val rendered = bitmap
                when {
                    rendered != null -> Image(
                        bitmap = rendered.asImageBitmap(),
                        contentDescription = "재생성된 $format 바코드",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                    !renderFinished -> CircularProgressIndicator()
                    else -> Text("바코드를 재생성할 수 없습니다. 원본 이미지를 사용해 주세요.", color = Color.Black)
                }
            }
            SelectionContainer {
                Text(
                    value,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "스캔되지 않으면 닫고 원본 이미지를 확대해 주세요.",
                color = Color.DarkGray,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
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
private fun CouponImage(
    roomId: String,
    coupon: Coupon,
    imageState: CouponOriginalImageState,
    onRequestImage: () -> Unit,
    onOpenImage: (ImageBitmap) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val targetWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() }
    val targetHeight = with(density) { 480.dp.roundToPx() }
    val fullImage = (imageState as? CouponOriginalImageState.Ready)?.image
    val hasThumbnail = coupon.thumbnailBlobPath != null
    var thumbnailBitmap by remember(coupon.imageBlobPath, coupon.thumbnailBlobPath, targetWidth, targetHeight) {
        mutableStateOf<ImageBitmap?>(null)
    }
    var thumbnailFinished by remember(coupon.imageBlobPath, coupon.thumbnailBlobPath, targetWidth, targetHeight) {
        mutableStateOf(!hasThumbnail)
    }
    var fullPreviewBitmap by remember(fullImage, targetWidth, targetHeight) { mutableStateOf<ImageBitmap?>(null) }
    var fullPreviewFinished by remember(fullImage, targetWidth, targetHeight) { mutableStateOf(false) }

    LaunchedEffect(roomId, coupon.id, coupon.imageBlobPath, coupon.thumbnailBlobPath, targetWidth, targetHeight) {
        thumbnailBitmap = null
        thumbnailFinished = !hasThumbnail
        if (hasThumbnail) {
            thumbnailBitmap = runCatching {
                CouponImageLoader.load(
                    roomId = roomId,
                    couponId = coupon.id,
                    imageBlobPath = coupon.imageBlobPath,
                    thumbnailBlobPath = coupon.thumbnailBlobPath,
                    targetWidth = targetWidth,
                    targetHeight = targetHeight
                )?.asImageBitmap()
            }.getOrNull()
            thumbnailFinished = true
        }
    }

    LaunchedEffect(fullImage, thumbnailFinished, thumbnailBitmap, targetWidth, targetHeight) {
        fullPreviewBitmap = null
        fullPreviewFinished = false
        if (fullImage != null && (!hasThumbnail || thumbnailFinished && thumbnailBitmap == null)) {
            fullPreviewBitmap = withContext(Dispatchers.IO) {
                runCatching {
                    CouponImageLoader.decodeSampledBitmap(fullImage.file, targetWidth, targetHeight)?.asImageBitmap()
                }.getOrNull()
            }
            fullPreviewFinished = true
        }
    }

    LaunchedEffect(hasThumbnail, thumbnailFinished, thumbnailBitmap) {
        if (shouldLoadOriginalImage(
                hasThumbnail = hasThumbnail,
                thumbnailFailed = thumbnailFinished && thumbnailBitmap == null
            )) {
            onRequestImage()
        }
    }

    val bitmap = thumbnailBitmap ?: fullPreviewBitmap
    val previewFinished = if (hasThumbnail) {
        thumbnailFinished && (thumbnailBitmap != null || fullPreviewFinished || imageState is CouponOriginalImageState.Error)
    } else {
        fullPreviewFinished || imageState is CouponOriginalImageState.Error
    }

    Card(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 480.dp)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val decodedBitmap = bitmap
            if (decodedBitmap != null) {
                Box(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onRequestImage()
                        onOpenImage(decodedBitmap)
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = decodedBitmap,
                        contentDescription = "쿠폰 이미지",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                    Text(
                        if (imageState is CouponOriginalImageState.Loading) {
                            "탭해서 크게 보기 · 원본 준비 중"
                        } else {
                            "탭해서 크게 보기"
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.58f), MaterialTheme.shapes.small)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            } else if (!previewFinished) {
                Text("이미지를 빠르게 준비하는 중입니다")
            } else {
                when (imageState) {
                    is CouponOriginalImageState.Error -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(imageState.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRequestImage) { Text("이미지 다시 불러오기") }
                    }
                    else -> Text("이미지를 표시할 수 없습니다.")
                }
            }
        }
    }
}

@Composable
internal fun CouponImageDialog(
    previewBitmap: ImageBitmap,
    imageState: CouponOriginalImageState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    KeepScreenBrightWhileVisible()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val maxWidth = with(density) { configuration.screenWidthDp.dp.roundToPx() * 2 }
    val maxHeight = with(density) { configuration.screenHeightDp.dp.roundToPx() * 2 }
    val imageFile = (imageState as? CouponOriginalImageState.Ready)?.image
    var highResolution by remember(imageFile, maxWidth, maxHeight) { mutableStateOf<ImageBitmap?>(null) }
    var highResolutionFinished by remember(imageFile, maxWidth, maxHeight) { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val updatedScale = (scale * zoomChange).coerceIn(1f, 4f)
        scale = updatedScale
        offset = clampZoomOffset(offset + panChange, updatedScale, viewportSize)
    }
    fun setZoom(targetScale: Float) {
        val updatedScale = targetScale.coerceIn(1f, 4f)
        val ratio = if (scale > 0f) updatedScale / scale else 1f
        scale = updatedScale
        offset = if (updatedScale == 1f) {
            Offset.Zero
        } else {
            clampZoomOffset(offset * ratio, updatedScale, viewportSize)
        }
    }

    LaunchedEffect(imageFile, maxWidth, maxHeight) {
        highResolution = null
        highResolutionFinished = false
        if (imageFile != null) {
            highResolution = withContext(Dispatchers.Default) {
                runCatching {
                    CouponImageLoader.decodeZoomBitmap(imageFile.file, maxWidth, maxHeight)?.asImageBitmap()
                }.getOrNull()
            }
            highResolutionFinished = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = highResolution ?: previewBitmap,
                contentDescription = "확대된 쿠폰 이미지",
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("zoomed-coupon-image")
                    .padding(16.dp)
                    .onSizeChanged { viewportSize = it }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
                    .pointerInput(viewportSize) {
                        detectTapGestures(onDoubleTap = { tap ->
                            val updatedScale = if (scale > 1f) 1f else 2f
                            scale = updatedScale
                            offset = zoomOffsetForDoubleTap(tap, updatedScale, viewportSize)
                        })
                    }
                    .transformable(transformState),
                contentScale = ContentScale.Fit
            )
            if (imageState is CouponOriginalImageState.Idle ||
                imageState is CouponOriginalImageState.Loading ||
                imageState is CouponOriginalImageState.Ready && !highResolutionFinished) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        if (imageState is CouponOriginalImageState.Idle ||
                            imageState is CouponOriginalImageState.Loading) {
                            "미리보기를 먼저 표시했어요 · 선명한 원본을 불러오는 중이에요"
                        } else {
                            "선명한 이미지를 준비하는 중이에요"
                        },
                        color = Color.White
                    )
                }
            } else if (imageState is CouponOriginalImageState.Error) {
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("원본을 불러오지 못해 미리보기로 확대 중이에요", color = Color.White)
                    TextButton(onClick = onRetry) { Text("원본 다시 불러오기") }
                }
            } else {
                Text(
                    if (highResolution == null) {
                        "원본 최적화 실패 · 미리보기로 확대 중"
                    } else {
                        "밝기 최적화됨 · 두 손가락 확대 · 두 번 탭"
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.small)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { setZoom(1f) },
                    enabled = scale != 1f
                ) {
                    Text("원본 맞춤", color = Color.White)
                }
                Row(
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.62f), MaterialTheme.shapes.extraLarge),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { setZoom(scale - 1f) }, enabled = scale > 1f) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "축소", tint = Color.White)
                    }
                    Text(
                        String.format(Locale.US, "%.1f×", scale),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    IconButton(onClick = { setZoom(scale + 1f) }, enabled = scale < 4f) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "확대", tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.White)
                }
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
