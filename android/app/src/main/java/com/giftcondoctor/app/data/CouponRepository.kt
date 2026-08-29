package com.giftcondoctor.app.data

import android.content.Context
import android.net.Uri
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.core.CouponDuplicateCandidate
import com.giftcondoctor.app.core.CouponDuplicateInput
import com.giftcondoctor.app.core.DetectedCouponBarcode
import com.giftcondoctor.app.core.favoriteDocId
import com.giftcondoctor.app.core.findPossibleCouponDuplicates
import com.giftcondoctor.app.data.model.Coupon
import com.giftcondoctor.app.data.model.CouponComment
import com.giftcondoctor.app.data.model.FavoriteRef
import com.giftcondoctor.app.data.model.DeletedCoupon
import com.giftcondoctor.app.data.model.DeletedCouponPage
import com.giftcondoctor.app.data.model.expiresAtUtcForSeoulDate
import com.giftcondoctor.app.data.model.toCoupon
import com.giftcondoctor.app.data.model.toCouponComment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.UUID
import com.giftcondoctor.app.core.resolveDisplayName

private const val DUPLICATE_QUERY_LIMIT_PER_VISIBILITY = 20L

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

    /**
     * 이 방에서 내가 즐겨찾기한 쿠폰 ID 를 관찰한다.
     *
     * 즐겨찾기는 사용자 하위 컬렉션(`users/{uid}/favorites`)에 있고 쿠폰 목록은
     * 방 하위 컬렉션에 있다. 두 스트림을 화면에서 합친다. 서버 조인을 만들지
     * 않는 이유는 즐겨찾기가 보통 몇 건이고, 참조만 담고 있어 합치는 비용이
     * 거의 없기 때문이다.
     *
     * 로그인하지 않았으면 빈 집합을 한 번 내보내고 끝낸다. 오류로 다루면 목록
     * 자체가 뜨지 않는다.
     */
    fun observeFavoriteCouponIds(roomId: String): Flow<Set<String>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users/$uid/favorites")
            .whereEqualTo("roomId", roomId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    // 즐겨찾기 조회가 실패해도 쿠폰 목록은 보여야 한다. 정렬이
                    // 기본으로 돌아갈 뿐 사용자가 할 수 있는 일은 그대로다.
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                val ids = snapshot?.documents.orEmpty()
                    .mapNotNull { it.getString("couponId") }
                    .toSet()
                trySend(ids)
            }
        awaitClose { registration.remove() }
    }

    /**
     * 방을 가리지 않고 내 즐겨찾기 전체를 관찰한다.
     *
     * 방 단위 스트림(`observeFavoriteCouponIds`)만 있으면 방이 여러 개일 때
     * 즐겨찾기도 방마다 흩어진다. 즐겨찾기를 만든 이유가 "자주 쓰는 쿠폰을
     * 빨리 꺼내는 것" 인데, 그러려면 어느 방에 넣었는지 기억하지 않아도 돼야
     * 한다.
     */
    fun observeAllFavorites(): Flow<List<FavoriteRef>> = callbackFlow {
        val uid = currentUid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users/$uid/favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val refs = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val roomId = document.getString("roomId")
                    val couponId = document.getString("couponId")
                    if (roomId.isNullOrBlank() || couponId.isNullOrBlank()) null
                    else FavoriteRef(roomId = roomId, couponId = couponId)
                }
                trySend(refs)
            }
        awaitClose { registration.remove() }
    }

    /**
     * 즐겨찾기가 가리키는 쿠폰들을 한 번에 읽는다.
     *
     * 참조만 저장했으므로 실제 내용은 여기서 따로 읽어야 한다. 참조에 제목을
     * 복사해 두는 대안은 쿠폰을 수정했을 때 즐겨찾기만 옛 정보를 보이게 한다.
     *
     * 읽지 못한 참조는 결과에서 조용히 빠진다. 쿠폰이 삭제됐거나 방에서
     * 나갔을 수 있고, 둘 다 화면을 오류로 덮을 만한 일이 아니다. 대신 몇 건이
     * 빠졌는지는 호출자가 셀 수 있도록 입력과 출력 수를 비교할 수 있게 둔다.
     */
    suspend fun loadFavoriteCoupons(refs: List<FavoriteRef>): List<Coupon> = coroutineScope {
        refs.map { ref ->
            async {
                runCatching {
                    firestore.document("rooms/${ref.roomId}/coupons/${ref.couponId}")
                        .get()
                        .await()
                        .takeIf { it.exists() }
                        ?.toCoupon(ref.roomId)
                }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }

    /**
     * 즐겨찾기를 켜거나 끈다.
     *
     * 문서 ID 를 참조에서 유도하므로 같은 쿠폰을 두 번 담아도 문서가 하나다.
     * 해제는 그 ID 를 지우면 끝이라 "무엇을 지울지" 를 따로 찾지 않아도 된다.
     */
    suspend fun setFavorite(roomId: String, couponId: String, favorite: Boolean) {
        val uid = currentUid ?: throw IllegalStateException("로그인이 필요합니다.")
        val ref = firestore.document("users/$uid/favorites/${favoriteDocId(roomId, couponId)}")
        if (favorite) {
            ref.set(
                mapOf(
                    "roomId" to roomId,
                    "couponId" to couponId,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } else {
            ref.delete().await()
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

    suspend fun findPossibleDuplicates(
        roomId: String,
        title: String,
        brand: String,
        expiresLocalDate: LocalDate,
        barcodeValue: String?
    ): List<CouponDuplicateCandidate> = coroutineScope {
        val coupons = firestore.collection("rooms/$roomId/coupons")
        val roomCoupons = async {
            coupons
                .whereEqualTo("visibility", "room")
                .whereEqualTo("expiresLocalDate", expiresLocalDate.toString())
                .orderBy(FieldPath.documentId())
                .limit(DUPLICATE_QUERY_LIMIT_PER_VISIBILITY)
                .get()
                .await()
                .documents
        }
        val privateCoupons = currentUid?.let { uid ->
            async {
                coupons
                    .whereEqualTo("visibility", "private")
                    .whereEqualTo("ownerUid", uid)
                    .whereEqualTo("expiresLocalDate", expiresLocalDate.toString())
                    .orderBy(FieldPath.documentId())
                    .limit(DUPLICATE_QUERY_LIMIT_PER_VISIBILITY)
                    .get()
                    .await()
                    .documents
            }
        }
        val visibleCoupons = (roomCoupons.await() + privateCoupons?.await().orEmpty())
            .distinctBy { it.id }
            .mapNotNull { it.toCoupon(roomId) }
        findPossibleCouponDuplicates(
            input = CouponDuplicateInput(title, brand, expiresLocalDate, barcodeValue),
            coupons = visibleCoupons
        )
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
        val uid = auth.currentUser?.uid ?: error("로그인이 필요합니다.")
        val contentType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        if (!contentType.startsWith("image/")) {
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

    suspend fun deleteCoupon(roomId: String, couponId: String): DeletedCoupon =
        backend.deleteCoupon(roomId, couponId)

    suspend fun deletedCoupons(roomId: String, cursor: String? = null): DeletedCouponPage =
        backend.deletedCoupons(roomId, cursor)

    suspend fun restoreDeletedCoupon(roomId: String, couponId: String) {
        backend.restoreDeletedCoupon(roomId, couponId)
    }

    suspend fun permanentlyDeleteCoupon(roomId: String, couponId: String): Boolean =
        backend.permanentlyDeleteCoupon(roomId, couponId)

    suspend fun addComment(roomId: String, couponId: String, body: String) {
        val user = auth.currentUser ?: error("로그인이 필요합니다.")
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "댓글 내용을 입력해 주세요." }
        require(trimmed.length <= 500) { "댓글은 500자까지 입력할 수 있습니다." }

        val now = FieldValue.serverTimestamp()
        firestore.collection("rooms/$roomId/coupons/$couponId/comments").add(
            mapOf(
                "authorUid" to user.uid,
                "authorName" to resolveDisplayName(user.displayName, user.email, user.uid),
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
