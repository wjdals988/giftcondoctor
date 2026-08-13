package com.giftcondoctor.app.data

import android.os.Build
import com.giftcondoctor.app.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

class PushTokenRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    suspend fun saveCurrentToken() {
        val token = runCatching { messaging.token.await() }
            .getOrElse {
                throw IllegalStateException(
                    "FCM 기기 등록에 실패했습니다. 인터넷 연결, DNS, Google Play 서비스를 확인해 주세요.",
                    it
                )
            }
        check(token.isNotBlank()) { "FCM에서 빈 기기 토큰을 반환했습니다. 잠시 후 다시 시도해 주세요." }
        runCatching { saveToken(token) }
            .getOrElse {
                throw IllegalStateException(
                    "FCM 토큰을 서버 계정에 저장하지 못했습니다. 로그인과 네트워크 상태를 확인해 주세요.",
                    it
                )
            }
    }

    suspend fun saveToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        val tokenId = sha256(token)
        val ref = firestore.document("users/$uid/pushTokens/$tokenId")
        firestore.runTransaction { transaction ->
            val existing = transaction.get(ref)
            val data = mutableMapOf<String, Any>(
                "token" to token,
                "platform" to "android",
                "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                "appVersion" to BuildConfig.VERSION_NAME,
                "lastSeenAt" to FieldValue.serverTimestamp()
            )
            if (!existing.exists()) data["createdAt"] = FieldValue.serverTimestamp()
            transaction.set(ref, data, SetOptions.merge())
        }.await()
    }

    suspend fun deleteCurrentToken() {
        val uid = auth.currentUser?.uid
        val token = runCatching { messaging.token.await() }.getOrNull()
        var cleanupFailure: Throwable? = null

        if (uid != null && token != null) {
            runCatching {
                firestore.document("users/$uid/pushTokens/${sha256(token)}").delete().await()
            }.onFailure { cleanupFailure = it }
        }

        runCatching { messaging.deleteToken().await() }
            .onFailure { if (cleanupFailure == null) cleanupFailure = it }

        cleanupFailure?.let { throw it }
    }
}

fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
