package com.example.sidequest.data

import com.google.firebase.Timestamp

data class UserMetadata(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val profilePhotoUrl: String = "",
    val groupId: String? = null,
    val premium: Boolean = false,
    val createdAt: Timestamp? = null
)
