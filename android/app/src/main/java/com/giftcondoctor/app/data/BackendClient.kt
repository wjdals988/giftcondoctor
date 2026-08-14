package com.giftcondoctor.app.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.giftcondoctor.app.BuildConfig
import com.giftcondoctor.app.core.AppConstants
import com.giftcondoctor.app.data.model.PublicRoom
import com.giftcondoctor.app.data.model.UploadedImage
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class BackendClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val client: OkHttpClient = sharedHttpClient
) {
    companion object {
        private val sharedHttpClient = OkHttpClient()
    }
    private val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/')
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createRoom(name: String, isPublic: Boolean, password: String): String {
        val response = postJson(
            "/api/rooms/create",
            JSONObject()
                .put("name", name)
                .put("isPublic", isPublic)
                .put("password", password)
        )
        return JSONObject(response).getString("roomId")
    }

    suspend fun joinRoom(inviteCode: String): String {
        val response = postJson("/api/rooms/join", JSONObject().put("inviteCode", inviteCode))
        return JSONObject(response).getString("roomId")
    }

    suspend fun joinPublicRoom(roomId: String, password: String): String {
        val response = postJson(
            "/api/rooms/join",
            JSONObject()
                .put("roomId", roomId)
                .put("password", password)
        )
        return JSONObject(response).getString("roomId")
    }

    suspend fun joinPushTestRoom(): String {
        val response = postJson("/api/rooms/join-push-test", JSONObject())
        return JSONObject(response).getString("roomId")
    }

    suspend fun publicRooms(): List<PublicRoom> {
        val response = authedRequest(
            Request.Builder()
                .url("$baseUrl/api/rooms/public")
                .get()
        )
        val rooms = JSONObject(response).optJSONArray("rooms") ?: JSONArray()
        return buildList {
            for (index in 0 until rooms.length()) {
                val item = rooms.optJSONObject(index) ?: continue
                add(
                    PublicRoom(
                        roomId = item.optString("roomId"),
                        name = item.optString("name"),
                        memberCount = item.optInt("memberCount"),
                        alreadyJoined = item.optBoolean("alreadyJoined")
                    )
                )
            }
        }.filter { it.roomId.isNotBlank() && it.name.isNotBlank() }
    }

    suspend fun regenerateInvite(roomId: String): String {
        val response = postJson("/api/rooms/regenerate-invite", JSONObject().put("roomId", roomId))
        return JSONObject(response).getString("inviteCode")
    }

    suspend fun removeMember(roomId: String, targetUid: String) {
        postJson("/api/rooms/remove-member", JSONObject().put("roomId", roomId).put("targetUid", targetUid))
    }

    suspend fun leaveRoom(roomId: String) {
        postJson("/api/rooms/leave", JSONObject().put("roomId", roomId))
    }

    suspend fun deleteRoom(roomId: String) {
        postJson("/api/rooms/delete", JSONObject().put("roomId", roomId))
    }

    suspend fun sendTestPush(): Int {
        val response = postJson("/api/notifications/test", JSONObject())
        return JSONObject(response).optInt("sent", 0)
    }

    suspend fun sendExpiryReminderTestPush(): Int {
        val response = postJson("/api/notifications/test", JSONObject().put("kind", "expiryReminder"))
        return JSONObject(response).optInt("sent", 0)
    }

    suspend fun deleteCoupon(roomId: String, couponId: String) {
        authedRequest(
            Request.Builder()
                .url("$baseUrl/api/coupons?roomId=$roomId&couponId=$couponId")
                .delete()
        )
    }

    suspend fun uploadCouponImage(
        context: Context,
        roomId: String,
        couponId: String,
        imageUri: Uri,
        contentType: String,
        fileName: String,
        uploadId: String,
        preparedUpload: PreparedCouponUpload? = null,
        onPrepared: (CouponUploadPreparation) -> Unit = {},
        onRequestStarted: () -> Unit = {},
        onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): UploadedImage = uploadCouponImageTo(
        path = "/api/coupons/upload-image",
        context = context,
        roomId = roomId,
        couponId = couponId,
        imageUri = imageUri,
        contentType = contentType,
        fileName = fileName,
        uploadId = uploadId,
        preparedUpload = preparedUpload,
        onPrepared = onPrepared,
        onRequestStarted = onRequestStarted,
        onProgress = onProgress
    )

    suspend fun replaceCouponImage(
        context: Context,
        roomId: String,
        couponId: String,
        imageUri: Uri,
        contentType: String,
        fileName: String,
        preparedUpload: PreparedCouponUpload? = null,
        onPrepared: (CouponUploadPreparation) -> Unit = {},
        onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): UploadedImage = uploadCouponImageTo(
        path = "/api/coupons/replace-image",
        context = context,
        roomId = roomId,
        couponId = couponId,
        imageUri = imageUri,
        contentType = contentType,
        fileName = fileName,
        preparedUpload = preparedUpload,
        onPrepared = onPrepared,
        onProgress = onProgress
    )

    private suspend fun uploadCouponImageTo(
        path: String,
        context: Context,
        roomId: String,
        couponId: String,
        imageUri: Uri,
        contentType: String,
        fileName: String,
        uploadId: String? = null,
        preparedUpload: PreparedCouponUpload? = null,
        onPrepared: (CouponUploadPreparation) -> Unit = {},
        onRequestStarted: () -> Unit = {},
        onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit
    ): UploadedImage {
        val prepared = preparedUpload ?: prepareCouponImage(
            context = context,
            imageUri = imageUri,
            contentType = contentType,
            fileName = fileName
        )
        return prepared.use { upload ->
            onPrepared(upload.preparation)
            val imageBody = StreamingImageRequestBody(
                openStream = upload::openStream,
                mediaType = upload.contentType.toMediaType(),
                knownLength = upload.contentLength,
                maxBytes = AppConstants.MAX_IMAGE_BYTES.toLong(),
                onProgress = onProgress
            )

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("roomId", roomId)
                .addFormDataPart("couponId", couponId)
            uploadId?.let { bodyBuilder.addFormDataPart("uploadId", it) }
            val body = bodyBuilder.addFormDataPart("image", upload.fileName, imageBody).build()

            onRequestStarted()
            val response = authedRequest(
                Request.Builder()
                    .url("$baseUrl$path")
                    .post(body)
            )
            val json = JSONObject(response)
            UploadedImage(
                blobPath = json.getString("blobPath"),
                thumbnailBlobPath = json.optString("thumbnailBlobPath").takeIf { it.isNotBlank() },
                imageWidth = json.optIntOrNull("imageWidth"),
                imageHeight = json.optIntOrNull("imageHeight"),
                contentType = json.optString("contentType", upload.contentType),
                size = json.optLong("size", upload.contentLength ?: -1L),
                cleanupPending = json.optBoolean("cleanupPending", false)
            )
        }
    }

    suspend fun prepareCouponImage(
        context: Context,
        imageUri: Uri,
        contentType: String,
        fileName: String
    ): PreparedCouponUpload {
        val size = withContext(Dispatchers.IO) { contentLength(context, imageUri) }
        if (size != null && size > AppConstants.MAX_IMAGE_BYTES) {
            throw IOException("이미지는 최대 10MB까지 업로드할 수 있습니다.")
        }
        return CouponUploadOptimizer.prepare(
            context = context.applicationContext,
            uri = imageUri,
            contentType = contentType,
            fileName = fileName,
            sourceBytes = size
        )
    }

    suspend fun fetchCouponImage(
        roomId: String,
        couponId: String,
        thumbnail: Boolean = false,
        backfillThumbnail: Boolean = false
    ): ByteArray {
        val path = if (backfillThumbnail) "/api/coupons/thumbnail" else "/api/coupons/image"
        val variant = if (thumbnail && !backfillThumbnail) "&variant=thumbnail" else ""
        val url = "$baseUrl$path" +
            "?roomId=${Uri.encode(roomId)}" +
            "&couponId=${Uri.encode(couponId)}" +
            variant
        val builder = authedBuilder()
            .url(url)
        val request = if (backfillThumbnail) {
            builder.post(ByteArray(0).toRequestBody(null)).build()
        } else {
            builder.get().build()
        }
        val response = client.newCall(request).awaitBufferedResponse()
        if (!response.isSuccessful) throw IOException(errorMessage(response.code, response.bodyText()))
        return response.body.takeIf { it.isNotEmpty() } ?: throw IOException("이미지 응답이 비어 있습니다.")
    }

    suspend fun fetchCouponImageToFile(
        roomId: String,
        couponId: String,
        destination: File
    ): Long {
        val url = "$baseUrl/api/coupons/image" +
            "?roomId=${Uri.encode(roomId)}" +
            "&couponId=${Uri.encode(couponId)}"
        val request = authedBuilder().url(url).get().build()
        val response = client.newCall(request).awaitFileResponse(
            destination = destination,
            maxBytes = AppConstants.MAX_IMAGE_BYTES.toLong()
        )
        if (!response.isSuccessful) throw IOException(errorMessage(response.code, response.errorBody))
        return response.byteCount
    }

    suspend fun discardCouponImage(
        roomId: String,
        couponId: String,
        blobPath: String,
        thumbnailBlobPath: String?
    ) {
        val thumbnailQuery = thumbnailBlobPath?.let { "&thumbnailBlobPath=${Uri.encode(it)}" }.orEmpty()
        authedRequest(
            Request.Builder()
                .url(
                    "$baseUrl/api/coupons/upload-image" +
                        "?roomId=${Uri.encode(roomId)}" +
                        "&couponId=${Uri.encode(couponId)}" +
                        "&blobPath=${Uri.encode(blobPath)}" +
                        thumbnailQuery
                )
                .delete()
        )
    }

    suspend fun discardCouponUploadSession(roomId: String, couponId: String, uploadId: String) {
        authedRequest(
            Request.Builder()
                .url(
                    "$baseUrl/api/coupons/upload-image" +
                        "?roomId=${Uri.encode(roomId)}" +
                        "&couponId=${Uri.encode(couponId)}" +
                        "&uploadId=${Uri.encode(uploadId)}"
                )
                .delete()
        )
    }

    suspend fun completeCouponUploadSession(roomId: String, couponId: String, uploadId: String) {
        authedRequest(
            Request.Builder()
                .url(
                    "$baseUrl/api/coupons/upload-image" +
                        "?roomId=${Uri.encode(roomId)}" +
                        "&couponId=${Uri.encode(couponId)}" +
                        "&uploadId=${Uri.encode(uploadId)}"
                )
                .patch(ByteArray(0).toRequestBody(null))
        )
    }

    private fun contentLength(context: Context, uri: Uri): Long? {
        val fromQuery = context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) cursor.getLong(index) else null
        }
        if (fromQuery != null && fromQuery > 0L) return fromQuery
        return context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
            descriptor.length.takeIf { it > 0L }
        }
    }

    private suspend fun postJson(path: String, body: JSONObject): String {
        return authedRequest(
            Request.Builder()
                .url("$baseUrl$path")
                .post(body.toString().toRequestBody(jsonMediaType))
        )
    }

    private suspend fun authedRequest(builder: Request.Builder): String {
        val request = authedBuilder(builder).build()
        val response = client.newCall(request).awaitBufferedResponse()
        val text = response.bodyText()
        if (!response.isSuccessful) throw IOException(errorMessage(response.code, text))
        return text
    }

    private suspend fun authedBuilder(builder: Request.Builder = Request.Builder()): Request.Builder {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw IOException("로그인이 필요합니다.")
        return builder.header("Authorization", "Bearer $token")
    }

    private fun errorMessage(code: Int, body: String?): String {
        val serverMessage = body?.let {
            runCatching { JSONObject(it).optString("error") }.getOrNull()
        }?.takeIf { it.isNotBlank() }
        return if (serverMessage != null) "$serverMessage ($code)" else "서버 요청에 실패했습니다. ($code)"
    }
}

internal data class BufferedHttpResponse(val code: Int, val body: ByteArray) {
    val isSuccessful: Boolean get() = code in 200..299
    fun bodyText(): String = body.toString(Charsets.UTF_8)
}

internal suspend fun Call.awaitBufferedResponse(): BufferedHttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            if (continuation.isActive) continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
            val buffered = runCatching {
                response.use {
                    BufferedHttpResponse(it.code, it.body?.bytes() ?: ByteArray(0))
                }
            }.getOrElse { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
                return
            }
            if (continuation.isActive) continuation.resume(buffered)
        }
    })
}

internal data class FileHttpResponse(
    val code: Int,
    val errorBody: String?,
    val byteCount: Long
) {
    val isSuccessful: Boolean get() = code in 200..299
}

internal suspend fun Call.awaitFileResponse(
    destination: File,
    maxBytes: Long
): FileHttpResponse = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation {
        cancel()
        destination.delete()
    }
    enqueue(object : Callback {
        override fun onFailure(call: Call, error: IOException) {
            destination.delete()
            if (continuation.isActive) continuation.resumeWithException(error)
        }

        override fun onResponse(call: Call, response: okhttp3.Response) {
            val streamed = runCatching {
                response.use { streamResponseToFile(it, destination, maxBytes) }
            }.getOrElse { error ->
                destination.delete()
                if (continuation.isActive) continuation.resumeWithException(error)
                return
            }
            if (continuation.isActive) continuation.resume(streamed) else destination.delete()
        }
    })
}

private fun streamResponseToFile(
    response: okhttp3.Response,
    destination: File,
    maxBytes: Long
): FileHttpResponse {
    if (!response.isSuccessful) {
        destination.delete()
        return FileHttpResponse(
            code = response.code,
            errorBody = response.body?.byteStream()?.use(::readLimitedErrorBody),
            byteCount = 0L
        )
    }

    val body = response.body ?: throw IOException("이미지 응답이 비어 있습니다.")
    val contentLength = body.contentLength()
    if (contentLength == 0L) throw IOException("이미지 응답이 비어 있습니다.")
    if (contentLength > maxBytes) throw IOException("이미지는 최대 10MB까지 내려받을 수 있습니다.")
    val byteCount = body.byteStream().use { input -> writeToFile(input, destination, maxBytes) }
    if (byteCount == 0L) throw IOException("이미지 응답이 비어 있습니다.")
    return FileHttpResponse(response.code, null, byteCount)
}

private fun writeToFile(input: java.io.InputStream, destination: File, maxBytes: Long): Long =
    destination.outputStream().buffered(DOWNLOAD_BUFFER_SIZE).use { output ->
        val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
        var totalBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            if (totalBytes > maxBytes) throw IOException("이미지는 최대 10MB까지 내려받을 수 있습니다.")
            output.write(buffer, 0, count)
        }
        totalBytes
    }

private fun readLimitedErrorBody(input: java.io.InputStream): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(ERROR_BODY_BUFFER_SIZE)
    var remaining = MAX_ERROR_BODY_BYTES
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toString(Charsets.UTF_8.name())
}

private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024
private const val ERROR_BODY_BUFFER_SIZE = 4 * 1024
private const val MAX_ERROR_BODY_BYTES = 32 * 1024

private class StreamingImageRequestBody(
    private val openStream: () -> java.io.InputStream?,
    private val mediaType: okhttp3.MediaType,
    private val knownLength: Long?,
    private val maxBytes: Long,
    private val onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit
) : RequestBody() {
    override fun contentType(): okhttp3.MediaType = mediaType

    override fun contentLength(): Long = knownLength ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
        var sent = 0L
        var lastReported = 0L
        openStream()?.use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                sent += count
                if (sent > maxBytes) throw IOException("이미지는 최대 10MB까지 업로드할 수 있습니다.")
                sink.write(buffer, 0, count)
                if (sent - lastReported >= PROGRESS_INTERVAL_BYTES || sent == knownLength) {
                    onProgress(sent, knownLength)
                    lastReported = sent
                }
            }
        } ?: throw IOException("이미지를 읽을 수 없습니다.")
        if (sent != lastReported) onProgress(sent, knownLength)
    }

    private companion object {
        const val UPLOAD_BUFFER_SIZE = 64 * 1024
        const val PROGRESS_INTERVAL_BYTES = 64L * 1024L
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (isNull(name)) null else optInt(name)
