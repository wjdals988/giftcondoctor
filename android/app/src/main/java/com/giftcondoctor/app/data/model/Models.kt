package com.giftcondoctor.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.time.LocalDate
import com.giftcondoctor.app.core.resolveDisplayName

data class UserProfile(
    val uid: String,
    val displayName: String,
    val email: String?,
    val photoUrl: String?,
    val defaultNotificationMode: String = "basic",
    val defaultNotificationDays: List<Int> = listOf(7, 3, 1, 0),
    val pushEnabled: Boolean = true
)

data class RoomMembership(
    val roomId: String,
    val name: String,
    val role: String
)

/**
 * 방을 가로지르는 만료 임박 쿠폰 한 건.
 *
 * 서버가 사용자의 방 목록 범위에서만 모아 내려준다. 클라이언트는 collectionGroup
 * 쿼리를 쓸 수 없어(쿠폰 문서에 멤버 식별자가 없고 규칙이 members/{uid} 존재로
 * 판정한다) 이 화면만은 서버 집계에 의존한다.
 */
data class ExpiringCoupon(
    val roomId: String,
    val roomName: String,
    val couponId: String,
    val title: String,
    val brand: String,
    val expiresLocalDate: String,
    val daysLeft: Int
)

/**
 * @param truncated 상한에 걸려 잘렸는지. 잘린 사실을 숨기면 "전부 봤다" 는 오해를 만든다.
 */
data class ExpiringCoupons(
    val days: Int,
    val coupons: List<ExpiringCoupon>,
    val roomCount: Int,
    val truncated: Boolean
)

data class PublicRoom(
    val roomId: String,
    val name: String,
    val memberCount: Int,
    val alreadyJoined: Boolean
)

data class Room(
    val id: String,
    val name: String,
    val ownerUid: String,
    val inviteCode: String?,
    val inviteExpiresAt: Instant?,
    val defaultNotificationMode: String,
    val defaultNotificationDays: List<Int>
)

data class RoomMember(
    val uid: String,
    val role: String,
    val displayName: String,
    val notificationEnabled: Boolean,
    val notificationMode: String?,
    val notificationDays: List<Int>?
)

data class Coupon(
    val id: String,
    val roomId: String,
    val title: String,
    val brand: String,
    val ownerUid: String,
    val imageBlobPath: String,
    val thumbnailBlobPath: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val expiresLocalDate: LocalDate,
    val timezone: String,
    val status: String,
    val reservedByUid: String?,
    val usedByUid: String?,
    val visibility: String,
    val notifyTarget: String,
    val barcodeValue: String? = null,
    val barcodeFormat: String? = null
)

data class CouponComment(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val body: String,
    val createdAt: Instant?
)

data class DeletedCoupon(
    val couponId: String,
    val title: String,
    val brand: String,
    val expiresLocalDate: LocalDate?,
    val deletedAt: Instant,
    val purgeAt: Instant
)

data class DeletedCouponPage(
    val coupons: List<DeletedCoupon>,
    val nextCursor: String?
)

data class UploadedImage(
    val blobPath: String,
    val thumbnailBlobPath: String?,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val contentType: String,
    val size: Long,
    val cleanupPending: Boolean = false
)

fun DocumentSnapshot.toRoomMembership(): RoomMembership? {
    val roomId = getString("roomId") ?: id
    val name = getString("name") ?: return null
    val role = getString("role") ?: "member"
    return RoomMembership(roomId = roomId, name = name, role = role)
}

fun DocumentSnapshot.toRoom(): Room? {
    val name = getString("name") ?: return null
    val ownerUid = getString("ownerUid") ?: return null
    return Room(
        id = id,
        name = name,
        ownerUid = ownerUid,
        inviteCode = getString("inviteCode"),
        inviteExpiresAt = getTimestamp("inviteExpiresAt")?.toDate()?.toInstant(),
        defaultNotificationMode = getString("defaultNotificationMode") ?: "basic",
        defaultNotificationDays = getLongList("defaultNotificationDays") ?: listOf(7, 3, 1, 0)
    )
}

fun DocumentSnapshot.toRoomMember(): RoomMember? {
    return RoomMember(
        uid = id,
        role = getString("role") ?: "member",
        // 저장된 표시명이 없을 때도 멤버끼리 구분되어야 한다. 방장이 멤버를 제거할 때
        // 확인 다이얼로그가 이 값을 그대로 보여주기 때문이다.
        displayName = resolveDisplayName(getString("displayName"), null, id),
        notificationEnabled = getBoolean("notificationEnabled") ?: true,
        notificationMode = getString("notificationMode"),
        notificationDays = getLongList("notificationDays")
    )
}

fun DocumentSnapshot.toCoupon(roomId: String): Coupon? {
    val expires = getString("expiresLocalDate") ?: return null
    val expiresLocalDate = runCatching { LocalDate.parse(expires) }.getOrNull() ?: return null
    return Coupon(
        id = id,
        roomId = roomId,
        title = getString("title") ?: return null,
        brand = getString("brand") ?: "",
        ownerUid = getString("ownerUid") ?: return null,
        imageBlobPath = getString("imageBlobPath") ?: "",
        thumbnailBlobPath = getString("thumbnailBlobPath"),
        imageWidth = getLong("imageWidth")?.toInt(),
        imageHeight = getLong("imageHeight")?.toInt(),
        expiresLocalDate = expiresLocalDate,
        timezone = getString("timezone") ?: "Asia/Seoul",
        status = getString("status") ?: "active",
        reservedByUid = getString("reservedByUid"),
        usedByUid = getString("usedByUid"),
        visibility = getString("visibility") ?: "room",
        notifyTarget = getString("notifyTarget") ?: "allMembers",
        barcodeValue = getString("barcodeValue"),
        barcodeFormat = getString("barcodeFormat")
    )
}

fun DocumentSnapshot.toCouponComment(): CouponComment? {
    return CouponComment(
        id = id,
        authorUid = getString("authorUid") ?: return null,
        authorName = resolveDisplayName(
            getString("authorName"),
            null,
            getString("authorUid").orEmpty()
        ),
        authorPhotoUrl = getString("authorPhotoUrl"),
        body = getString("body") ?: return null,
        createdAt = getTimestamp("createdAt")?.toDate()?.toInstant()
    )
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.getLongList(field: String): List<Int>? =
    (get(field) as? List<*>)?.mapNotNull {
        when (it) {
            is Long -> it.toInt()
            is Int -> it
            else -> null
        }
    }

fun expiresAtUtcForSeoulDate(date: LocalDate): Timestamp {
    val seoul = java.time.ZoneId.of("Asia/Seoul")
    val instant = date.plusDays(1).atStartOfDay(seoul).minusNanos(1).toInstant()
    return Timestamp(java.util.Date.from(instant))
}
