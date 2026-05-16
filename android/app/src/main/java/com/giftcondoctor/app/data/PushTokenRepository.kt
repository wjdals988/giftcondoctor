package com.giftcondoctor.app.data

import android.os.Build
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
        saveToken(messaging.token.await())
    }

    suspend fun saveToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        val tokenId = sha256(token)
        val data = mapOf(
            "token" to token,
            "platform" to "android",
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
            "appVersion" to "0.1.0",
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp()
        )
        firestore.document("users/$uid/pushTokens/$tokenId").set(data, SetOptions.merge()).await()
    }
}

fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
