package com.example.sidequest.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface MatchRepository {
    suspend fun createMatch(
        groupId: String,
        createdBy: String,
        challengeCountPerPlayer: Int
    ): Result<String>
    
    fun getActiveMatchFlow(groupId: String): Flow<Match?>
    fun getAssignedChallengesFlow(matchId: String, playerId: String): Flow<List<AssignedChallenge>>
    suspend fun updateChallengeStatus(assignmentId: String, status: ChallengeStatus): Result<Unit>
}

class FirebaseMatchRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : MatchRepository {

    override suspend fun createMatch(
        groupId: String,
        createdBy: String,
        challengeCountPerPlayer: Int
    ): Result<String> {
        return try {
            // 1. Fetch Group members
            val groupRef = firestore.collection("groups").document(groupId)
            val groupSnapshot = groupRef.get().await()
            @Suppress("UNCHECKED_CAST")
            val members = groupSnapshot.get("members") as? List<String> ?: emptyList()
            
            if (members.isEmpty()) return Result.failure(Exception("Group has no members"))

            // 2. Fetch all Challenge Templates
            val templatesSnapshot = firestore.collection("challengeTemplates").get().await()
            val allTemplates = templatesSnapshot.toObjects(Challenge::class.java)
            
            if (allTemplates.isEmpty()) {
                return Result.failure(Exception("No challenge templates found in pool"))
            }

            // 3. Start transaction for atomic updates
            val matchId = firestore.runTransaction { transaction ->
                // Final safety check: group is not already started
                val currentGroup = transaction.get(groupRef)
                if (currentGroup.getBoolean("isStarted") == true) {
                    throw IllegalStateException("Match already in progress")
                }

                val matchRef = firestore.collection("matches").document()
                val id = matchRef.id
                
                val matchData = mapOf(
                    "id" to id,
                    "groupId" to groupId,
                    "createdBy" to createdBy,
                    "startDate" to Timestamp.now(),
                    "status" to MatchStatus.ACTIVE.name,
                    "challengeCountPerPlayer" to challengeCountPerPlayer,
                    "createdAt" to Timestamp.now()
                )
                
                // 4. Assign random challenges
                members.forEach { memberId ->
                    // For each player, shuffle the pool and take the requested count
                    val shuffled = allTemplates.shuffled().take(challengeCountPerPlayer)
                    shuffled.forEach { template ->
                        val assignmentRef = firestore.collection("assignedChallenges").document()
                        val assignmentData = mapOf(
                            "id" to assignmentRef.id,
                            "matchId" to id,
                            "playerId" to memberId,
                            "challengeId" to template.id,
                            "status" to ChallengeStatus.ASSIGNED.name,
                            "completedAt" to null,
                            "proofUrl" to ""
                        )
                        transaction.set(assignmentRef, assignmentData)
                    }
                }
                
                transaction.set(matchRef, matchData)
                transaction.update(groupRef, "isStarted", true)
                id
            }.await()
            
            Result.success(matchId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getActiveMatchFlow(groupId: String): Flow<Match?> = callbackFlow {
        val subscription = firestore.collection("matches")
            .whereEqualTo("groupId", groupId)
            .whereEqualTo("status", MatchStatus.ACTIVE.name)
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val match = snapshot?.documents?.firstOrNull()?.toObject(Match::class.java)
                trySend(match)
            }
        
        awaitClose { subscription.remove() }
    }

    override fun getAssignedChallengesFlow(matchId: String, playerId: String): Flow<List<AssignedChallenge>> = callbackFlow {
        val subscription = firestore.collection("assignedChallenges")
            .whereEqualTo("matchId", matchId)
            .whereEqualTo("playerId", playerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val assignments = snapshot?.toObjects(AssignedChallenge::class.java) ?: emptyList()
                trySend(assignments)
            }
        
        awaitClose { subscription.remove() }
    }

    override suspend fun updateChallengeStatus(assignmentId: String, status: ChallengeStatus): Result<Unit> {
        return try {
            firestore.collection("assignedChallenges")
                .document(assignmentId)
                .update("status", status.name)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
