package com.example.sidequest.data

import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface AuthRepository {
    val currentUser: FirebaseUser?
    suspend fun login(email: String, password: String): Result<FirebaseUser?>
    suspend fun register(username: String, email: String, password: String): Result<FirebaseUser?>
    suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser?>
    suspend fun getUserMetadata(uid: String): Result<UserMetadata?>
    suspend fun getUsersMetadata(uids: List<String>): Result<List<UserMetadata>>
    fun getUsersMetadataFlow(uids: List<String>): Flow<List<UserMetadata>>
    fun logout()
}

class FirebaseAuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) : AuthRepository {
    
    override val currentUser: FirebaseUser?
        get() = firebaseAuth.currentUser

    override suspend fun login(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(username: String, email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val user = result.user
            
            if (user != null) {
                val userDoc = mapOf(
                    "uid" to user.uid,
                    "username" to username,
                    "email" to email,
                    "profilePhotoUrl" to "",
                    "groupId" to null,
                    "premium" to false,
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection("users").document(user.uid).set(userDoc).await()
            }
            
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun signInWithCredential(credential: AuthCredential): Result<FirebaseUser?> {
        return try {
            val result = firebaseAuth.signInWithCredential(credential).await()
            Result.success(result.user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUserMetadata(uid: String): Result<UserMetadata?> {
        return try {
            val document = firestore.collection("users").document(uid).get().await()
            val metadata = document.toObject(UserMetadata::class.java)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getUsersMetadata(uids: List<String>): Result<List<UserMetadata>> {
        if (uids.isEmpty()) return Result.success(emptyList())
        return try {
            val documents = firestore.collection("users")
                .whereIn("uid", uids)
                .get()
                .await()
            val metadataList = documents.toObjects(UserMetadata::class.java)
            Result.success(metadataList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getUsersMetadataFlow(uids: List<String>): Flow<List<UserMetadata>> = callbackFlow {
        if (uids.isEmpty()) {
            trySend(emptyList())
            return@callbackFlow
        }

        val subscription = firestore.collection("users")
            .whereIn("uid", uids)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val metadataList = snapshot?.toObjects(UserMetadata::class.java) ?: emptyList()
                trySend(metadataList)
            }
        
        awaitClose { subscription.remove() }
    }

    override fun logout() {
        firebaseAuth.signOut()
    }
}
