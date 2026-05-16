package com.giftcondoctor.app.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.giftcondoctor.app.core.NotificationMode
import com.giftcondoctor.app.core.UiState
import com.giftcondoctor.app.data.AuthRepository
import com.giftcondoctor.app.data.CouponRepository
import com.giftcondoctor.app.data.NotificationRepository
import com.giftcondoctor.app.data.PushTokenRepository
import com.giftcondoctor.app.data.RoomRepository
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.time.LocalDate

class SessionViewModel(
    private val authRepository: AuthRepository = AuthRepository(),
    private val pushTokenRepository: PushTokenRepository = PushTokenRepository()
) : ViewModel() {
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val currentUid: String?
        get() = authRepository.currentUid

    init {
        viewModelScope.launch {
            authRepository.authState().collect {
                _isLoggedIn.value = it
                if (it) runCatching { pushTokenRepository.saveCurrentToken() }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    fun signIn(email: String, password: String) = runAuth {
        authRepository.signInWithEmail(email, password)
        pushTokenRepository.saveCurrentToken()
    }

    fun createAccount(email: String, password: String) = runAuth {
        authRepository.createAccount(email, password)
        pushTokenRepository.saveCurrentToken()
    }

    fun signInWithGoogle(context: Context) = runAuth {
        authRepository.signInWithGoogle(context)
        pushTokenRepository.saveCurrentToken()
    }

    fun signOut() {
        authRepository.signOut()
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

    init {
        viewModelScope.launch {
            repository.observeMemberships()
                .catch { _rooms.value = UiState.Error(it.localizedMessage ?: "방 목록을 불러오지 못했습니다.") }
                .collect { _rooms.value = UiState.Success(it) }
        }
    }

    fun createRoom(name: String, onCreated: (String) -> Unit) = runAction {
        val roomId = repository.createRoom(name)
        onCreated(roomId)
    }

    fun joinRoom(inviteCode: String, onJoined: (String) -> Unit) = runAction {
        val roomId = repository.joinRoom(inviteCode)
        onJoined(roomId)
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

    private val _room = MutableStateFlow<UiState<Room>>(UiState.Loading)
    val room: StateFlow<UiState<Room>> = _room

    private val _coupons = MutableStateFlow<UiState<List<Coupon>>>(UiState.Loading)
    val coupons: StateFlow<UiState<List<Coupon>>> = _coupons

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun start(roomId: String) {
        if (roomJob != null) return
        roomJob = viewModelScope.launch {
            roomRepository.observeRoom(roomId)
                .catch { _room.value = UiState.Error(it.localizedMessage ?: "방 정보를 불러오지 못했습니다.") }
                .collect { room ->
                    _room.value = room?.let { UiState.Success(it) } ?: UiState.Error("방을 찾을 수 없습니다.")
                }
        }
        couponJob = viewModelScope.launch {
            couponRepository.observeCoupons(roomId)
                .catch { _coupons.value = UiState.Error(it.localizedMessage ?: "쿠폰 목록을 불러오지 못했습니다.") }
                .collect { _coupons.value = UiState.Success(it) }
        }
    }
}

class AddCouponViewModel(
    private val repository: CouponRepository = CouponRepository()
) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun addCoupon(
        context: Context,
        roomId: String,
        imageUri: Uri?,
        title: String,
        brand: String,
        expiresLocalDate: String,
        visibility: String,
        notifyTarget: String,
        onAdded: (String) -> Unit
    ) {
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching {
                require(imageUri != null) { "쿠폰 이미지를 선택해 주세요." }
                require(title.isNotBlank()) { "쿠폰 이름을 입력해 주세요." }
                val date = LocalDate.parse(expiresLocalDate)
                val couponId = repository.addCoupon(
                    context = context,
                    roomId = roomId,
                    imageUri = imageUri,
                    title = title,
                    brand = brand,
                    expiresLocalDate = date,
                    visibility = visibility,
                    notifyTarget = notifyTarget
                )
                onAdded(couponId)
            }.onFailure {
                _message.value = it.localizedMessage ?: "쿠폰을 추가하지 못했습니다."
            }
            _busy.value = false
        }
    }
}

class CouponDetailViewModel(
    private val repository: CouponRepository = CouponRepository()
) : ViewModel() {
    private var couponJob: Job? = null

    private val _coupon = MutableStateFlow<UiState<Coupon>>(UiState.Loading)
    val coupon: StateFlow<UiState<Coupon>> = _coupon

    private val _imageBytes = MutableStateFlow<UiState<ByteArray>>(UiState.Loading)
    val imageBytes: StateFlow<UiState<ByteArray>> = _imageBytes

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun start(roomId: String, couponId: String) {
        if (couponJob != null) return
        couponJob = viewModelScope.launch {
            repository.observeCoupon(roomId, couponId)
                .catch { _coupon.value = UiState.Error(it.localizedMessage ?: "쿠폰을 불러오지 못했습니다.") }
                .collect { coupon ->
                    _coupon.value = coupon?.let { UiState.Success(it) } ?: UiState.Error("쿠폰을 찾을 수 없습니다.")
                }
        }
        refreshImage(roomId, couponId)
    }

    fun refreshImage(roomId: String, couponId: String) {
        viewModelScope.launch {
            _imageBytes.value = UiState.Loading
            runCatching { repository.fetchImage(roomId, couponId) }
                .onSuccess { _imageBytes.value = UiState.Success(it) }
                .onFailure { _imageBytes.value = UiState.Error(it.localizedMessage ?: "이미지를 불러오지 못했습니다.") }
        }
    }

    fun reserve(roomId: String, couponId: String) = runAction { repository.reserve(roomId, couponId) }
    fun cancelReservation(roomId: String, couponId: String) = runAction { repository.cancelReservation(roomId, couponId) }
    fun markUsed(roomId: String, couponId: String) = runAction { repository.markUsed(roomId, couponId) }
    fun delete(roomId: String, couponId: String, onDeleted: () -> Unit) = runAction {
        repository.deleteCoupon(roomId, couponId)
        onDeleted()
    }

    fun edit(
        roomId: String,
        couponId: String,
        title: String,
        brand: String,
        expiresLocalDate: String,
        visibility: String,
        notifyTarget: String
    ) = runAction {
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

    private fun runAction(block: suspend () -> Unit) {
        viewModelScope.launch {
            _message.value = null
            runCatching { block() }
                .onFailure { _message.value = it.localizedMessage ?: "요청에 실패했습니다." }
        }
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

    fun updateDefault(mode: NotificationMode, pushEnabled: Boolean) {
        viewModelScope.launch {
            runCatching { notificationRepository.updateDefault(mode.wire, mode.days, pushEnabled) }
                .onSuccess { _message.value = "알림 설정을 저장했습니다." }
                .onFailure { _message.value = it.localizedMessage ?: "알림 설정 저장에 실패했습니다." }
        }
    }

    fun updateRoom(roomId: String, mode: NotificationMode) {
        viewModelScope.launch {
            runCatching { roomRepository.updateRoomNotification(roomId, mode.wire, mode.days) }
                .onSuccess { _message.value = "방 알림 기본값을 저장했습니다." }
                .onFailure { _message.value = it.localizedMessage ?: "방 설정 저장에 실패했습니다." }
        }
    }

    fun updateMember(roomId: String, enabled: Boolean, mode: NotificationMode?) {
        viewModelScope.launch {
            runCatching { roomRepository.updateMemberNotification(roomId, enabled, mode?.wire, mode?.days) }
                .onSuccess { _message.value = "내 방 알림 설정을 저장했습니다." }
                .onFailure { _message.value = it.localizedMessage ?: "방 알림 설정 저장에 실패했습니다." }
        }
    }

    fun regenerateInvite(roomId: String) {
        viewModelScope.launch {
            runCatching { roomRepository.regenerateInvite(roomId) }
                .onSuccess { _message.value = "초대코드를 새로 만들었습니다: $it" }
                .onFailure { _message.value = it.localizedMessage ?: "초대코드 재발급에 실패했습니다." }
        }
    }

    fun leaveRoom(roomId: String, onLeft: () -> Unit) {
        viewModelScope.launch {
            runCatching { roomRepository.leaveRoom(roomId) }
                .onSuccess { onLeft() }
                .onFailure { _message.value = it.localizedMessage ?: "방 나가기에 실패했습니다." }
        }
    }
}
