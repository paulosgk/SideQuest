package com.example.sidequest.data

import com.google.firebase.Timestamp

enum class MatchStatus {
    ACTIVE, FINISHED
}

data class Match(
    val id: String = "",
    val groupId: String = "",
    val createdBy: String = "",
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val status: MatchStatus = MatchStatus.ACTIVE,
    val challengeCountPerPlayer: Int = 3,
    val createdAt: Timestamp? = null
)
