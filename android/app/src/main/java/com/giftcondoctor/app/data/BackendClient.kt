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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

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
        onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> }
    ): UploadedImage {
        val size = withContext(Dispatchers.IO) { contentLength(context, imageUri) }
        if (size != null && size > AppConstants.MAX_IMAGE_BYTES) {
            throw IOException("이미지는 최대 10MB까지 업로드할 수 있습니다.")
        }
        val imageBody = ContentUriRequestBody(
            context = context,
            uri = imageUri,
            mediaType = contentType.toMediaType(),
            knownLength = size,
            maxBytes = AppConstants.MAX_IMAGE_BYTES.toLong(),
            onProgress = onProgress
        )

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("roomId", roomId)
            .addFormDataPart("couponId", couponId)
            .addFormDataPart("image", fileName, imageBody)
            .build()

        val response = authedRequest(
            Request.Builder()
                .url("$baseUrl/api/coupons/upload-image")
                .post(body)
        )
        val json = JSONObject(response)
        return UploadedImage(
            blobPath = json.getString("blobPath"),
            thumbnailBlobPath = json.optString("thumbnailBlobPath").takeIf { it.isNotBlank() },
            imageWidth = json.optIntOrNull("imageWidth"),
            imageHeight = json.optIntOrNull("imageHeight"),
            contentType = json.optString("contentType", contentType),
            size = json.optLong("size", size ?: -1L)
        )
    }

    suspend fun fetchCouponImage(
        roomId: String,
        couponId: String,
        thumbnail: Boolean = false,
        backfillThumbnail: Boolean = false
    ): ByteArray =
        withContext(Dispatchers.IO) {
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
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, response.body?.string()))
                response.body?.bytes() ?: throw IOException("이미지 응답이 비어 있습니다.")
            }
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

    private suspend fun authedRequest(builder: Request.Builder): String =
        withContext(Dispatchers.IO) {
            val request = authedBuilder(builder).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, text))
                text
            }
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

private class ContentUriRequestBody(
    context: Context,
    private val uri: Uri,
    private val mediaType: okhttp3.MediaType,
    private val knownLength: Long?,
    private val maxBytes: Long,
    private val onProgress: (sentBytes: Long, totalBytes: Long?) -> Unit
) : RequestBody() {
    private val resolver = context.contentResolver

    override fun contentType(): okhttp3.MediaType = mediaType

    override fun contentLength(): Long = knownLength ?: -1L

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var sent = 0L
        var lastReported = 0L
        resolver.openInputStream(uri)?.use { input ->
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
        const val PROGRESS_INTERVAL_BYTES = 64L * 1024L
    }
}

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (isNull(name)) null else optInt(name)
