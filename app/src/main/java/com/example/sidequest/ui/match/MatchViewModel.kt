package com.example.sidequest.ui.match

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sidequest.data.Challenge
import com.example.sidequest.data.ChallengeRepository
import com.example.sidequest.data.ChallengeStatus
import com.example.sidequest.data.ChallengeWithAssignment
import com.example.sidequest.data.Match
import com.example.sidequest.data.MatchRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class MatchState(
    val activeMatch: Match? = null,
    val userChallenges: List<ChallengeWithAssignment> = emptyList(),
    val selectedChallenge: ChallengeWithAssignment? = null,
    val isLoading: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
    val matchCreatedId: String? = null,
    val challengeSubmitted: Boolean = false
)

class MatchViewModel(
    private val groupId: String,
    private val matchRepository: MatchRepository,
    private val challengeRepository: ChallengeRepository,
    private val userId: String
) : ViewModel() {

    private val _state = MutableStateFlow(MatchState())
    val state: StateFlow<MatchState> = _state.asStateFlow()

    init {
        listenToActiveMatch()
        listenToUserChallenges()
    }

    private fun listenToActiveMatch() {
        matchRepository.getActiveMatchFlow(groupId)
            .onEach { match ->
                _state.value = _state.value.copy(activeMatch = match)
            }
            .launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun listenToUserChallenges() {
        state.flatMapLatest { matchState ->
            val matchId = matchState.activeMatch?.id
            if (matchId != null) {
                combine(
                    matchRepository.getAssignedChallengesFlow(matchId, userId),
                    challengeRepository.getChallengeTemplatesFlow()
                ) { assignments, templates ->
                    assignments.mapNotNull { assignment ->
                        val template = templates.find { it.id == assignment.challengeId }
                        if (template != null) {
                            ChallengeWithAssignment(template, assignment)
                        } else null
                    }
                }
            } else {
                flowOf(emptyList())
            }
        }.onEach { userChallenges ->
            _state.value = _state.value.copy(userChallenges = userChallenges)
        }.launchIn(viewModelScope)
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

    fun selectChallenge(assignmentId: String) {
        val challenge = _state.value.userChallenges.find { it.assignment.id == assignmentId }
        _state.value = _state.value.copy(selectedChallenge = challenge)
    }

    fun updateChallengeStatus(assignmentId: String, status: ChallengeStatus) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = matchRepository.updateChallengeStatus(assignmentId, status)
            if (result.isSuccess) {
                // The flow from repository will update the UI automatically
                _state.value = _state.value.copy(
                    isLoading = false,
                    challengeSubmitted = status == ChallengeStatus.SUBMITTED
                )
            } else {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun resetChallengeSubmissionState() {
        _state.value = _state.value.copy(challengeSubmitted = false)
    }

    fun resetMatchCreationState() {
        _state.value = _state.value.copy(matchCreatedId = null)
    }
}
