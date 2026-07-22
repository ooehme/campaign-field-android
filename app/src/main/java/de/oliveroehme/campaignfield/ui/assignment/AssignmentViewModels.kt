package de.oliveroehme.campaignfield.ui.assignment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.TeamSummary
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentListUiState(
    val isLoading: Boolean = true,
    val items: List<AssignmentSummary> = emptyList(),
    val selectedTeamId: String? = null,
    val errorMessage: String? = null,
) {
    val visibleItems: List<AssignmentSummary>
        get() = selectedTeamId?.let { teamId -> items.filter { it.team?.id == teamId } } ?: items

    val teamFilters: List<TeamSummary>
        get() = items.mapNotNull(AssignmentSummary::team)
            .filter { it.id != null }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }
}

class AssignmentListViewModel(
    private val repository: AssignmentRepository,
    private val profile: UserProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentListUiState())
    val state: StateFlow<AssignmentListUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.loadAssignments(profile)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    val selectedTeam = current.selectedTeamId
                        ?.takeIf { selected -> result.value.items.any { it.team?.id == selected } }
                    current.copy(
                        isLoading = false,
                        items = result.value.items,
                        selectedTeamId = selectedTeam,
                        errorMessage = null,
                    )
                }
                is AssignmentResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.failure.userMessage)
                }
            }
        }
    }

    fun selectTeam(teamId: String?) {
        mutableState.update { it.copy(selectedTeamId = teamId) }
    }

    companion object {
        fun factory(
            repository: AssignmentRepository,
            profile: UserProfile,
        ): ViewModelProvider.Factory = factoryFor {
            AssignmentListViewModel(repository, profile)
        }
    }
}

data class AssignmentDetailUiState(
    val isLoading: Boolean = true,
    val assignment: AssignmentDetail? = null,
    val errorMessage: String? = null,
)

class AssignmentDetailViewModel(
    private val repository: AssignmentRepository,
    private val assignmentId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentDetailUiState())
    val state: StateFlow<AssignmentDetailUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.loadAssignment(assignmentId)) {
                is AssignmentResult.Success -> mutableState.value = AssignmentDetailUiState(
                    isLoading = false,
                    assignment = result.value,
                )
                is AssignmentResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.failure.userMessage)
                }
            }
        }
    }

    companion object {
        fun factory(
            repository: AssignmentRepository,
            assignmentId: String,
        ): ViewModelProvider.Factory = factoryFor {
            AssignmentDetailViewModel(repository, assignmentId)
        }
    }
}

private inline fun <reified T : ViewModel> factoryFor(
    crossinline create: () -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        require(modelClass.isAssignableFrom(T::class.java))
        return create() as VM
    }
}
