package com.giftcondoctor.app.data

import android.content.Context
import android.content.Intent
import com.giftcondoctor.app.BuildConfig
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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

    fun googleSignInIntent(context: Context): Intent {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            throw IOException("Google 로그인 설정이 누락되었습니다. local.properties의 googleWebClientId를 확인해 주세요.")
        }

        val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(context, options).signInIntent
    }

    /**
     * 실패한 Google 로그인 인텐트에서 상태 코드를 꺼낸다.
     *
     * GoogleSignIn 은 설정 오류·네트워크 실패에서도 RESULT_CANCELED 를 돌려주므로,
     * 결과 코드만으로는 "사용자가 취소함" 과 "설정이 잘못됨" 을 구분할 수 없다.
     * 인텐트에 담긴 Status 를 읽어야 실제 원인을 알 수 있다.
     */
    fun googleSignInFailureCode(data: Intent?): Int? {
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        return runCatching { task.getResult(ApiException::class.java); null }
            .getOrElse { error -> (error as? ApiException)?.statusCode }
    }

    suspend fun signInWithGoogleIntent(data: Intent?) {
        val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
        val idToken = account.idToken ?: throw IOException("Google ID 토큰을 받지 못했습니다.")
        val firebaseCredential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(firebaseCredential).await()
        upsertUser()
    }

    suspend fun upsertUser() {
        val user = auth.currentUser ?: return
        val ref = firestore.document("users/${user.uid}")
        firestore.runTransaction { transaction ->
            val existing = transaction.get(ref)
            val data = mutableMapOf<String, Any?>(
            "displayName" to (user.displayName ?: user.email ?: "이름 없음"),
            "email" to user.email,
            "photoUrl" to user.photoUrl?.toString(),
            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )
            if (!existing.exists()) {
                data += mapOf(
                    "defaultNotificationMode" to "basic",
                    "defaultNotificationDays" to listOf(7, 3, 1, 0),
                    "pushEnabled" to true,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
            }
            transaction.set(ref, data, com.google.firebase.firestore.SetOptions.merge())
        }.await()
    }

    fun signOut() {
        CouponImageLoader.clear()
        CouponImageFileStore.clearTracked()
        auth.signOut()
    }
}
