package com.example.sidequest.data

import com.google.firebase.Timestamp

data class Group(
    val id: String = "",
    val inviteCode: String = "",
    val ownerId: String = "",
    val members: List<String> = emptyList(),
    val maxMembers: Int = 10,
    val isStarted: Boolean = false,
    val createdAt: Timestamp? = null
)
