package com.giftcondoctor.app.data

import android.content.Context
import android.net.Uri
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.CouponComment
import com.giftcondoctor.app.data.model.expiresAtUtcForSeoulDate
import com.giftcondoctor.app.data.model.toCoupon
import com.giftcondoctor.app.data.model.toCouponComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

class CouponRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val backend: BackendClient = BackendClient()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun observeCoupons(roomId: String): Flow<List<Coupon>> = callbackFlow {
        val uid = auth.currentUser?.uid
        val coupons = mutableMapOf<String, Coupon>()
        val publicIds = mutableSetOf<String>()
        val privateIds = mutableSetOf<String>()

        fun emit() {
            trySend(coupons.values.sortedWith(compareBy<Coupon> { it.expiresLocalDate }.thenBy { it.title }))
        }

        val publicRegistration = firestore.collection("rooms/$roomId/coupons")
            .whereEqualTo("visibility", "room")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                publicIds.forEach { coupons.remove(it) }
                publicIds.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toCoupon(roomId)?.let {
                        coupons[it.id] = it
                        publicIds += it.id
                    }
                }
                emit()
            }

        val privateRegistration = uid?.let {
            firestore.collection("rooms/$roomId/coupons")
                .whereEqualTo("visibility", "private")
                .whereEqualTo("ownerUid", it)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    privateIds.forEach { id -> coupons.remove(id) }
                    privateIds.clear()
                    snapshot?.documents?.forEach { doc ->
                        doc.toCoupon(roomId)?.let { coupon ->
                            coupons[coupon.id] = coupon
                            privateIds += coupon.id
                        }
                    }
                    emit()
                }
        }

        awaitClose {
            publicRegistration.remove()
            privateRegistration?.remove()
        }
    }

    fun observeCoupon(roomId: String, couponId: String): Flow<Coupon?> = callbackFlow {
        val registration = firestore.document("rooms/$roomId/coupons/$couponId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toCoupon(roomId))
            }
        awaitClose { registration.remove() }
    }

    fun observeComments(roomId: String, couponId: String): Flow<List<CouponComment>> = callbackFlow {
        val registration = firestore.collection("rooms/$roomId/coupons/$couponId/comments")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toCouponComment() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun addCoupon(
        context: Context,
        roomId: String,
        imageUri: Uri,
        title: String,
        brand: String,
        expiresLocalDate: LocalDate,
        visibility: String,
        notifyTarget: String
    ): String {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val contentType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        require(contentType.startsWith("image/")) { "이미지 파일만 선택할 수 있습니다." }

        val couponId = firestore.collection("rooms/$roomId/coupons").document().id
        val upload = backend.uploadCouponImage(
            context = context,
            roomId = roomId,
            couponId = couponId,
            imageUri = imageUri,
            contentType = contentType,
            fileName = imageUri.lastPathSegment ?: "coupon-image"
        )

        val now = FieldValue.serverTimestamp()
        firestore.document("rooms/$roomId/coupons/$couponId").set(
            mapOf(
                "title" to title.trim(),
                "brand" to brand.trim(),
                "ownerUid" to uid,
                "imageBlobPath" to upload.blobPath,
                "imageWidth" to upload.imageWidth,
                "imageHeight" to upload.imageHeight,
                "expiresLocalDate" to expiresLocalDate.toString(),
                "expiresAtUtc" to expiresAtUtcForSeoulDate(expiresLocalDate),
                "timezone" to AppConstants.SEOUL_TIME_ZONE,
                "status" to "active",
                "reservedByUid" to null,
                "usedByUid" to null,
                "usedAt" to null,
                "visibility" to visibility,
                "notifyTarget" to notifyTarget,
                "createdAt" to now,
                "updatedAt" to now
            )
        ).await()
        return couponId
    }

    suspend fun reserve(roomId: String, couponId: String) {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val ref = firestore.document("rooms/$roomId/coupons/$couponId")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (snapshot.getString("status") != "active") error("사용 가능한 쿠폰만 예약할 수 있습니다.")
            transaction.update(ref, mapOf(
                "status" to "reserved",
                "reservedByUid" to uid,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun cancelReservation(roomId: String, couponId: String) {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val ref = firestore.document("rooms/$roomId/coupons/$couponId")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (snapshot.getString("status") != "reserved") error("예약된 쿠폰이 아닙니다.")
            if (snapshot.getString("reservedByUid") != uid && snapshot.getString("ownerUid") != uid) {
                error("예약자 또는 등록자만 예약을 취소할 수 있습니다.")
            }
            transaction.update(ref, mapOf(
                "status" to "active",
                "reservedByUid" to null,
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun markUsed(roomId: String, couponId: String) {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val ref = firestore.document("rooms/$roomId/coupons/$couponId")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val status = snapshot.getString("status")
            if (status != "active" && status != "reserved") error("사용 처리할 수 없는 쿠폰입니다.")
            transaction.update(ref, mapOf(
                "status" to "used",
                "usedByUid" to uid,
                "usedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun editCoupon(
        roomId: String,
        couponId: String,
        title: String,
        brand: String,
        expiresLocalDate: LocalDate,
        visibility: String,
        notifyTarget: String
    ) {
        firestore.document("rooms/$roomId/coupons/$couponId").update(
            mapOf(
                "title" to title.trim(),
                "brand" to brand.trim(),
                "expiresLocalDate" to expiresLocalDate.toString(),
                "expiresAtUtc" to expiresAtUtcForSeoulDate(expiresLocalDate),
                "timezone" to AppConstants.SEOUL_TIME_ZONE,
                "visibility" to visibility,
                "notifyTarget" to notifyTarget,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun deleteCoupon(roomId: String, couponId: String) {
        backend.deleteCoupon(roomId, couponId)
    }

    suspend fun addComment(roomId: String, couponId: String, body: String) {
        val user = auth.currentUser ?: error("로그인이 필요합니다.")
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "댓글 내용을 입력해 주세요." }
        require(trimmed.length <= 500) { "댓글은 500자까지 입력할 수 있습니다." }

        val now = FieldValue.serverTimestamp()
        firestore.collection("rooms/$roomId/coupons/$couponId/comments").add(
            mapOf(
                "authorUid" to user.uid,
                "authorName" to (user.displayName ?: user.email ?: "이름 없음"),
                "authorPhotoUrl" to user.photoUrl?.toString(),
                "body" to trimmed,
                "createdAt" to now,
                "updatedAt" to now
            )
        ).await()
    }

    suspend fun deleteComment(roomId: String, couponId: String, commentId: String) {
        firestore.document("rooms/$roomId/coupons/$couponId/comments/$commentId").delete().await()
    }

    suspend fun fetchImage(roomId: String, couponId: String): ByteArray =
        backend.fetchCouponImage(roomId, couponId)
}
