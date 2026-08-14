package com.giftcondoctor.app.data

import android.content.Context
import android.net.Uri
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.DetectedCouponBarcode
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID

class CouponRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val backend: BackendClient = BackendClient()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun couponPager(
        roomId: String,
        pageSize: Int = DEFAULT_COUPON_PAGE_SIZE_PER_VISIBILITY
    ): CouponPager = CouponPager(roomId, currentUid, firestore, pageSize)

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
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toCouponComment() }?.reversed().orEmpty())
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
        notifyTarget: String,
        barcode: DetectedCouponBarcode? = null,
        preparedUpload: PreparedCouponUpload? = null,
        onUploadPrepared: (CouponUploadPreparation) -> Unit = {},
        onUploadProgress: (sentBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        onImageUploaded: () -> Unit = {}
    ): String {
        val uid = auth.currentUser?.uid ?: run {
            preparedUpload?.close()
            error("로그인이 필요합니다.")
        }
        val contentType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        if (!contentType.startsWith("image/")) {
            preparedUpload?.close()
            error("이미지 파일만 선택할 수 있습니다.")
        }

        val couponId = firestore.collection("rooms/$roomId/coupons").document().id
        val uploadId = UUID.randomUUID().toString()
        var requestStarted = false
        val upload = try {
            backend.uploadCouponImage(
                context = context,
                roomId = roomId,
                couponId = couponId,
                imageUri = imageUri,
                contentType = contentType,
                fileName = imageUri.lastPathSegment ?: "coupon-image",
                uploadId = uploadId,
                preparedUpload = preparedUpload,
                onPrepared = onUploadPrepared,
                onRequestStarted = { requestStarted = true },
                onProgress = onUploadProgress
            )
        } catch (error: Exception) {
            if (requestStarted) {
                withContext(NonCancellable) {
                    runCatching { backend.discardCouponUploadSession(roomId, couponId, uploadId) }
                }
            }
            throw error
        }
        val now = FieldValue.serverTimestamp()
        runCatching {
            onImageUploaded()
            val couponData = mutableMapOf<String, Any?>(
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
            upload.thumbnailBlobPath?.let { couponData["thumbnailBlobPath"] = it }
            barcode?.let {
                couponData["barcodeValue"] = it.value
                couponData["barcodeFormat"] = it.format
            }
            firestore.document("rooms/$roomId/coupons/$couponId").set(couponData).await()
            withContext(NonCancellable) {
                runCatching { backend.completeCouponUploadSession(roomId, couponId, uploadId) }
            }
        }.getOrElse { error ->
            withContext(NonCancellable) {
                runCatching {
                    backend.discardCouponUploadSession(roomId, couponId, uploadId)
                }
            }
            throw error
        }
        return couponId
    }

    suspend fun prepareCouponImage(context: Context, imageUri: Uri): PreparedCouponUpload {
        val contentType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        require(contentType.startsWith("image/")) { "이미지 파일만 선택할 수 있습니다." }
        return backend.prepareCouponImage(
            context = context,
            imageUri = imageUri,
            contentType = contentType,
            fileName = imageUri.lastPathSegment ?: "coupon-image"
        )
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
            if (status == "reserved" && snapshot.getString("reservedByUid") != uid) {
                error("예약한 멤버만 사용 완료로 변경할 수 있습니다.")
            }
            transaction.update(ref, mapOf(
                "status" to "used",
                "reservedByUid" to null,
                "usedByUid" to uid,
                "usedAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun undoMarkUsed(roomId: String, couponId: String) {
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val ref = firestore.document("rooms/$roomId/coupons/$couponId")
        firestore.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (snapshot.getString("status") != "used") error("사용 완료된 쿠폰이 아닙니다.")
            if (snapshot.getString("usedByUid") != uid) error("사용 완료로 변경한 멤버만 실행 취소할 수 있습니다.")
            transaction.update(ref, mapOf(
                "status" to "active",
                "reservedByUid" to null,
                "usedByUid" to null,
                "usedAt" to null,
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

    suspend fun replaceCouponImage(
        context: Context,
        roomId: String,
        couponId: String,
        imageUri: Uri,
        preparedUpload: PreparedCouponUpload? = null,
        onUploadPrepared: (CouponUploadPreparation) -> Unit = {},
        onUploadProgress: (sentBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): Boolean {
        val contentType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        if (!contentType.startsWith("image/")) {
            preparedUpload?.close()
            error("이미지 파일만 선택할 수 있습니다.")
        }
        val upload = backend.replaceCouponImage(
            context = context,
            roomId = roomId,
            couponId = couponId,
            imageUri = imageUri,
            contentType = contentType,
            fileName = imageUri.lastPathSegment ?: "coupon-image",
            preparedUpload = preparedUpload,
            onPrepared = onUploadPrepared,
            onProgress = onUploadProgress
        )
        CouponImageLoader.clear()
        return upload.cleanupPending
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

    suspend fun fetchImage(
        roomId: String,
        couponId: String,
        thumbnail: Boolean = false,
        backfillThumbnail: Boolean = false
    ): ByteArray = backend.fetchCouponImage(roomId, couponId, thumbnail, backfillThumbnail)

    suspend fun fetchImageToFile(
        context: Context,
        roomId: String,
        couponId: String
    ): CouponImageFile {
        val destination = CouponImageFileStore.create(context.applicationContext)
        return try {
            val downloadedBytes = backend.fetchCouponImageToFile(roomId, couponId, destination)
            check(destination.length() == downloadedBytes) { "이미지 다운로드 크기가 일치하지 않습니다." }
            CouponImageFileStore.complete(destination)
        } catch (error: Throwable) {
            CouponImageFileStore.delete(destination)
            throw error
        }
    }
}
