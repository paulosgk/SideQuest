package com.example.sidequest.data

import com.google.firebase.Timestamp

enum class Difficulty(val points: Int) {
    EASY(50),
    MEDIUM(100),
    HARD(200),
    EXTREME(500)
}

enum class Category {
    SOCIAL,
    FITNESS,
    ADVENTURE,
    CREATIVITY,
    PUZZLE,
    RANDOM
}

enum class ChallengeType {
    DEFAULT,
    COMMUNITY,
    CUSTOM
}

data class Challenge(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val difficulty: Difficulty = Difficulty.EASY,
    val points: Int = difficulty.points,
    val category: Category = Category.RANDOM,
    val creatorId: String = "",
    val type: ChallengeType = ChallengeType.DEFAULT
)

enum class ChallengeStatus {
    ASSIGNED, SUBMITTED, COMPLETED
}

data class AssignedChallenge(
    val id: String = "",
    val matchId: String = "",
    val playerId: String = "",
    val challengeId: String = "",
    val status: ChallengeStatus = ChallengeStatus.ASSIGNED,
    val completedAt: Timestamp? = null,
    val proofUrl: String = ""
)

data class ChallengeWithAssignment(
    val template: Challenge,
    val assignment: AssignedChallenge
)
