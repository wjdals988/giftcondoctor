package com.giftcondoctor.app.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.CouponTextSuggestion
import com.giftcondoctor.app.core.DetectedCouponBarcode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.core.couponBarcodeValidationError
import com.giftcondoctor.app.core.detectCouponBarcode
import com.giftcondoctor.app.core.parseCouponText
import com.giftcondoctor.app.core.renderCouponBarcode
import com.giftcondoctor.app.data.AuthRepository
import com.giftcondoctor.app.data.CouponImageLoader
import com.giftcondoctor.app.data.CouponImageFile
import com.giftcondoctor.app.data.CouponImageFileStore
import com.giftcondoctor.app.data.CouponPager
import com.giftcondoctor.app.data.CouponPagingState
import com.giftcondoctor.app.data.CouponRepository
import com.giftcondoctor.app.data.CouponUploadPreparation
import com.giftcondoctor.app.data.PreparedCouponUpload
import com.giftcondoctor.app.data.NotificationRepository
import com.giftcondoctor.app.data.PushTokenRepository
import com.giftcondoctor.app.data.RoomRepository
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.CouponComment
import com.giftcondoctor.app.data.model.DeletedCoupon
import com.giftcondoctor.app.data.model.PublicRoom
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class SessionAuthState { Loading, Authenticated, Unauthenticated }
enum class CouponUploadStage { Idle, Preparing, Uploading, Cancelling, Saving }

data class CouponUploadState(
    val stage: CouponUploadStage = CouponUploadStage.Idle,
    val percent: Int? = null,
    val originalBytes: Long? = null,
    val uploadBytes: Long? = null
)

private fun CouponUploadPreparation.toUploadState() = CouponUploadState(
    stage = CouponUploadStage.Uploading,
    percent = 0,
    originalBytes = originalBytes,
    uploadBytes = uploadBytes
)

sealed interface CouponOriginalImageState {
    data object Idle : CouponOriginalImageState
    data object Loading : CouponOriginalImageState
    data class Ready(val image: CouponImageFile) : CouponOriginalImageState
    data class Error(val message: String) : CouponOriginalImageState
}

internal fun shouldStartOriginalImageLoad(
    state: CouponOriginalImageState,
    force: Boolean
): Boolean = force || state !is CouponOriginalImageState.Loading &&
    state !is CouponOriginalImageState.Ready

internal fun shouldCancelOriginalImageLoad(state: CouponOriginalImageState): Boolean =
    state is CouponOriginalImageState.Loading

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val pushTokenRepository: PushTokenRepository = PushTokenRepository()
) : ViewModel() {
    private val _authState = MutableStateFlow(SessionAuthState.Loading)
    val authState: StateFlow<SessionAuthState> = _authState

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val currentUid: String?
        get() = authRepository.currentUid

    init {
        viewModelScope.launch {
            authRepository.authState().collect {
                _authState.value = if (it) SessionAuthState.Authenticated else SessionAuthState.Unauthenticated
                if (it) runCatching { pushTokenRepository.saveCurrentToken() }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun showMessage(message: String) {
        _message.value = message
    }

    fun signIn(email: String, password: String) = runAuth {
        authRepository.signInWithEmail(email, password)
        pushTokenRepository.saveCurrentToken()
    }

    fun createAccount(email: String, password: String) = runAuth {
        authRepository.createAccount(email, password)
        pushTokenRepository.saveCurrentToken()
    }

    fun googleSignInIntent(context: Context): Intent = authRepository.googleSignInIntent(context)

    fun signInWithGoogleIntent(data: Intent?) = runAuth {
        authRepository.signInWithGoogleIntent(data)
        pushTokenRepository.saveCurrentToken()
    }

    fun signOut() {
        viewModelScope.launch {
            _busy.value = true
            val cleanup = runCatching { pushTokenRepository.deleteCurrentToken() }
            authRepository.signOut()
            cleanup.onFailure {
                _message.value = "로그아웃했지만 알림 토큰 정리가 지연될 수 있습니다."
            }
            _busy.value = false
        }
    }

    private fun runAuth(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { block() }
                .onFailure { _message.value = it.localizedMessage ?: "로그인에 실패했습니다." }
            _busy.value = false
        }
    }
}

class RoomListViewModel(
    private val repository: RoomRepository = RoomRepository()
) : ViewModel() {
    private val _rooms = MutableStateFlow<UiState<List<RoomMembership>>>(UiState.Loading)
    val rooms: StateFlow<UiState<List<RoomMembership>>> = _rooms

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _publicRooms = MutableStateFlow<UiState<List<PublicRoom>>>(UiState.Loading)
    val publicRooms: StateFlow<UiState<List<PublicRoom>>> = _publicRooms

    init {
        viewModelScope.launch {
            repository.observeMemberships()
                .catch { _rooms.value = UiState.Error(it.localizedMessage ?: "방 목록을 불러오지 못했습니다.") }
                .collect { _rooms.value = UiState.Success(it) }
        }
    }

    fun createRoom(name: String, isPublic: Boolean, password: String, onCreated: (String) -> Unit) = runAction {
        val roomId = repository.createRoom(name, isPublic, password)
        onCreated(roomId)
    }

    fun joinRoom(inviteCode: String, onJoined: (String) -> Unit) = runAction {
        val roomId = repository.joinRoom(inviteCode)
        onJoined(roomId)
    }

    fun joinPublicRoom(roomId: String, password: String, onJoined: (String) -> Unit) = runAction {
        val roomIdResult = repository.joinPublicRoom(roomId, password)
        onJoined(roomIdResult)
    }

    fun joinPushTestRoom(onJoined: (String) -> Unit) = runAction {
        val roomId = repository.joinPushTestRoom()
        onJoined(roomId)
    }

    fun refreshPublicRooms() {
        viewModelScope.launch {
            _publicRooms.value = UiState.Loading
            runCatching { repository.publicRooms() }
                .onSuccess { _publicRooms.value = UiState.Success(it) }
                .onFailure { _publicRooms.value = UiState.Error(it.localizedMessage ?: "공개 방 목록을 불러오지 못했습니다.") }
        }
    }

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { block() }
                .onFailure { _message.value = it.localizedMessage ?: "요청에 실패했습니다." }
            _busy.value = false
        }
    }
}

class RoomDetailViewModel(
    private val roomRepository: RoomRepository = RoomRepository(),
    private val couponRepository: CouponRepository = CouponRepository()
) : ViewModel() {
    private var roomJob: Job? = null
    private var couponJob: Job? = null
    private var couponPager: CouponPager? = null
    private var startedRoomId: String? = null

    private val _room = MutableStateFlow<UiState<Room>>(UiState.Loading)
    val room: StateFlow<UiState<Room>> = _room

    private val _coupons = MutableStateFlow(CouponPagingState())
    val coupons: StateFlow<CouponPagingState> = _coupons

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val currentUid: String?
        get() = roomRepository.currentUid

    fun start(roomId: String) {
        if (startedRoomId == roomId) {
            couponPager?.refresh()
            return
        }
        roomJob?.cancel()
        couponJob?.cancel()
        couponPager?.close()
        startedRoomId = roomId
        _room.value = UiState.Loading
        _coupons.value = CouponPagingState()
        roomJob = viewModelScope.launch {
            roomRepository.observeRoom(roomId)
                .catch { _room.value = UiState.Error(it.localizedMessage ?: "방 정보를 불러오지 못했습니다.") }
                .collect { room ->
                    _room.value = room?.let { UiState.Success(it) } ?: UiState.Error("방을 찾을 수 없습니다.")
                }
        }
        couponPager = couponRepository.couponPager(roomId)
        couponJob = viewModelScope.launch {
            couponPager?.state?.collect { _coupons.value = it }
        }
    }

    fun loadMoreCoupons() {
        couponPager?.loadNextPage()
    }

    fun retryCoupons() {
        couponPager?.refresh()
    }

    suspend fun restoreDeletedCoupon(roomId: String, couponId: String): Result<Unit> {
        return runCatching { couponRepository.restoreDeletedCoupon(roomId, couponId) }
            .onSuccess { couponPager?.refresh() }
    }

    override fun onCleared() {
        couponPager?.close()
        super.onCleared()
    }
}

class AddCouponViewModel(
    private val repository: CouponRepository = CouponRepository()
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _analysisBusy = MutableStateFlow(false)
    val analysisBusy: StateFlow<Boolean> = _analysisBusy

    private val _analysisMessage = MutableStateFlow<String?>(null)
    val analysisMessage: StateFlow<String?> = _analysisMessage

    private val _suggestion = MutableStateFlow<CouponTextSuggestion?>(null)
    val suggestion: StateFlow<CouponTextSuggestion?> = _suggestion

    private val _barcode = MutableStateFlow<DetectedCouponBarcode?>(null)
    val barcode: StateFlow<DetectedCouponBarcode?> = _barcode

    private val _uploadState = MutableStateFlow(CouponUploadState())
    val uploadState: StateFlow<CouponUploadState> = _uploadState
    private var addCouponJob: Job? = null
    private var imageAnalysisJob: Job? = null
    private var imageAnalysisRequestId = 0L
    private var preparedUpload: PreparedCouponUpload? = null

    fun recognizeCouponImage(context: Context, imageUri: Uri) {
        imageAnalysisJob?.cancel()
        preparedUpload?.close()
        preparedUpload = null
        val requestId = ++imageAnalysisRequestId
        imageAnalysisJob = viewModelScope.launch {
            _analysisBusy.value = true
            _analysisMessage.value = "쿠폰 정보를 읽고 빠른 업로드를 준비하는 중입니다."
            _suggestion.value = null
            _barcode.value = null

            val result = runCatching {
                coroutineScope {
                    val uploadPreparation = async {
                        repository.prepareCouponImage(context.applicationContext, imageUri)
                    }
                    val imageAnalysis = async {
                        val analysisBitmap = withContext(Dispatchers.IO) {
                            CouponImageLoader.decodeScaledBitmap(
                                streamProvider = { context.contentResolver.openInputStream(imageUri) },
                                maxDimension = 1_600
                            )
                        } ?: error("이미지를 읽을 수 없습니다.")
                        val image = InputImage.fromBitmap(analysisBitmap, 0)
                        val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
                        try {
                            coroutineScope {
                                val text = async {
                                    runCatching { recognizer.process(image).await().text }.getOrDefault("")
                                }
                                val detectedBarcode = async {
                                    runCatching {
                                        withContext(Dispatchers.Default) { detectCouponBarcode(analysisBitmap) }
                                    }.getOrNull()
                                }
                                parseCouponText(text.await()) to detectedBarcode.await()
                            }
                        } finally {
                            recognizer.close()
                            analysisBitmap.recycle()
                        }
                    }
                    try {
                        val (suggestion, detectedBarcode) = imageAnalysis.await()
                        Triple(suggestion, detectedBarcode, uploadPreparation.await())
                    } catch (error: Throwable) {
                        uploadPreparation.cancel()
                        runCatching { uploadPreparation.await().close() }
                        throw error
                    }
                }
            }
            if (requestId != imageAnalysisRequestId) {
                result.getOrNull()?.third?.close()
                return@launch
            }
            result.onSuccess { (suggestion, detectedBarcode, upload) ->
                preparedUpload = upload
                _suggestion.value = suggestion
                _barcode.value = detectedBarcode
                _analysisMessage.value =
                    if (detectedBarcode != null) {
                        "쿠폰 정보와 ${detectedBarcode.format} 바코드를 찾았고 업로드 준비도 마쳤습니다."
                    } else if (suggestion.title != null || suggestion.brand != null || suggestion.expiresLocalDate != null) {
                        "읽은 정보로 입력값을 채우고 업로드 준비를 마쳤습니다. 정확한지 확인해 주세요."
                    } else {
                        "자동으로 읽을 정보는 부족하지만 업로드 준비를 마쳤습니다."
                    }
            }.onFailure {
                if (it !is CancellationException) {
                    _analysisMessage.value = it.localizedMessage ?: "이미지를 준비하지 못했습니다."
                }
            }

            _analysisBusy.value = false
            imageAnalysisJob = null
        }
    }

    fun addCoupon(
        context: Context,
        roomId: String,
        imageUri: Uri?,
        title: String,
        brand: String,
        expiresLocalDate: String,
        visibility: String,
        notifyTarget: String,
        barcodeValue: String?,
        barcodeFormat: String?,
        onAdded: (String) -> Unit
    ) {
        if (_busy.value) return
        addCouponJob = viewModelScope.launch {
            _busy.value = true
            _message.value = null
            _uploadState.value = CouponUploadState(CouponUploadStage.Preparing)
            runCatching {
                require(imageUri != null) { "쿠폰 이미지를 선택해 주세요." }
                require(title.isNotBlank()) { "쿠폰 이름을 입력해 주세요." }
                val date = LocalDate.parse(expiresLocalDate)
                val barcode = if (!barcodeValue.isNullOrBlank() && !barcodeFormat.isNullOrBlank()) {
                    val normalizedValue = barcodeValue.trim()
                    val normalizedFormat = barcodeFormat.trim()
                    couponBarcodeValidationError(normalizedValue, normalizedFormat)?.let { error(it) }
                    val renderable = withContext(Dispatchers.Default) {
                        renderCouponBarcode(normalizedValue, normalizedFormat)
                    }
                    require(renderable != null) { "바코드 값과 형식을 다시 확인해 주세요." }
                    renderable.recycle()
                    DetectedCouponBarcode(normalizedValue, normalizedFormat)
                } else {
                    null
                }
                val upload = preparedUpload
                preparedUpload = null
                val couponId = repository.addCoupon(
                    context = context,
                    roomId = roomId,
                    imageUri = imageUri,
                    title = title,
                    brand = brand,
                    expiresLocalDate = date,
                    visibility = visibility,
                    notifyTarget = notifyTarget,
                    barcode = barcode,
                    preparedUpload = upload,
                    onUploadPrepared = { preparation ->
                        _uploadState.value = preparation.toUploadState()
                    },
                    onUploadProgress = { sentBytes, totalBytes ->
                        val percent = totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { ((sentBytes * 100L) / it).toInt().coerceIn(0, 100) }
                        _uploadState.value = _uploadState.value.copy(
                            stage = CouponUploadStage.Uploading,
                            percent = percent
                        )
                    },
                    onImageUploaded = {
                        _uploadState.value = _uploadState.value.copy(
                            stage = CouponUploadStage.Saving,
                            percent = 100
                        )
                    }
                )
                onAdded(couponId)
            }.onFailure {
                _message.value = if (it is CancellationException) {
                    "이미지 업로드를 취소했습니다. 전송된 임시 파일이 있으면 자동 정리합니다."
                } else {
                    it.localizedMessage ?: "쿠폰을 추가하지 못했습니다."
                }
            }
            _busy.value = false
            _uploadState.value = CouponUploadState()
            addCouponJob = null
        }
    }

    fun cancelUpload() {
        if (_uploadState.value.stage !in setOf(CouponUploadStage.Preparing, CouponUploadStage.Uploading)) return
        _uploadState.value = CouponUploadState(CouponUploadStage.Cancelling)
        addCouponJob?.cancel()
    }

    override fun onCleared() {
        imageAnalysisJob?.cancel()
        preparedUpload?.close()
        preparedUpload = null
        super.onCleared()
    }
}

class CouponDetailViewModel(
    private val repository: CouponRepository = CouponRepository(),
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {
    private var couponJob: Job? = null
    private var commentsJob: Job? = null
    private var roomJob: Job? = null
    private var imageJob: Job? = null
    private var currentImageFile: CouponImageFile? = null
    private var replacementPreparationJob: Job? = null
    private var replacementPreparationRequestId = 0L
    private var preparedReplacementUpload: PreparedCouponUpload? = null

    private val _coupon = MutableStateFlow<UiState<Coupon>>(UiState.Loading)
    val coupon: StateFlow<UiState<Coupon>> = _coupon

    private val _comments = MutableStateFlow<UiState<List<CouponComment>>>(UiState.Loading)
    val comments: StateFlow<UiState<List<CouponComment>>> = _comments

    private val _originalImage = MutableStateFlow<CouponOriginalImageState>(CouponOriginalImageState.Idle)
    val originalImage: StateFlow<CouponOriginalImageState> = _originalImage

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _commentBusy = MutableStateFlow(false)
    val commentBusy: StateFlow<Boolean> = _commentBusy

    private val _busyAction = MutableStateFlow<String?>(null)
    val busyAction: StateFlow<String?> = _busyAction

    private val _imageReplaceState = MutableStateFlow(CouponUploadState())
    val imageReplaceState: StateFlow<CouponUploadState> = _imageReplaceState

    private val _roomOwnerUid = MutableStateFlow<String?>(null)
    val roomOwnerUid: StateFlow<String?> = _roomOwnerUid

    val currentUid: String?
        get() = repository.currentUid

    fun start(roomId: String, couponId: String) {
        if (couponJob != null) return
        couponJob = viewModelScope.launch {
            repository.observeCoupon(roomId, couponId)
                .catch { _coupon.value = UiState.Error(it.localizedMessage ?: "쿠폰을 불러오지 못했습니다.") }
                .collect { coupon ->
                    _coupon.value = coupon?.let { UiState.Success(it) } ?: UiState.Error("쿠폰을 찾을 수 없습니다.")
                }
        }
        commentsJob = viewModelScope.launch {
            repository.observeComments(roomId, couponId)
                .catch { _comments.value = UiState.Error(it.localizedMessage ?: "댓글을 불러오지 못했습니다.") }
                .collect { _comments.value = UiState.Success(it) }
        }
        roomJob = viewModelScope.launch {
            roomRepository.observeRoom(roomId)
                .catch { _roomOwnerUid.value = null }
                .collect { _roomOwnerUid.value = it?.ownerUid }
        }
    }

    fun loadOriginalImage(
        context: Context,
        roomId: String,
        couponId: String,
        force: Boolean = false
    ) {
        if (!shouldStartOriginalImageLoad(_originalImage.value, force)) return
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            _originalImage.value = CouponOriginalImageState.Loading
            runCatching { repository.fetchImageToFile(context, roomId, couponId) }
                .onSuccess { downloadedImage ->
                    val previousImage = currentImageFile
                    currentImageFile = downloadedImage
                    _originalImage.value = CouponOriginalImageState.Ready(downloadedImage)
                    CouponImageFileStore.delete(previousImage)
                }
                .onFailure {
                    if (it is CancellationException) return@onFailure
                    _originalImage.value = CouponOriginalImageState.Error(
                        it.localizedMessage ?: "이미지를 불러오지 못했습니다."
                    )
                }
        }
    }

    fun cancelOriginalImageLoad() {
        if (!shouldCancelOriginalImageLoad(_originalImage.value)) return
        imageJob?.cancel()
        imageJob = null
        _originalImage.value = CouponOriginalImageState.Idle
    }

    fun reserve(roomId: String, couponId: String) =
        runAction("reserve", "쿠폰을 예약했습니다.") { repository.reserve(roomId, couponId) }

    fun cancelReservation(roomId: String, couponId: String) =
        runAction("cancelReservation", "예약을 취소했습니다.") { repository.cancelReservation(roomId, couponId) }

    fun markUsed(roomId: String, couponId: String, onSuccess: () -> Unit = {}) =
        runAction("markUsed", onSuccess = onSuccess) { repository.markUsed(roomId, couponId) }

    fun undoMarkUsed(roomId: String, couponId: String) =
        runAction("undoMarkUsed", "사용 가능한 쿠폰으로 되돌렸습니다.") {
            repository.undoMarkUsed(roomId, couponId)
        }

    fun delete(roomId: String, couponId: String, onDeleted: (DeletedCoupon) -> Unit) {
        if (_busyAction.value != null) return
        _busyAction.value = "delete"
        viewModelScope.launch {
            _message.value = null
            runCatching { repository.deleteCoupon(roomId, couponId) }
                .onSuccess(onDeleted)
                .onFailure { _message.value = it.localizedMessage ?: "쿠폰을 삭제하지 못했습니다." }
            _busyAction.value = null
        }
    }

    fun addComment(roomId: String, couponId: String, body: String, onAdded: () -> Unit) {
        if (_commentBusy.value) return
        _commentBusy.value = true
        viewModelScope.launch {
            _message.value = null
            runCatching { repository.addComment(roomId, couponId, body) }
                .onSuccess { onAdded() }
                .onFailure { _message.value = it.localizedMessage ?: "댓글을 등록하지 못했습니다." }
            _commentBusy.value = false
        }
    }

    fun deleteComment(roomId: String, couponId: String, commentId: String) {
        viewModelScope.launch {
            _message.value = null
            runCatching { repository.deleteComment(roomId, couponId, commentId) }
                .onFailure { _message.value = it.localizedMessage ?: "댓글을 삭제하지 못했습니다." }
        }
    }

    fun edit(
        roomId: String,
        couponId: String,
        title: String,
        brand: String,
        expiresLocalDate: String,
        visibility: String,
        notifyTarget: String,
        onSaved: () -> Unit
    ) = runAction("edit", "쿠폰 정보를 수정했습니다.", onSaved) {
        repository.editCoupon(
            roomId,
            couponId,
            title,
            brand,
            LocalDate.parse(expiresLocalDate),
            visibility,
            notifyTarget
        )
    }

    fun prepareReplacementImage(context: Context, imageUri: Uri) {
        if (_busyAction.value != null) return
        _message.value = null
        replacementPreparationJob?.cancel()
        preparedReplacementUpload?.close()
        preparedReplacementUpload = null
        val requestId = ++replacementPreparationRequestId
        _imageReplaceState.value = CouponUploadState(CouponUploadStage.Preparing)
        replacementPreparationJob = viewModelScope.launch {
            val result = runCatching {
                repository.prepareCouponImage(context.applicationContext, imageUri)
            }
            if (requestId != replacementPreparationRequestId) {
                result.getOrNull()?.close()
                return@launch
            }
            result.onSuccess { upload ->
                preparedReplacementUpload = upload
                _imageReplaceState.value = upload.preparation.toUploadState().copy(
                    stage = CouponUploadStage.Idle,
                    percent = null
                )
            }.onFailure { error ->
                _imageReplaceState.value = CouponUploadState()
                if (error !is CancellationException) {
                    _message.value = error.localizedMessage ?: "새 이미지를 준비하지 못했습니다."
                }
            }
            replacementPreparationJob = null
        }
    }

    fun discardReplacementImage() {
        if (_busyAction.value != null) return
        replacementPreparationRequestId += 1
        replacementPreparationJob?.cancel()
        replacementPreparationJob = null
        preparedReplacementUpload?.close()
        preparedReplacementUpload = null
        _imageReplaceState.value = CouponUploadState()
    }

    fun replaceImage(
        context: Context,
        roomId: String,
        couponId: String,
        imageUri: Uri,
        onReplaced: () -> Unit
    ) {
        if (_busyAction.value != null) return
        _busyAction.value = "replaceImage"
        replacementPreparationRequestId += 1
        replacementPreparationJob?.cancel()
        replacementPreparationJob = null
        val upload = preparedReplacementUpload
        preparedReplacementUpload = null
        _imageReplaceState.value = upload?.preparation?.toUploadState()
            ?: CouponUploadState(CouponUploadStage.Preparing)
        viewModelScope.launch {
            _message.value = null
            runCatching {
                repository.replaceCouponImage(
                    context = context,
                    roomId = roomId,
                    couponId = couponId,
                    imageUri = imageUri,
                    preparedUpload = upload,
                    onUploadPrepared = { preparation ->
                        _imageReplaceState.value = preparation.toUploadState()
                    },
                    onUploadProgress = { sentBytes, totalBytes ->
                        val percent = totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { ((sentBytes * 100L) / it).toInt().coerceIn(0, 100) }
                        _imageReplaceState.value = _imageReplaceState.value.copy(
                            stage = if (percent == 100) CouponUploadStage.Saving else CouponUploadStage.Uploading,
                            percent = percent
                        )
                    }
                )
            }.onSuccess { cleanupPending ->
                _message.value = if (cleanupPending) {
                    "이미지를 교체했습니다. 이전 파일 정리가 지연되고 있습니다."
                } else {
                    "쿠폰 이미지를 교체했습니다."
                }
                loadOriginalImage(context, roomId, couponId, force = true)
                onReplaced()
            }.onFailure {
                _message.value = it.localizedMessage ?: "쿠폰 이미지를 교체하지 못했습니다."
            }
            _busyAction.value = null
            _imageReplaceState.value = CouponUploadState()
        }
    }

    private fun runAction(
        action: String,
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit
    ) {
        if (_busyAction.value != null) return
        _busyAction.value = action
        viewModelScope.launch {
            _message.value = null
            runCatching { block() }
                .onSuccess {
                    _message.value = successMessage
                    onSuccess()
                }
                .onFailure { _message.value = it.localizedMessage ?: "요청에 실패했습니다." }
            _busyAction.value = null
        }
    }

    override fun onCleared() {
        imageJob?.cancel()
        replacementPreparationJob?.cancel()
        preparedReplacementUpload?.close()
        preparedReplacementUpload = null
        CouponImageFileStore.delete(currentImageFile)
        currentImageFile = null
        super.onCleared()
    }
}

class CouponTrashViewModel(
    private val repository: CouponRepository = CouponRepository()
) : ViewModel() {
    private val _coupons = MutableStateFlow<UiState<List<DeletedCoupon>>>(UiState.Loading)
    val coupons: StateFlow<UiState<List<DeletedCoupon>>> = _coupons

    private val _busyCouponId = MutableStateFlow<String?>(null)
    val busyCouponId: StateFlow<String?> = _busyCouponId

    private val _busyAction = MutableStateFlow<String?>(null)
    val busyAction: StateFlow<String?> = _busyAction

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore

    private val _pagingError = MutableStateFlow<String?>(null)
    val pagingError: StateFlow<String?> = _pagingError

    private var nextCursor: String? = null

    fun load(roomId: String) {
        viewModelScope.launch { refresh(roomId) }
    }

    fun loadMore(roomId: String) {
        val cursor = nextCursor ?: return
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        _pagingError.value = null
        viewModelScope.launch {
            runCatching { repository.deletedCoupons(roomId, cursor) }
                .onSuccess { page ->
                    val current = (_coupons.value as? UiState.Success)?.data.orEmpty()
                    _coupons.value = UiState.Success((current + page.coupons).distinctBy(DeletedCoupon::couponId))
                    nextCursor = page.nextCursor
                    _hasMore.value = page.nextCursor != null
                }
                .onFailure { _pagingError.value = it.localizedMessage ?: "다음 쿠폰을 불러오지 못했습니다." }
            _isLoadingMore.value = false
        }
    }

    fun retry(roomId: String) {
        if (_pagingError.value != null && nextCursor != null) loadMore(roomId) else load(roomId)
    }

    fun restore(roomId: String, couponId: String) = runCouponAction(roomId, couponId, "restore") {
        repository.restoreDeletedCoupon(roomId, couponId)
        "쿠폰을 목록으로 복원했습니다."
    }

    fun permanentlyDelete(roomId: String, couponId: String) = runCouponAction(roomId, couponId, "delete") {
        val cleanupPending = repository.permanentlyDeleteCoupon(roomId, couponId)
        if (cleanupPending) {
            "쿠폰은 영구 삭제됐고 이미지 정리는 재시도됩니다."
        } else {
            "쿠폰을 영구 삭제했습니다."
        }
    }

    private fun runCouponAction(
        roomId: String,
        couponId: String,
        action: String,
        block: suspend () -> String
    ) {
        if (_busyCouponId.value != null) return
        _busyCouponId.value = couponId
        _busyAction.value = action
        viewModelScope.launch {
            _message.value = null
            runCatching { block() }
                .onSuccess {
                    _message.value = it
                    val current = (_coupons.value as? UiState.Success)?.data.orEmpty()
                    _coupons.value = UiState.Success(current.filterNot { coupon -> coupon.couponId == couponId })
                }
                .onFailure { _message.value = it.localizedMessage ?: "요청에 실패했습니다." }
            _busyCouponId.value = null
            _busyAction.value = null
        }
    }

    private suspend fun refresh(roomId: String) {
        _message.value = null
        _coupons.value = UiState.Loading
        _pagingError.value = null
        _isLoadingMore.value = false
        nextCursor = null
        _hasMore.value = false
        runCatching { repository.deletedCoupons(roomId) }
            .onSuccess { page ->
                _coupons.value = UiState.Success(page.coupons)
                nextCursor = page.nextCursor
                _hasMore.value = page.nextCursor != null
            }
            .onFailure { _coupons.value = UiState.Error(it.localizedMessage ?: "복구함을 불러오지 못했습니다.") }
    }
}

class MemberListViewModel(
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {
    private var job: Job? = null
    private val _members = MutableStateFlow<UiState<List<RoomMember>>>(UiState.Loading)
    val members: StateFlow<UiState<List<RoomMember>>> = _members

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun start(roomId: String) {
        if (job != null) return
        job = viewModelScope.launch {
            roomRepository.observeMembers(roomId)
                .catch { _members.value = UiState.Error(it.localizedMessage ?: "멤버 목록을 불러오지 못했습니다.") }
                .collect { _members.value = UiState.Success(it) }
        }
    }

    fun removeMember(roomId: String, uid: String) {
        viewModelScope.launch {
            runCatching { roomRepository.removeMember(roomId, uid) }
                .onFailure { _message.value = it.localizedMessage ?: "멤버를 제거하지 못했습니다." }
        }
    }
}

class SettingsViewModel(
    private val notificationRepository: NotificationRepository = NotificationRepository(),
    private val roomRepository: RoomRepository = RoomRepository()
) : ViewModel() {
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _busyAction = MutableStateFlow<String?>(null)
    val busyAction: StateFlow<String?> = _busyAction

    private val _testPushBusy = MutableStateFlow(false)
    val testPushBusy: StateFlow<Boolean> = _testPushBusy

    private val _expiryTestPushBusy = MutableStateFlow(false)
    val expiryTestPushBusy: StateFlow<Boolean> = _expiryTestPushBusy

    private val _defaultMode = MutableStateFlow(NotificationMode.Basic)
    val defaultMode: StateFlow<NotificationMode> = _defaultMode

    private val _defaultPushEnabled = MutableStateFlow(true)
    val defaultPushEnabled: StateFlow<Boolean> = _defaultPushEnabled

    fun loadDefaultSettings() {
        viewModelScope.launch {
            runCatching { notificationRepository.currentDefault() }
                .onSuccess {
                    _defaultMode.value = NotificationMode.fromWire(it.mode)
                    _defaultPushEnabled.value = it.pushEnabled
                }
        }
    }

    fun updateDefault(mode: NotificationMode, pushEnabled: Boolean) {
        runSettingsAction("default") {
            notificationRepository.updateDefault(mode.wire, mode.days, pushEnabled)
            _defaultMode.value = mode
            _defaultPushEnabled.value = pushEnabled
            _message.value = "알림 설정을 저장했습니다."
        }
    }

    fun regenerateInvite(roomId: String) {
        runSettingsAction("invite") {
            val code = roomRepository.regenerateInvite(roomId)
            _message.value = "초대코드를 새로 만들었습니다: $code"
        }
    }

    fun leaveRoom(roomId: String, onLeft: () -> Unit) {
        runSettingsAction("leave") {
            roomRepository.leaveRoom(roomId)
            onLeft()
        }
    }

    fun deleteRoom(roomId: String, onDeleted: () -> Unit) {
        runSettingsAction("deleteRoom") {
            roomRepository.deleteRoom(roomId)
            onDeleted()
        }
    }

    fun sendTestPush() {
        viewModelScope.launch {
            _testPushBusy.value = true
            _message.value = null
            runCatching { notificationRepository.sendTestPush() }
                .onSuccess { sent ->
                    _message.value = if (sent > 0) {
                        "권한·FCM 등록·서버 전송을 확인했습니다. 잠시 후 알림을 확인해 주세요."
                    } else {
                        "서버 요청은 처리됐지만 유효한 FCM 토큰으로 전송되지 않았습니다."
                    }
                }
                .onFailure {
                    _message.value = it.localizedMessage ?: "테스트 푸시 전송에 실패했습니다."
                }
            _testPushBusy.value = false
        }
    }

    fun sendExpiryReminderTestPush() {
        viewModelScope.launch {
            _expiryTestPushBusy.value = true
            _message.value = null
            runCatching { notificationRepository.sendExpiryReminderTestPush() }
                .onSuccess { sent ->
                    _message.value = if (sent > 0) {
                        "만료 알림 형식의 테스트 푸시를 보냈습니다."
                    } else {
                        "요청은 처리됐지만 전송된 토큰이 없습니다."
                    }
                }
                .onFailure {
                    _message.value = it.localizedMessage ?: "만료 알림 테스트 전송에 실패했습니다."
                }
            _expiryTestPushBusy.value = false
        }
    }

    private fun runSettingsAction(action: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            _busyAction.value = action
            _message.value = null
            runCatching { block() }
                .onFailure { _message.value = it.localizedMessage ?: "요청에 실패했습니다." }
            _busyAction.value = null
            _busy.value = false
        }
    }
}
