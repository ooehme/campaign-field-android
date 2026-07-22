package de.oliveroehme.campaignfield.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.domain.auth.TeamDetail
import de.oliveroehme.campaignfield.domain.auth.TeamInvitation
import de.oliveroehme.campaignfield.network.auth.ProfileRemoteDataSource
import de.oliveroehme.campaignfield.network.auth.ProfileResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class TeamDetailUiState(
    val isLoading: Boolean = true,
    val detail: TeamDetail? = null,
    val errorMessage: String? = null,
)

data class ProfileUiState(
    val teams: Map<String, TeamDetailUiState> = emptyMap(),
    val invitations: List<TeamInvitation> = emptyList(),
    val invitationsLoading: Boolean = true,
    val invitationsError: String? = null,
    val invitationActionId: String? = null,
    val invitationActionAccept: Boolean? = null,
    val invitationActionError: String? = null,
    val profileRefreshRevision: Int = 0,
)

class ProfileViewModel(
    private val remote: ProfileRemoteDataSource,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var invitationJob: Job? = null

    fun refresh(teamIds: List<String>) {
        if (loadJob?.isActive == true) return
        val ids = teamIds.filter(String::isNotBlank).distinct()
        mutableState.update { current ->
            current.copy(
                teams = ids.associateWith { id ->
                    current.teams[id]?.copy(isLoading = true, errorMessage = null)
                        ?: TeamDetailUiState()
                },
                invitationsLoading = true,
                invitationsError = null,
                invitationActionError = null,
            )
        }
        loadJob = viewModelScope.launch {
            supervisorScope {
                val teamJobs = ids.map { id ->
                    async {
                        val teamState = when (val result = remote.loadTeam(id)) {
                            is ProfileResult.Success -> TeamDetailUiState(
                                isLoading = false,
                                detail = result.value,
                            )
                            is ProfileResult.Failure -> TeamDetailUiState(
                                isLoading = false,
                                errorMessage = result.userMessage,
                            )
                        }
                        mutableState.update { current ->
                            current.copy(teams = current.teams + (id to teamState))
                        }
                    }
                }
                val invitationsJob = async {
                    when (val result = remote.loadInvitations()) {
                        is ProfileResult.Success -> mutableState.update {
                            it.copy(
                                invitations = result.value,
                                invitationsLoading = false,
                                invitationsError = null,
                            )
                        }
                        is ProfileResult.Failure -> mutableState.update {
                            it.copy(
                                invitationsLoading = false,
                                invitationsError = result.userMessage,
                            )
                        }
                    }
                }
                teamJobs.awaitAll()
                invitationsJob.await()
            }
        }
    }

    fun respondToInvitation(id: String, accept: Boolean) {
        if (invitationJob?.isActive == true) return
        mutableState.update {
            it.copy(
                invitationActionId = id,
                invitationActionAccept = accept,
                invitationActionError = null,
            )
        }
        invitationJob = viewModelScope.launch {
            val result = if (accept) remote.acceptInvitation(id) else remote.declineInvitation(id)
            when (result) {
                is ProfileResult.Success -> mutableState.update { current ->
                    current.copy(
                        invitations = current.invitations.filterNot { it.id == id },
                        invitationActionId = null,
                        invitationActionAccept = null,
                        invitationActionError = null,
                        profileRefreshRevision = current.profileRefreshRevision + 1,
                    )
                }
                is ProfileResult.Failure -> mutableState.update {
                    it.copy(
                        invitationActionId = null,
                        invitationActionAccept = null,
                        invitationActionError = result.userMessage,
                    )
                }
            }
        }
    }

    fun consumeProfileRefresh() {
        mutableState.update { it.copy(profileRefreshRevision = 0) }
    }

    companion object {
        fun factory(remote: ProfileRemoteDataSource): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ProfileViewModel::class.java))
                    return ProfileViewModel(remote) as T
                }
            }
    }
}
