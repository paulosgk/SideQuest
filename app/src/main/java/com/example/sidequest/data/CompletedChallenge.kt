package com.example.sidequest.data

import com.google.firebase.Timestamp

data class CompletedChallenge(
    val id: String = "",
    val challengeId: String = "",
    val playerId: String = "",
    val seasonId: String = "season_1", // Default for now
    val completedAt: Timestamp? = null,
    val pointsAwarded: Int = 0
)
