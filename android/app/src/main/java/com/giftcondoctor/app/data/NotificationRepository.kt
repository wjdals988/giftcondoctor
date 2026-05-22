package com.giftcondoctor.app.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

class NotificationRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val backend: BackendClient = BackendClient(),
    private val pushTokenRepository: PushTokenRepository = PushTokenRepository()
) {
    data class DefaultNotificationSettings(
        val mode: String,
        val days: List<Int>,
        val pushEnabled: Boolean
    )

    suspend fun currentDefault(): DefaultNotificationSettings {
        val uid = auth.currentUser?.uid ?: return DefaultNotificationSettings("basic", listOf(7, 3, 1, 0), true)
        val user = firestore.document("users/$uid").get().await()
        return DefaultNotificationSettings(
            mode = user.getString("defaultNotificationMode") ?: "basic",
            days = user.getLongList("defaultNotificationDays") ?: listOf(7, 3, 1, 0),
            pushEnabled = user.getBoolean("pushEnabled") ?: true
        )
    }

    suspend fun updateDefault(mode: String, days: List<Int>, pushEnabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        firestore.document("users/$uid").set(
            mapOf(
                "defaultNotificationMode" to mode,
                "defaultNotificationDays" to days,
                "pushEnabled" to pushEnabled,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun sendTestPush(): Int {
        pushTokenRepository.saveCurrentToken()
        return backend.sendTestPush()
    }

    suspend fun sendExpiryReminderTestPush(): Int {
        pushTokenRepository.saveCurrentToken()
        return backend.sendExpiryReminderTestPush()
    }
}

@Suppress("UNCHECKED_CAST")
private fun com.google.firebase.firestore.DocumentSnapshot.getLongList(field: String): List<Int>? =
    (get(field) as? List<*>)?.mapNotNull {
        when (it) {
            is Long -> it.toInt()
            is Int -> it
            else -> null
        }
    }
