package com.example.sidequest.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface GroupRepository {
    suspend fun createGroup(ownerId: String): Result<String>
    suspend fun leaveGroup(userId: String, groupId: String): Result<Unit>
    suspend fun joinGroup(userId: String, inviteCode: String): Result<String>
    suspend fun startMatch(groupId: String): Result<Unit>
    fun getGroupFlow(groupId: String): Flow<Group?>
}

class FirebaseGroupRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : GroupRepository {

    override suspend fun createGroup(ownerId: String): Result<String> {
        return try {
            val result = firestore.runTransaction { transaction ->
                val userRef = firestore.collection("users").document(ownerId)
                val userSnapshot = transaction.get(userRef)
                
                val currentGroupId = userSnapshot.getString("groupId")
                if (currentGroupId != null) {
                    throw IllegalStateException("User is already in a group")
                }

                val groupRef = firestore.collection("groups").document()
                val groupId = groupRef.id
                val inviteCode = generateInviteCode()

                val groupData = mapOf(
                    "id" to groupId,
                    "inviteCode" to inviteCode,
                    "ownerId" to ownerId,
                    "members" to listOf(ownerId),
                    "maxMembers" to 10,
                    "createdAt" to Timestamp.now()
                )

                transaction.set(groupRef, groupData)
                transaction.update(userRef, "groupId", groupId)
                
                groupId
            }.await()
            
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun joinGroup(userId: String, inviteCode: String): Result<String> {
        return try {
            val normalizedCode = inviteCode.trim().uppercase()
            
            // First find the group with this invite code
            val querySnapshot = firestore.collection("groups")
                .whereEqualTo("inviteCode", normalizedCode)
                .limit(1)
                .get()
                .await()
            
            if (querySnapshot.isEmpty) {
                return Result.failure(Exception("Invalid invite code"))
            }
            
            val groupSnapshot = querySnapshot.documents.first()
            val groupId = groupSnapshot.id
            
            firestore.runTransaction { transaction ->
                val groupRef = firestore.collection("groups").document(groupId)
                val userRef = firestore.collection("users").document(userId)
                
                val currentGroup = transaction.get(groupRef)
                val currentUser = transaction.get(userRef)
                
                // Rules verification
                val members = currentGroup.get("members") as? List<*> ?: emptyList<String>()
                val maxMembers = currentGroup.getLong("maxMembers") ?: 10L
                val isStarted = currentGroup.getBoolean("isStarted") ?: false
                
                if (isStarted) {
                    throw IllegalStateException("Cannot join: Match has already started")
                }

                if (currentUser.getString("groupId") != null) {
                    throw IllegalStateException("You are already in a group")
                }
                
                if (members.size >= maxMembers) {
                    throw IllegalStateException("Group is full")
                }
                
                if (members.contains(userId)) {
                    throw IllegalStateException("You are already a member of this group")
                }
                
                val updatedMembers = members.toMutableList().apply { add(userId) }
                
                transaction.update(groupRef, "members", updatedMembers)
                transaction.update(userRef, "groupId", groupId)
                
                groupId
            }.await()
            
            Result.success(groupId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun startMatch(groupId: String): Result<Unit> {
        return try {
            firestore.collection("groups").document(groupId)
                .update("isStarted", true)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun leaveGroup(userId: String, groupId: String): Result<Unit> {
        return try {
            firestore.runTransaction { transaction ->
                val groupRef = firestore.collection("groups").document(groupId)
                val userRef = firestore.collection("users").document(userId)
                
                val groupSnapshot = transaction.get(groupRef)
                val members = groupSnapshot.get("members") as? List<*> ?: emptyList<String>()
                val updatedMembers = members.filter { it != userId }

                if (updatedMembers.isEmpty()) {
                    transaction.delete(groupRef)
                } else {
                    transaction.update(groupRef, "members", updatedMembers)
                    // If owner leaves, pick next member as owner (optional logic, but good for consistency)
                    if (groupSnapshot.getString("ownerId") == userId) {
                        transaction.update(groupRef, "ownerId", updatedMembers.first())
                    }
                }

                transaction.update(userRef, "groupId", null)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getGroupFlow(groupId: String): Flow<Group?> = callbackFlow {
        val subscription = firestore.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val group = snapshot?.toObject(Group::class.java)
                trySend(group)
            }
        
        awaitClose { subscription.remove() }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // Removed ambiguous characters
        return (1..8)
            .map { chars.random() }
            .joinToString("")
    }
}
