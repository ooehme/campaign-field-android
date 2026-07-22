package de.oliveroehme.campaignfield.ui.assignment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.domain.auth.leadsAssignmentTeam
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import de.oliveroehme.campaignfield.network.assignment.AssignmentDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AssignmentListUiState(
    val isLoading: Boolean = true,
    val items: List<AssignmentSummary> = emptyList(),
    val errorMessage: String? = null,
    val isUsingCachedData: Boolean = false,
    val cachedAtEpochMillis: Long? = null,
    val offlineReadyAssignmentIds: Set<String> = emptySet(),
    val teamLeadAssignmentIds: Set<String> = emptySet(),
    val changingStatusAssignmentId: String? = null,
    val changingTargetStatus: AssignmentStatus? = null,
    val statusErrors: Map<String, String> = emptyMap(),
)

class AssignmentListViewModel(
    private val repository: AssignmentRepository,
    private val profile: UserProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentListUiState())
    val state: StateFlow<AssignmentListUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var statusChangeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCachedAssignments(profile).collect { cachedPage ->
                if (cachedPage != null) {
                    mutableState.update { current ->
                        current.copy(
                            items = cachedPage.items,
                            teamLeadAssignmentIds = cachedPage.items
                                .filter(profile::leadsAssignmentTeam)
                                .map(AssignmentSummary::id)
                                .toSet(),
                        )
                    }
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
                        teamLeadAssignmentIds = result.value.items
                            .filter(profile::leadsAssignmentTeam)
                            .map(AssignmentSummary::id)
                            .toSet(),
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

    fun changeStatus(assignment: AssignmentSummary, targetStatus: AssignmentStatus) {
        if (statusChangeJob?.isActive == true || assignment.id !in mutableState.value.teamLeadAssignmentIds) {
            return
        }
        mutableState.update { current ->
            current.copy(
                changingStatusAssignmentId = assignment.id,
                changingTargetStatus = targetStatus,
                statusErrors = current.statusErrors - assignment.id,
            )
        }
        statusChangeJob = viewModelScope.launch {
            val detail = when (val result = repository.loadAssignment(assignment.id)) {
                is AssignmentResult.Success -> result.value
                is AssignmentResult.Failure -> {
                    mutableState.update { current ->
                        current.copy(
                            changingStatusAssignmentId = null,
                            changingTargetStatus = null,
                            statusErrors = current.statusErrors +
                                (assignment.id to result.failure.userMessage),
                        )
                    }
                    return@launch
                }
            }
            when (val result = repository.changeStatus(profile, detail, targetStatus)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    current.copy(
                        items = current.items.map { item ->
                            if (item.id == assignment.id) {
                                result.value.assignment.summary
                            } else item
                        },
                        changingStatusAssignmentId = null,
                        changingTargetStatus = null,
                        statusErrors = current.statusErrors - assignment.id,
                    )
                }
                is AssignmentResult.Failure -> mutableState.update { current ->
                    current.copy(
                        changingStatusAssignmentId = null,
                        changingTargetStatus = null,
                        statusErrors = current.statusErrors +
                            (assignment.id to result.failure.userMessage),
                    )
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
    val mapData: AssignmentMapData? = null,
    val isMapDataLoading: Boolean = false,
    val mapDataErrorMessage: String? = null,
    val canChangeStatus: Boolean = false,
    val changingBuildingId: String? = null,
    val buildingStatusMessage: String? = null,
)

data class ScannerUiState(
    val isLoading: Boolean = true,
    val activeAssignments: List<AssignmentDetail> = emptyList(),
    val mapEntries: List<ScannerAssignmentMapEntry> = emptyList(),
    val errorMessage: String? = null,
    val changingBuildingId: String? = null,
    val buildingStatusMessage: String? = null,
)

data class ScannerAssignmentMapEntry(
    val assignment: AssignmentDetail,
    val mapData: AssignmentMapData,
)

class ScannerViewModel(
    private val repository: AssignmentRepository,
    private val profile: UserProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var buildingStatusJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCachedAssignments(profile)
                .map { page ->
                    page?.items.orEmpty().filter { it.status == AssignmentStatus.ACTIVE }
                }
                .distinctUntilChanged()
                .collectLatest { active ->
                    val activeIds = active.map(AssignmentSummary::id)
                    val currentIds = mutableState.value.activeAssignments.map { it.summary.id }
                    if (!mutableState.value.isLoading && activeIds == currentIds) {
                        return@collectLatest
                    }
                    loadActiveAssignments(active)
                }
        }
        refresh()
    }

    fun refresh() {
        if (loadJob?.isActive == true) return
        mutableState.value = ScannerUiState()
        loadJob = viewModelScope.launch {
            when (val result = repository.loadAssignments(profile)) {
                is AssignmentResult.Failure -> mutableState.value = ScannerUiState(
                    isLoading = false,
                    errorMessage = result.failure.userMessage,
                )
                is AssignmentResult.Success -> {
                    val active = result.value.items.filter { it.status == AssignmentStatus.ACTIVE }
                    loadActiveAssignments(active)
                }
            }
        }
    }

    fun changeBuildingStatus(
        assignmentId: String,
        building: AssignmentMapFeature,
        status: BuildingStatus,
    ) {
        val entry = mutableState.value.mapEntries.firstOrNull {
            it.assignment.summary.id == assignmentId
        } ?: return
        if (buildingStatusJob?.isActive == true) return
        mutableState.update {
            it.copy(
                changingBuildingId = building.id,
                errorMessage = null,
                buildingStatusMessage = null,
            )
        }
        buildingStatusJob = viewModelScope.launch {
            when (val result = repository.changeBuildingStatus(entry.assignment, building, status)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    current.copy(
                        mapEntries = current.mapEntries.map { currentEntry ->
                            if (currentEntry.assignment.summary.id != assignmentId) {
                                currentEntry
                            } else {
                                currentEntry.copy(
                                    mapData = currentEntry.mapData.copy(
                                        features = currentEntry.mapData.features.map { feature ->
                                            if (feature.id == building.id) {
                                                result.value.building
                                            } else feature
                                        },
                                    ),
                                )
                            }
                        },
                        changingBuildingId = null,
                        buildingStatusMessage = if (result.value.queued) {
                            "Gebäude lokal gespeichert. Synchronisation ausstehend."
                        } else {
                            "Gebäude aktualisiert."
                        },
                    )
                }
                is AssignmentResult.Failure -> mutableState.update { current ->
                    current.copy(
                        changingBuildingId = null,
                        errorMessage = result.failure.userMessage,
                    )
                }
            }
        }
    }

    private suspend fun loadActiveAssignments(active: List<AssignmentSummary>) {
        val details = active.mapNotNull { summary ->
            (repository.loadAssignment(summary.id) as? AssignmentResult.Success)?.value
        }
        var mapDataFailureMessage: String? = null
        val mapEntries = details.mapNotNull { assignment ->
            when (val result = repository.loadAssignmentMapData(assignment)) {
                is AssignmentResult.Success -> ScannerAssignmentMapEntry(assignment, result.value)
                is AssignmentResult.Failure -> {
                    mapDataFailureMessage = mapDataFailureMessage ?: result.failure.userMessage
                    null
                }
            }
        }
        mutableState.value = ScannerUiState(
            isLoading = false,
            activeAssignments = details,
            mapEntries = mapEntries,
            errorMessage = when {
                details.size < active.size ->
                    "Ein Teil der aktiven Aufträge konnte nicht geladen werden."
                mapEntries.size < details.size ->
                    mapDataFailureMessage ?: "Ein Teil der Kartendaten konnte nicht geladen werden."
                else -> null
            },
        )
    }

    companion object {
        fun factory(
            repository: AssignmentRepository,
            profile: UserProfile,
        ): ViewModelProvider.Factory = factoryFor {
            ScannerViewModel(repository, profile)
        }
    }
}

class AssignmentDetailViewModel(
    private val repository: AssignmentRepository,
    private val assignmentId: String,
    private val profile: UserProfile,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AssignmentDetailUiState())
    val state: StateFlow<AssignmentDetailUiState> = mutableState.asStateFlow()
    private var loadJob: Job? = null
    private var statusJob: Job? = null
    private var buildingStatusJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeCachedAssignment(assignmentId).collect { assignment ->
                if (assignment != null) {
                    mutableState.update { current ->
                        current.copy(
                            assignment = assignment,
                            canChangeStatus = profile.leadsAssignmentTeam(assignment),
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            repository.observeCachedAssignmentMapData(assignmentId).collect { mapData ->
                if (mapData != null) {
                    mutableState.update { current -> current.copy(mapData = mapData) }
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
                is AssignmentResult.Success -> {
                    mutableState.update { current ->
                        AssignmentDetailUiState(
                            isLoading = false,
                            assignment = result.value,
                            isUsingCachedData = result.source == AssignmentDataSource.LOCAL_CACHE,
                            cachedAtEpochMillis = result.cachedAtEpochMillis,
                            mapData = current.mapData,
                            isMapDataLoading = true,
                            canChangeStatus = profile.leadsAssignmentTeam(result.value),
                        )
                    }
                    when (val mapResult = repository.loadAssignmentMapData(result.value)) {
                        is AssignmentResult.Success -> mutableState.update { current ->
                            current.copy(
                                mapData = mapResult.value,
                                isMapDataLoading = false,
                                mapDataErrorMessage = null,
                            )
                        }
                        is AssignmentResult.Failure -> mutableState.update { current ->
                            current.copy(
                                isMapDataLoading = false,
                                mapDataErrorMessage = mapResult.failure.userMessage,
                            )
                        }
                    }
                }
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
            when (val result = repository.changeStatus(profile, assignment, status)) {
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

    fun changeBuildingStatus(building: AssignmentMapFeature, status: BuildingStatus) {
        val assignment = mutableState.value.assignment ?: return
        if (buildingStatusJob?.isActive == true) return
        mutableState.update {
            it.copy(
                changingBuildingId = building.id,
                errorMessage = null,
                buildingStatusMessage = null,
            )
        }
        buildingStatusJob = viewModelScope.launch {
            when (val result = repository.changeBuildingStatus(assignment, building, status)) {
                is AssignmentResult.Success -> mutableState.update { current ->
                    current.copy(
                        mapData = current.mapData?.copy(
                            features = current.mapData.features.map { feature ->
                                if (feature.id == building.id) result.value.building else feature
                            },
                        ),
                        changingBuildingId = null,
                        buildingStatusMessage = if (result.value.queued) {
                            "Gebäude lokal gespeichert. Synchronisation ausstehend."
                        } else {
                            "Gebäude aktualisiert."
                        },
                    )
                }
                is AssignmentResult.Failure -> mutableState.update { current ->
                    current.copy(
                        changingBuildingId = null,
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
            profile: UserProfile,
        ): ViewModelProvider.Factory = factoryFor {
            AssignmentDetailViewModel(repository, assignmentId, profile)
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
