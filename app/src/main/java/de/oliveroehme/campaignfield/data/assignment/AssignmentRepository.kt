package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.data.Repository
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.domain.availableStatusActions
import de.oliveroehme.campaignfield.domain.forOperativeOverview
import de.oliveroehme.campaignfield.network.AlwaysOnlineNetworkStateProvider
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.network.assignment.AssignmentDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailure
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailureKind
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import de.oliveroehme.campaignfield.sync.NoOpSyncScheduler
import de.oliveroehme.campaignfield.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class AssignmentStatusChange(
    val assignment: AssignmentDetail,
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
        assignment: AssignmentDetail,
        targetStatus: AssignmentStatus,
    ): AssignmentResult<AssignmentStatusChange>
    fun observeCachedAssignments(profile: UserProfile): Flow<AssignmentPage?>
    fun observeCachedAssignment(id: String): Flow<AssignmentDetail?>
    fun observeOfflineReadyAssignmentIds(): Flow<Set<String>>
}

class DefaultAssignmentRepository(
    private val remote: AssignmentRemoteDataSource,
    private val offlineStore: OfflineStore? = null,
    private val syncScheduler: SyncScheduler = NoOpSyncScheduler,
    private val networkStateProvider: NetworkStateProvider = AlwaysOnlineNetworkStateProvider,
) : AssignmentRepository {
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
                AssignmentResult.Success(page)
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
    ): AssignmentResult<AssignmentMapData> = remote.loadAssignmentMapData(
        id = assignment.summary.id,
        type = assignment.summary.type,
    )

    override suspend fun warmAssignments(ids: List<String>) {
        if (offlineStore == null || !networkStateProvider.isOnline()) return
        ids.distinct().forEach { id ->
            val cached = offlineStore.readAssignment(id)
            if (cached?.value?.description != null || cached?.value?.instructions?.isNotEmpty() == true) {
                return@forEach
            }
            val detail = remote.loadAssignment(id) as? AssignmentResult.Success ?: return@forEach
            offlineStore.storeAssignment(detail.value)
        }
    }

    override suspend fun changeStatus(
        assignment: AssignmentDetail,
        targetStatus: AssignmentStatus,
    ): AssignmentResult<AssignmentStatusChange> {
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

    override fun observeCachedAssignments(profile: UserProfile): Flow<AssignmentPage?> =
        offlineStore?.observeAssignments()?.map { cached ->
            cached?.value?.copy(items = normalizeForProfile(cached.value.items, profile))
        } ?: flowOf(null)

    override fun observeCachedAssignment(id: String): Flow<AssignmentDetail?> =
        offlineStore?.observeAssignment(id)?.map { it?.value } ?: flowOf(null)

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
