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
}
