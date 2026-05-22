package com.giftcondoctor.app.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.time.Instant
import java.time.LocalDate

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
    val imageWidth: Int?,
    val imageHeight: Int?,
    val expiresLocalDate: LocalDate,
    val timezone: String,
    val status: String,
    val reservedByUid: String?,
    val usedByUid: String?,
    val visibility: String,
    val notifyTarget: String
)

data class CouponComment(
    val id: String,
    val authorUid: String,
    val authorName: String,
    val authorPhotoUrl: String?,
    val body: String,
    val createdAt: Instant?
)

data class UploadedImage(
    val blobPath: String,
    val imageWidth: Int?,
    val imageHeight: Int?,
    val contentType: String,
    val size: Long
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
        displayName = getString("displayName") ?: "이름 없음",
        notificationEnabled = getBoolean("notificationEnabled") ?: true,
        notificationMode = getString("notificationMode"),
        notificationDays = getLongList("notificationDays")
    )
}

fun DocumentSnapshot.toCoupon(roomId: String): Coupon? {
    val expires = getString("expiresLocalDate") ?: return null
    return Coupon(
        id = id,
        roomId = roomId,
        title = getString("title") ?: return null,
        brand = getString("brand") ?: "",
        ownerUid = getString("ownerUid") ?: return null,
        imageBlobPath = getString("imageBlobPath") ?: "",
        imageWidth = getLong("imageWidth")?.toInt(),
        imageHeight = getLong("imageHeight")?.toInt(),
        expiresLocalDate = LocalDate.parse(expires),
        timezone = getString("timezone") ?: "Asia/Seoul",
        status = getString("status") ?: "active",
        reservedByUid = getString("reservedByUid"),
        usedByUid = getString("usedByUid"),
        visibility = getString("visibility") ?: "room",
        notifyTarget = getString("notifyTarget") ?: "allMembers"
    )
}

fun DocumentSnapshot.toCouponComment(): CouponComment? {
    return CouponComment(
        id = id,
        authorUid = getString("authorUid") ?: return null,
        authorName = getString("authorName") ?: "이름 없음",
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
