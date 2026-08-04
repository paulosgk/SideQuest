package com.example.sidequest.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sidequest.data.AuthRepository
import com.example.sidequest.data.Group
import com.example.sidequest.data.GroupRepository
import com.example.sidequest.data.UserMetadata
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class GroupState(
    val group: Group? = null,
    val members: List<UserMetadata> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class GroupViewModel(
    private val groupId: String,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GroupState())
    val state: StateFlow<GroupState> = _state.asStateFlow()

    init {
        listenToGroupAndMembers()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun listenToGroupAndMembers() {
        groupRepository.getGroupFlow(groupId)
            .onEach { group ->
                if (group != null) {
                    _state.value = _state.value.copy(group = group, isLoading = false)
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Group not found"
                    )
                }
            }
            .flatMapLatest { group ->
                if (group != null) {
                    authRepository.getUsersMetadataFlow(group.members)
                } else {
                    flowOf(emptyList())
                }
            }
            .onEach { members ->
                _state.value = _state.value.copy(members = members)
            }
            .launchIn(viewModelScope)
    }

    fun startMatch() {
        val groupId = _state.value.group?.id ?: return
        viewModelScope.launch {
            val result = groupRepository.startMatch(groupId)
            if (result.isFailure) {
                _state.value = _state.value.copy(error = result.exceptionOrNull()?.message)
            }
        }
    }
}
