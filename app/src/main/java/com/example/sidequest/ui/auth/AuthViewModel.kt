package com.example.sidequest.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sidequest.data.AuthRepository
import com.example.sidequest.data.Challenge
import com.example.sidequest.data.ChallengeRepository
import com.example.sidequest.data.GroupRepository
import com.example.sidequest.data.UserMetadata
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseUser
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AuthState(
    val user: FirebaseUser? = null,
    val userMetadata: UserMetadata? = null,
    val isLoading: Boolean = false,
    val isCreatingGroup: Boolean = false,
    val isLeavingGroup: Boolean = false,
    val isJoiningGroup: Boolean = false,
    val isSeeding: Boolean = false,
    val groupCreatedId: String? = null,
    val error: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository,
    private val groupRepository: GroupRepository,
    private val challengeRepository: ChallengeRepository
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState(user = authRepository.currentUser))
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var metadataJob: Job? = null

    init {
        authRepository.currentUser?.uid?.let { startListeningToMetadata(it) }
    }

    private fun startListeningToMetadata(uid: String) {
        metadataJob?.cancel()
        metadataJob = authRepository.getUserMetadataFlow(uid)
            .onEach { metadata ->
                _authState.value = _authState.value.copy(userMetadata = metadata)
            }
            .launchIn(viewModelScope)
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isLoading = true, error = null)
            val result = authRepository.login(email, password)
            val user = result.getOrNull()
            
            if (user != null) {
                startListeningToMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user
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
                startListeningToMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user
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
                startListeningToMetadata(user.uid)
                _authState.value = _authState.value.copy(
                    isLoading = false,
                    user = user
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
                _authState.value = _authState.value.copy(isLeavingGroup = false)
            } else {
                _authState.value = _authState.value.copy(
                    isLeavingGroup = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun seedDefaultChallenges(jsonString: String) {
        viewModelScope.launch {
            _authState.value = _authState.value.copy(isSeeding = true, error = null)
            try {
                val listType = object : TypeToken<List<Challenge>>() {}.type
                val challenges: List<Challenge> = Gson().fromJson(jsonString, listType)
                val result = challengeRepository.seedChallenges(challenges)
                if (result.isSuccess) {
                    _authState.value = _authState.value.copy(isSeeding = false)
                } else {
                    _authState.value = _authState.value.copy(
                        isSeeding = false,
                        error = result.exceptionOrNull()?.message
                    )
                }
            } catch (e: Exception) {
                _authState.value = _authState.value.copy(
                    isSeeding = false,
                    error = "Failed to parse challenges: ${e.message}"
                )
            }
        }
    }

    fun resetGroupCreationState() {
        _authState.value = _authState.value.copy(groupCreatedId = null)
    }

    fun logout() {
        metadataJob?.cancel()
        authRepository.logout()
        _authState.value = AuthState()
    }

    fun clearError() {
        _authState.value = _authState.value.copy(error = null)
    }
}
