package de.oliveroehme.campaignfield.ui.assignment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import de.oliveroehme.campaignfield.network.assignment.AssignmentDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentListUiState(
    val isLoading: Boolean = true,
    val items: List<AssignmentSummary> = emptyList(),
    val errorMessage: String? = null,
    val isUsingCachedData: Boolean = false,
    val cachedAtEpochMillis: Long? = null,
    val offlineReadyAssignmentIds: Set<String> = emptySet(),
)

class AssignmentListViewModel(
    private val repository: AssignmentRepository,
    private val profile: UserProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentListUiState())
    val state: StateFlow<AssignmentListUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCachedAssignments(profile).collect { cachedPage ->
                if (cachedPage != null) {
                    mutableState.update { current -> current.copy(items = cachedPage.items) }
                }
            }
        }
        viewModelScope.launch {
            repository.observeOfflineReadyAssignmentIds().collect { ids ->
                mutableState.update { current -> current.copy(offlineReadyAssignmentIds = ids) }
            }
        }
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        mutableState.update { it.copy(isLoading = true, errorMessage = null) }
        loadJob = viewModelScope.launch {
            when (val result = repository.loadAssignments(profile)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    current.copy(
                        isLoading = false,
                        items = result.value.items,
                        errorMessage = null,
                        isUsingCachedData = result.source == AssignmentDataSource.LOCAL_CACHE,
                        cachedAtEpochMillis = result.cachedAtEpochMillis,
                    )
                }.also {
                    viewModelScope.launch {
                        repository.warmAssignments(result.value.items.map(AssignmentSummary::id))
                    }
                }
                is AssignmentResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.failure.userMessage)
                }
            }
        }
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
    val isUsingCachedData: Boolean = false,
    val cachedAtEpochMillis: Long? = null,
    val isChangingStatus: Boolean = false,
    val statusMessage: String? = null,
)

class AssignmentDetailViewModel(
    private val repository: AssignmentRepository,
    private val assignmentId: String,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentDetailUiState())
    val state: StateFlow<AssignmentDetailUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var statusJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCachedAssignment(assignmentId).collect { assignment ->
                if (assignment != null) {
                    mutableState.update { current -> current.copy(assignment = assignment) }
                }
            }
        }
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
                    isUsingCachedData = result.source == AssignmentDataSource.LOCAL_CACHE,
                    cachedAtEpochMillis = result.cachedAtEpochMillis,
                )
                is AssignmentResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, errorMessage = result.failure.userMessage)
                }
            }
        }
    }

    fun changeStatus(status: AssignmentStatus) {
        val assignment = mutableState.value.assignment ?: return
        if (statusJob?.isActive == true) return
        mutableState.update {
            it.copy(isChangingStatus = true, errorMessage = null, statusMessage = null)
        }
        statusJob = viewModelScope.launch {
            when (val result = repository.changeStatus(assignment, status)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    current.copy(
                        assignment = result.value.assignment,
                        isChangingStatus = false,
                        statusMessage = if (result.value.queued) {
                            "Lokal gespeichert. Die Änderung wird bei bestehender Verbindung synchronisiert."
                        } else {
                            "Status wurde auf dem Server gespeichert."
                        },
                    )
                }
                is AssignmentResult.Failure -> mutableState.update { current ->
                    current.copy(
                        isChangingStatus = false,
                        errorMessage = result.failure.userMessage,
                    )
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
