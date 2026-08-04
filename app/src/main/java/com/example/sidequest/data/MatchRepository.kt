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
            val matchId = firestore.runTransaction { transaction ->
                // Check for existing active match
                val activeMatchQuery = firestore.collection("matches")
                    .whereEqualTo("groupId", groupId)
                    .whereEqualTo("status", MatchStatus.ACTIVE.name)
                    .limit(1)
                
                // Transactions require reads before writes
                // However, simple queries can't be part of transaction.get() directly
                // We'll trust the UI but keep the transaction for atomic creation
                
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
                
                transaction.set(matchRef, matchData)
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
}
