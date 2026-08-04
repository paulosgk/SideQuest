package com.example.sidequest.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sidequest.data.AuthRepository
import com.example.sidequest.data.GroupRepository
import com.example.sidequest.data.UserMetadata
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val user: FirebaseUser? = null,
    val userMetadata: UserMetadata? = null,
    val isLoading: Boolean = false,
    val isCreatingGroup: Boolean = false,
    val isLeavingGroup: Boolean = false,
    val isJoiningGroup: Boolean = false,
    val groupCreatedId: String? = null,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState(user = authRepository.currentUser))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        authRepository.currentUser?.uid?.let { fetchUserMetadata(it) }
    }

    private fun fetchUserMetadata(uid: String) {
        viewModelScope.launch {
            val result = authRepository.getUserMetadata(uid)
            _authState.value = _authState.value.copy(
                userMetadata = result.getOrNull()
            )
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.login(email, password)
            val user = result.getOrNull()
            
            if (user != null) {
                val metadataResult = authRepository.getUserMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user,
                    userMetadata = metadataResult.getOrNull(),
                    error = metadataResult.exceptionOrNull()?.message
                )
            } else {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun register(username: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.register(username, email, password)
            val user = result.getOrNull()

            if (user != null) {
                val metadataResult = authRepository.getUserMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user,
                    userMetadata = metadataResult.getOrNull(),
                    error = metadataResult.exceptionOrNull()?.message
                )
            } else {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun signInWithGoogle(credential: AuthCredential) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.signInWithCredential(credential)
            val user = result.getOrNull()

            if (user != null) {
                val metadataResult = authRepository.getUserMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user,
                    userMetadata = metadataResult.getOrNull(),
                    error = metadataResult.exceptionOrNull()?.message
                )
            } else {
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun createGroup() {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isCreatingGroup = true, error = null)
            val result = groupRepository.createGroup(uid)
            if (result.isSuccess) {
                val groupId = result.getOrNull()
                // Refresh metadata to reflect new groupId
                fetchUserMetadata(uid)
                _authState.value = _authState.value.copy(
                    isCreatingGroup = false,
                    groupCreatedId = groupId
                )
            } else {
                _authState.value = _authState.value.copy(
                    isCreatingGroup = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun joinGroup(inviteCode: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isJoiningGroup = true, error = null)
            val result = groupRepository.joinGroup(uid, inviteCode)
            if (result.isSuccess) {
                val groupId = result.getOrNull()
                fetchUserMetadata(uid)
                _authState.value = _authState.value.copy(
                    isJoiningGroup = false,
                    groupCreatedId = groupId
                )
            } else {
                _authState.value = _authState.value.copy(
                    isJoiningGroup = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun leaveGroup(groupId: String) {
        val uid = authRepository.currentUser?.uid ?: return
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLeavingGroup = true, error = null)
            val result = groupRepository.leaveGroup(uid, groupId)
            if (result.isSuccess) {
                fetchUserMetadata(uid)
                _authState.value = _authState.value.copy(isLeavingGroup = false)
            } else {
                _authState.value = _authState.value.copy(
                    isLeavingGroup = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun resetGroupCreationState() {
        _authState.value = _authState.value.copy(groupCreatedId = null)
    }

    fun logout() {
        authRepository.logout()
        _authState.value = AuthState()
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}
