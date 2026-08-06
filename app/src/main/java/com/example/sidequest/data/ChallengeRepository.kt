package com.example.sidequest.data

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface ChallengeRepository {
    suspend fun getChallengeTemplates(): Result<List<Challenge>>
    fun getChallengeTemplatesFlow(): Flow<List<Challenge>>
    suspend fun createChallengeTemplate(challenge: Challenge): Result<Unit>
    suspend fun seedChallenges(challenges: List<Challenge>): Result<Unit>
}

class FirebaseChallengeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ChallengeRepository {

    override suspend fun getChallengeTemplates(): Result<List<Challenge>> {
        return try {
            val snapshot = firestore.collection("challengeTemplates").get().await()
            val challenges = snapshot.toObjects(Challenge::class.java)
            Result.success(challenges)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getChallengeTemplatesFlow(): Flow<List<Challenge>> = callbackFlow {
        val subscription = firestore.collection("challengeTemplates")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val challenges = snapshot?.toObjects(Challenge::class.java) ?: emptyList()
                trySend(challenges)
            }
        awaitClose { subscription.remove() }
    }

    override suspend fun createChallengeTemplate(challenge: Challenge): Result<Unit> {
        return try {
            val docRef = if (challenge.id.isEmpty()) {
                firestore.collection("challengeTemplates").document()
            } else {
                firestore.collection("challengeTemplates").document(challenge.id)
            }
            
            val id = docRef.id
            val finalChallenge = challenge.copy(id = id)
            
            docRef.set(finalChallenge).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun seedChallenges(challenges: List<Challenge>): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val collection = firestore.collection("challengeTemplates")
            
            challenges.forEach { challenge ->
                val docRef = collection.document()
                val id = docRef.id
                val finalChallenge = challenge.copy(id = id)
                batch.set(docRef, finalChallenge)
            }
            
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
