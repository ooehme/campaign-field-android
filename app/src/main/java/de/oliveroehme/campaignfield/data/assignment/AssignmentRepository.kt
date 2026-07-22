package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.data.Repository
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.domain.availableStatusActions
import de.oliveroehme.campaignfield.domain.forOperativeOverview
import de.oliveroehme.campaignfield.domain.auth.leadsAssignmentTeam
import de.oliveroehme.campaignfield.network.AlwaysOnlineNetworkStateProvider
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.network.assignment.AssignmentDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailure
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailureKind
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import de.oliveroehme.campaignfield.sync.NoOpSyncScheduler
import de.oliveroehme.campaignfield.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AssignmentStatusChange(
    val assignment: AssignmentDetail,
    val queued: Boolean,
)

data class AssignmentBuildingStatusChange(
    val building: AssignmentMapFeature,
    val queued: Boolean,
)

interface AssignmentRepository : Repository {
    suspend fun loadAssignments(profile: UserProfile): AssignmentResult<AssignmentPage>
    suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail>
    suspend fun loadAssignmentMapData(
        assignment: AssignmentDetail,
    ): AssignmentResult<AssignmentMapData> = AssignmentResult.Success(AssignmentMapData())
    suspend fun warmAssignments(ids: List<String>)
    suspend fun changeStatus(
        profile: UserProfile,
        assignment: AssignmentDetail,
        targetStatus: AssignmentStatus,
    ): AssignmentResult<AssignmentStatusChange>
    suspend fun changeBuildingStatus(
        assignment: AssignmentDetail,
        building: AssignmentMapFeature,
        targetStatus: BuildingStatus,
    ): AssignmentResult<AssignmentBuildingStatusChange>
    fun observeCachedAssignments(profile: UserProfile): Flow<AssignmentPage?>
    fun observeCachedAssignment(id: String): Flow<AssignmentDetail?>
    fun observeCachedAssignmentMapData(id: String): Flow<AssignmentMapData?>
    fun observeOfflineReadyAssignmentIds(): Flow<Set<String>>
}

class DefaultAssignmentRepository(
    private val remote: AssignmentRemoteDataSource,
    private val offlineStore: OfflineStore? = null,
    private val syncScheduler: SyncScheduler = NoOpSyncScheduler,
    private val networkStateProvider: NetworkStateProvider = AlwaysOnlineNetworkStateProvider,
) : AssignmentRepository {
    private val mapDataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mapDataRequestsMutex = Mutex()
    private val mapDataRequests = mutableMapOf<String, Deferred<AssignmentResult<AssignmentMapData>>>()

    override suspend fun loadAssignments(profile: UserProfile): AssignmentResult<AssignmentPage> {
        val remoteResult = remote.loadAssignments(
            userId = profile.id,
            teamIds = profile.teams.mapNotNull { it.teamId },
        )
        return when (remoteResult) {
            is AssignmentResult.Success -> {
                val page = remoteResult.value.copy(
                    items = normalizeForProfile(remoteResult.value.items, profile),
                )
                offlineStore?.storeAssignments(page)
                val effectivePage = offlineStore?.readAssignments()?.value ?: page
                AssignmentResult.Success(effectivePage)
            }
            is AssignmentResult.Failure -> {
                val cached = offlineStore?.readAssignments()
                if (cached != null && remoteResult.failure.kind.isCacheFallbackAllowed()) {
                    AssignmentResult.Success(
                        value = cached.value.copy(
                            items = normalizeForProfile(cached.value.items, profile),
                        ),
                        source = AssignmentDataSource.LOCAL_CACHE,
                        cachedAtEpochMillis = cached.cachedAtEpochMillis,
                    )
                } else {
                    remoteResult
                }
            }
        }
    }

    override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> {
        return when (val result = remote.loadAssignment(id)) {
            is AssignmentResult.Success -> {
                offlineStore?.storeAssignment(result.value)
                result
            }
            is AssignmentResult.Failure -> {
                val cached = offlineStore?.readAssignment(id)
                if (cached != null && result.failure.kind.isCacheFallbackAllowed()) {
                    AssignmentResult.Success(
                        value = cached.value,
                        source = AssignmentDataSource.LOCAL_CACHE,
                        cachedAtEpochMillis = cached.cachedAtEpochMillis,
                    )
                } else {
                    result
                }
            }
        }
    }

    override suspend fun loadAssignmentMapData(
        assignment: AssignmentDetail,
    ): AssignmentResult<AssignmentMapData> {
        val assignmentId = assignment.summary.id
        val request = mapDataRequestsMutex.withLock {
            mapDataRequests[assignmentId] ?: mapDataScope.async {
                loadAndCacheAssignmentMapData(assignment)
            }.also { created ->
                mapDataRequests[assignmentId] = created
                created.invokeOnCompletion {
                    mapDataScope.launch {
                        mapDataRequestsMutex.withLock {
                            if (mapDataRequests[assignmentId] === created) {
                                mapDataRequests.remove(assignmentId)
                            }
                        }
                    }
                }
            }
        }
        return request.await()
    }

    private suspend fun loadAndCacheAssignmentMapData(
        assignment: AssignmentDetail,
    ): AssignmentResult<AssignmentMapData> {
        return when (
            val result = remote.loadAssignmentMapData(
                id = assignment.summary.id,
                type = assignment.summary.type,
            )
        ) {
            is AssignmentResult.Success -> {
                offlineStore?.storeAssignmentMapData(assignment.summary.id, result.value)
                AssignmentResult.Success(
                    offlineStore?.readAssignmentMapData(assignment.summary.id)?.value ?: result.value,
                )
            }
            is AssignmentResult.Failure -> {
                val cached = offlineStore?.readAssignmentMapData(assignment.summary.id)
                if (cached != null && result.failure.kind.isCacheFallbackAllowed()) {
                    AssignmentResult.Success(
                        value = cached.value,
                        source = AssignmentDataSource.LOCAL_CACHE,
                        cachedAtEpochMillis = cached.cachedAtEpochMillis,
                    )
                } else {
                    result
                }
            }
        }
    }

    override suspend fun warmAssignments(ids: List<String>) {
        if (offlineStore == null || !networkStateProvider.isOnline()) return
        ids.distinct().forEach { id ->
            val cached = offlineStore.readAssignment(id)
            val detail = if (
                cached?.value?.description != null ||
                cached?.value?.instructions?.isNotEmpty() == true
            ) {
                cached.value
            } else {
                val remoteDetail = remote.loadAssignment(id) as? AssignmentResult.Success
                    ?: return@forEach
                offlineStore.storeAssignment(remoteDetail.value)
                remoteDetail.value
            }
            if (offlineStore.readAssignmentMapData(id) == null) {
                loadAssignmentMapData(detail)
            }
        }
    }

    override suspend fun changeStatus(
        profile: UserProfile,
        assignment: AssignmentDetail,
        targetStatus: AssignmentStatus,
    ): AssignmentResult<AssignmentStatusChange> {
        if (!profile.leadsAssignmentTeam(assignment)) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    kind = AssignmentFailureKind.FORBIDDEN,
                    httpStatus = 403,
                    userMessage = "Nur die Teamleitung darf den Auftragsstatus ändern.",
                ),
            )
        }
        val isAllowed = assignment.availableStatusActions().any { it.targetStatus == targetStatus }
        if (!isAllowed) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    kind = AssignmentFailureKind.FORBIDDEN,
                    httpStatus = 403,
                    userMessage = "Keine Berechtigung für diese Statusänderung.",
                ),
            )
        }

        if (!networkStateProvider.isOnline()) {
            return queueStatusChange(assignment, targetStatus)
        }

        return when (val result = remote.updateAssignmentStatus(assignment.summary.id, targetStatus)) {
            is AssignmentResult.Success -> {
                offlineStore?.storeAssignment(result.value)
                AssignmentResult.Success(AssignmentStatusChange(result.value, queued = false))
            }
            is AssignmentResult.Failure -> if (result.failure.kind.isRetryableMutation()) {
                queueStatusChange(assignment, targetStatus)
            } else {
                result
            }
        }
    }

    override suspend fun changeBuildingStatus(
        assignment: AssignmentDetail,
        building: AssignmentMapFeature,
        targetStatus: BuildingStatus,
    ): AssignmentResult<AssignmentBuildingStatusChange> {
        if (assignment.summary.status != AssignmentStatus.ACTIVE) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.CONFLICT,
                    409,
                    "Gebäude können nur in einem aktiven Auftrag bearbeitet werden.",
                ),
            )
        }
        if (building.kind != AssignmentMapFeatureKind.BUILDING || !building.canUpdate) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.FORBIDDEN,
                    403,
                    "Keine Berechtigung für diese Gebäudeänderung.",
                ),
            )
        }
        if (targetStatus !in BuildingStatus.actionStatuses) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.VALIDATION,
                    422,
                    "Unbekannter Gebäude-Status.",
                ),
            )
        }
        if (building.isPendingSync) {
            return AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.CONFLICT,
                    409,
                    "Für dieses Gebäude ist bereits eine Synchronisierung offen.",
                ),
            )
        }

        if (!networkStateProvider.isOnline()) {
            return queueBuildingStatusChange(assignment, building, targetStatus)
        }
        return when (val result = remote.updateAssignmentBuildingStatus(building.id, targetStatus)) {
            is AssignmentResult.Success -> {
                offlineStore?.updateAssignmentBuildingStatus(
                    assignment.summary.id,
                    building.id,
                    targetStatus,
                )
                AssignmentResult.Success(
                    AssignmentBuildingStatusChange(
                        building.copy(status = targetStatus),
                        queued = false,
                    ),
                )
            }
            is AssignmentResult.Failure -> if (result.failure.kind.isRetryableMutation()) {
                queueBuildingStatusChange(assignment, building, targetStatus)
            } else {
                result
            }
        }
    }

    override fun observeCachedAssignments(profile: UserProfile): Flow<AssignmentPage?> =
        offlineStore?.observeAssignments()?.map { cached ->
            cached?.value?.copy(items = normalizeForProfile(cached.value.items, profile))
        } ?: flowOf(null)

    override fun observeCachedAssignment(id: String): Flow<AssignmentDetail?> =
        offlineStore?.observeAssignment(id)?.map { it?.value } ?: flowOf(null)

    override fun observeCachedAssignmentMapData(id: String): Flow<AssignmentMapData?> =
        offlineStore?.observeAssignmentMapData(id)?.map { it?.value } ?: flowOf(null)

    override fun observeOfflineReadyAssignmentIds(): Flow<Set<String>> =
        offlineStore?.observeOfflineReadyAssignmentIds() ?: flowOf(emptySet())

    private suspend fun queueStatusChange(
        assignment: AssignmentDetail,
        targetStatus: AssignmentStatus,
    ): AssignmentResult<AssignmentStatusChange> {
        val store = offlineStore ?: return AssignmentResult.Failure(AssignmentFailure.network())
        store.enqueueAssignmentStatusUpdate(
            assignmentId = assignment.summary.id,
            previousStatus = assignment.summary.status,
            targetStatus = targetStatus,
        )
        syncScheduler.schedule()
        val localAssignment = assignment.copy(
            summary = assignment.summary.copy(status = targetStatus),
        )
        return AssignmentResult.Success(AssignmentStatusChange(localAssignment, queued = true))
    }

    private suspend fun queueBuildingStatusChange(
        assignment: AssignmentDetail,
        building: AssignmentMapFeature,
        targetStatus: BuildingStatus,
    ): AssignmentResult<AssignmentBuildingStatusChange> {
        val store = offlineStore ?: return AssignmentResult.Failure(AssignmentFailure.network())
        store.enqueueAssignmentBuildingStatusUpdate(
            assignmentId = assignment.summary.id,
            buildingId = building.id,
            previousStatus = building.status ?: BuildingStatus.OPEN,
            targetStatus = targetStatus,
        )
        store.updateAssignmentBuildingStatus(assignment.summary.id, building.id, targetStatus)
        syncScheduler.schedule()
        return AssignmentResult.Success(
            AssignmentBuildingStatusChange(
                building.copy(status = targetStatus, isPendingSync = true),
                queued = true,
            ),
        )
    }

    private fun normalizeForProfile(
        assignments: List<AssignmentSummary>,
        profile: UserProfile,
    ): List<AssignmentSummary> {
        val teamsById = profile.teams
            .mapNotNull { membership ->
                membership.teamId?.let { teamId -> teamId to membership.teamName }
            }
            .toMap()
        return assignments
            .map { assignment ->
                val currentTeam = assignment.team
                val teamId = currentTeam?.id
                val teamName = teamId?.let(teamsById::get)
                if (currentTeam != null && teamName != null && currentTeam.name.startsWith("Team #")) {
                    assignment.copy(team = currentTeam.copy(name = teamName))
                } else {
                    assignment
                }
            }
            .filter { assignment ->
                teamsById.isEmpty() || assignment.team?.id?.let(teamsById::containsKey) == true
            }
            .forOperativeOverview()
    }

    private fun AssignmentFailureKind.isCacheFallbackAllowed(): Boolean =
        this == AssignmentFailureKind.NETWORK || this == AssignmentFailureKind.SERVER

    private fun AssignmentFailureKind.isRetryableMutation(): Boolean =
        this == AssignmentFailureKind.NETWORK || this == AssignmentFailureKind.SERVER
}
