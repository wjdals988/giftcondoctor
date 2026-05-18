package com.giftcondoctor.app.data

import android.content.Context
import android.net.Uri
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class BackendClient(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val client: OkHttpClient = OkHttpClient()
) {
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

    suspend fun sendTestPush(): Int {
        val response = postJson("/api/notifications/test", JSONObject())
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
        fileName: String
    ): UploadedImage {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: throw IOException("이미지를 읽을 수 없습니다.")
        }
        if (bytes.size > AppConstants.MAX_IMAGE_BYTES) {
            throw IOException("이미지는 최대 10MB까지 업로드할 수 있습니다.")
        }

        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("roomId", roomId)
            .addFormDataPart("couponId", couponId)
            .addFormDataPart("image", fileName, bytes.toRequestBody(contentType.toMediaType()))
            .build()

        val response = authedRequest(
            Request.Builder()
                .url("$baseUrl/api/coupons/upload-image")
                .post(body)
        )
        val json = JSONObject(response)
        return UploadedImage(
            blobPath = json.getString("blobPath"),
            imageWidth = json.optIntOrNull("imageWidth"),
            imageHeight = json.optIntOrNull("imageHeight"),
            contentType = json.optString("contentType", contentType),
            size = json.optLong("size", bytes.size.toLong())
        )
    }

    suspend fun fetchCouponImage(roomId: String, couponId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val request = authedBuilder()
                .url("$baseUrl/api/coupons/image?roomId=$roomId&couponId=$couponId")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(errorMessage(response.code, response.body?.string()))
                response.body?.bytes() ?: throw IOException("이미지 응답이 비어 있습니다.")
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

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (isNull(name)) null else optInt(name)
