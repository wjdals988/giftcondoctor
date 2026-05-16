package com.giftcondoctor.app.data

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.giftcondoctor.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.io.IOException

class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun authState(): Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser != null) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
        upsertUser()
    }

    suspend fun createAccount(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
        upsertUser()
    }

    suspend fun signInWithGoogle(context: Context) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            throw IOException("googleWebClientId가 local.properties에 설정되지 않았습니다.")
        }

        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(option)
            .build()

        val credential = CredentialManager.create(context).getCredential(context, request).credential
        val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        upsertUser()
    }

    suspend fun upsertUser() {
        val user = auth.currentUser ?: return
        val data = mapOf(
            "displayName" to (user.displayName ?: user.email ?: "이름 없음"),
            "email" to user.email,
            "photoUrl" to user.photoUrl?.toString(),
            "defaultNotificationMode" to "basic",
            "defaultNotificationDays" to listOf(7, 3, 1, 0),
            "pushEnabled" to true,
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        firestore.document("users/${user.uid}").set(data, com.google.firebase.firestore.SetOptions.merge()).await()
    }

    fun signOut() {
        auth.signOut()
    }
}
