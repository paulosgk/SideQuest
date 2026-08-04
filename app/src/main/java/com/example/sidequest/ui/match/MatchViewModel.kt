package com.example.sidequest.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sidequest.data.Match
import com.example.sidequest.data.MatchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MatchState(
    val activeMatch: Match? = null,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val matchCreatedId: String? = null
)

class MatchViewModel(
    private val groupId: String,
    private val matchRepository: MatchRepository,
    private val userId: String
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    init {
        listenToActiveMatch()
    }

    private fun listenToActiveMatch() {
        matchRepository.getActiveMatchFlow(groupId)
            .onEach { match ->
                _state.value = _state.value.copy(activeMatch = match)
            }
            .launchIn(viewModelScope)
    }

    fun createMatch(challengeCount: Int) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCreating = true, error = null)
            val result = matchRepository.createMatch(groupId, userId, challengeCount)
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isCreating = false,
                    matchCreatedId = result.getOrNull()
                )
            } else {
                _state.value = _state.value.copy(
                    isCreating = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun resetMatchCreationState() {
        _state.value = _state.value.copy(matchCreatedId = null)
    }
}
