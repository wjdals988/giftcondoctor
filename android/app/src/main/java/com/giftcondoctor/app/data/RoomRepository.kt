package com.giftcondoctor.app.data

import com.giftcondoctor.app.data.model.Room
import com.giftcondoctor.app.data.model.RoomMember
import com.giftcondoctor.app.data.model.RoomMembership
import com.giftcondoctor.app.data.model.PublicRoom
import com.giftcondoctor.app.data.model.toRoom
import com.giftcondoctor.app.data.model.toRoomMember
import com.giftcondoctor.app.data.model.toRoomMembership
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import com.giftcondoctor.app.data.model.ExpiringCoupons

class RoomRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val backend: BackendClient = BackendClient()
) {
    val currentUid: String?
        get() = auth.currentUser?.uid

    fun observeMemberships(): Flow<List<RoomMembership>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val registration = firestore.collection("users/$uid/roomMemberships")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toRoomMembership() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    fun observeRoom(roomId: String): Flow<Room?> = callbackFlow {
        val registration = firestore.document("rooms/$roomId")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toRoom())
            }
        awaitClose { registration.remove() }
    }

    fun observeMembers(roomId: String): Flow<List<RoomMember>> = callbackFlow {
        val registration = firestore.collection("rooms/$roomId/members")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents?.mapNotNull { it.toRoomMember() }.orEmpty())
            }
        awaitClose { registration.remove() }
    }

    suspend fun createRoom(name: String, isPublic: Boolean, password: String): String =
        backend.createRoom(name, isPublic, password)

    suspend fun joinRoom(inviteCode: String): String = backend.joinRoom(inviteCode)

    suspend fun joinPublicRoom(roomId: String, password: String): String =
        backend.joinPublicRoom(roomId, password)

    suspend fun joinPushTestRoom(): String = backend.joinPushTestRoom()

    suspend fun publicRooms(): List<PublicRoom> = backend.publicRooms()

    suspend fun expiringCoupons(days: Int): ExpiringCoupons = backend.expiringCoupons(days)

    suspend fun regenerateInvite(roomId: String): String = backend.regenerateInvite(roomId)

    suspend fun leaveRoom(roomId: String) = backend.leaveRoom(roomId)

    suspend fun deleteRoom(roomId: String) = backend.deleteRoom(roomId)

    suspend fun removeMember(roomId: String, targetUid: String) = backend.removeMember(roomId, targetUid)
}
