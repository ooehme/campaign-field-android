package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.database.CachedValue
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.domain.AssignmentLocationInput
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentPermissions
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.TeamSummary
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.domain.SyncFailureKind
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncQueueStatus
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.domain.auth.TeamMembership
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.network.assignment.AssignmentDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailure
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailureKind
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import de.oliveroehme.campaignfield.sync.AssignmentSyncEngine
import de.oliveroehme.campaignfield.sync.SyncProcessOutcome
import de.oliveroehme.campaignfield.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentOfflineRepositoryTest {
    @Test
    fun `uses persisted assignments when server is unreachable`() = runBlocking {
        val store = FakeOfflineStore().apply {
            assignments = CachedValue(AssignmentPage(listOf(summary())), 1234L)
        }
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(loadResult = networkFailure()),
            offlineStore = store,
        )

        val result = repository.loadAssignments(profile()) as AssignmentResult.Success

        assertEquals(AssignmentDataSource.LOCAL_CACHE, result.source)
        assertEquals(1234L, result.cachedAtEpochMillis)
        assertEquals("assignment-1", result.value.items.single().id)
    }

    @Test
    fun `stores allowed status change in queue while offline`() = runBlocking {
        val store = FakeOfflineStore()
        val scheduler = RecordingScheduler()
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(),
            offlineStore = store,
            syncScheduler = scheduler,
            networkStateProvider = NetworkStateProvider { false },
        )
        val detail = detail(AssignmentStatus.READY)

        val result = repository.changeStatus(profile(), detail, AssignmentStatus.ACTIVE)
            as AssignmentResult.Success

        assertTrue(result.value.queued)
        assertEquals(AssignmentStatus.ACTIVE, result.value.assignment.summary.status)
        assertEquals(AssignmentStatus.ACTIVE, store.queue.value.single().targetStatus)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `stores building status in cache and queue while offline`() = runBlocking {
        val building = AssignmentMapFeature(
            id = "building-1",
            kind = AssignmentMapFeatureKind.BUILDING,
            geometryGeoJson = "{}",
            status = BuildingStatus.OPEN,
            canUpdate = true,
            serverUpdatedAt = "2026-07-22T08:00:00Z",
        )
        val store = FakeOfflineStore().apply {
            mapData = CachedValue(AssignmentMapData(1, features = listOf(building)), 1L)
        }
        val scheduler = RecordingScheduler()
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(),
            offlineStore = store,
            syncScheduler = scheduler,
            networkStateProvider = NetworkStateProvider { false },
        )

        val result = repository.changeBuildingStatus(
            detail(AssignmentStatus.ACTIVE),
            building,
            BuildingStatus.DONE,
        ) as AssignmentResult.Success

        assertTrue(result.value.queued)
        assertEquals(BuildingStatus.DONE, result.value.building.status)
        assertEquals(BuildingStatus.DONE, store.mapData?.value?.features?.single()?.status)
        assertEquals(SyncEventKind.ASSIGNMENT_BUILDING_UPDATE, store.queue.value.single().kind)
        assertEquals("building-1", store.queue.value.single().buildingId)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `stores free poster create as pending overlay while offline`() = runBlocking {
        val store = FakeOfflineStore().apply {
            mapData = CachedValue(AssignmentMapData(), 1L)
        }
        val scheduler = RecordingScheduler()
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(),
            offlineStore = store,
            syncScheduler = scheduler,
            networkStateProvider = NetworkStateProvider { false },
        )
        val assignment = detail(AssignmentStatus.ACTIVE).copy(
            summary = summary(AssignmentStatus.ACTIVE).copy(type = AssignmentType.POSTER_FREE),
            permissions = AssignmentPermissions(managePosterLocations = true),
        )

        val result = repository.createPosterLocation(
            assignment,
            AssignmentLocationInput(50.8, 12.9, label = "Mast"),
        ) as AssignmentResult.Success

        assertTrue(result.value.queued)
        assertTrue(result.value.feature.isPendingSync)
        assertEquals(SyncEventKind.POSTER_LOCATION_CREATE, store.queue.value.single().kind)
        assertEquals(1, store.mapData?.value?.posterCount)
        assertEquals(1, scheduler.calls)
    }

    @Test
    fun `sync replaces local poster with authoritative server feature`() = runBlocking {
        val local = AssignmentMapFeature(
            id = "local-poster",
            kind = AssignmentMapFeatureKind.POSTER,
            geometryGeoJson = """{"type":"Point","coordinates":[12.9,50.8]}""",
            label = "Mast",
            isPendingSync = true,
        )
        val store = FakeOfflineStore().apply {
            mapData = CachedValue(AssignmentMapData(posterCount = 1, features = listOf(local)), 1L)
            enqueueAssignmentMapFeatureMutation(
                "assignment-1",
                SyncEventKind.POSTER_LOCATION_CREATE,
                local,
            )
        }
        val remote = FakeRemote()

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.COMPLETE, outcome)
        assertEquals(50.8, remote.createdPosterInput?.latitude)
        assertEquals("poster-server", store.mapData?.value?.features?.single()?.id)
        assertEquals(SyncQueueStatus.SYNCED, store.queue.value.single().status)
    }

    @Test
    fun `team member cannot complete assignment even with assignment permission`() = runBlocking {
        val repository = DefaultAssignmentRepository(remote = FakeRemote())

        val result = repository.changeStatus(
            profile(role = "member"),
            detail(AssignmentStatus.ACTIVE),
            AssignmentStatus.COMPLETED,
        )

        assertTrue(result is AssignmentResult.Failure)
        assertEquals(
            AssignmentFailureKind.FORBIDDEN,
            (result as AssignmentResult.Failure).failure.kind,
        )
    }

    @Test
    fun `team lead can start assignment through general update permission`() = runBlocking {
        val repository = DefaultAssignmentRepository(remote = FakeRemote())
        val assignment = detail(AssignmentStatus.READY).copy(
            permissions = AssignmentPermissions(update = true),
        )

        val result = repository.changeStatus(
            profile(),
            assignment,
            AssignmentStatus.ACTIVE,
        )

        assertTrue(result is AssignmentResult.Success)
    }

    @Test
    fun `uses persisted map data when server is unreachable`() = runBlocking {
        val store = FakeOfflineStore().apply {
            mapData = CachedValue(AssignmentMapData(buildingCount = 12), 1234L)
        }
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(mapResult = networkFailure()),
            offlineStore = store,
        )

        val result = repository.loadAssignmentMapData(detail(AssignmentStatus.READY))
            as AssignmentResult.Success

        assertEquals(AssignmentDataSource.LOCAL_CACHE, result.source)
        assertEquals(12, result.value.buildingCount)
        assertEquals(1234L, result.cachedAtEpochMillis)
    }

    @Test
    fun `warming assignment also stores map data`() = runBlocking {
        val store = FakeOfflineStore()
        val repository = DefaultAssignmentRepository(
            remote = FakeRemote(
                mapResult = AssignmentResult.Success(AssignmentMapData(buildingCount = 3)),
            ),
            offlineStore = store,
        )

        repository.warmAssignments(listOf("assignment-1"))

        assertEquals(3, store.mapData?.value?.buildingCount)
    }

    @Test
    fun `shares concurrent paginated map data load`() = runBlocking {
        val remote = FakeRemote(
            mapResult = AssignmentResult.Success(AssignmentMapData(buildingCount = 713)),
            mapDelayMillis = 50,
        )
        val repository = DefaultAssignmentRepository(remote = remote)
        val assignment = detail(AssignmentStatus.READY)

        awaitAll(
            async { repository.loadAssignmentMapData(assignment) },
            async { repository.loadAssignmentMapData(assignment) },
            async { repository.loadAssignmentMapData(assignment) },
        )

        assertEquals(1, remote.mapLoadCalls)
    }

    @Test
    fun `sync stores server snapshot before event is marked synced`() = runBlocking {
        val store = FakeOfflineStore().apply {
            enqueueAssignmentStatusUpdate(
                "assignment-1",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
        }
        val remote = FakeRemote(
            updateResult = AssignmentResult.Success(detail(AssignmentStatus.ACTIVE)),
        )

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.COMPLETE, outcome)
        assertEquals(listOf("store", "synced"), store.operationLog)
        assertEquals(SyncQueueStatus.SYNCED, store.queue.value.single().status)
    }

    @Test
    fun `sync sends queued building status and persists it before completion`() = runBlocking {
        val building = AssignmentMapFeature(
            id = "building-1",
            kind = AssignmentMapFeatureKind.BUILDING,
            geometryGeoJson = "{}",
            status = BuildingStatus.OPEN,
            canUpdate = true,
            serverUpdatedAt = "2026-07-22T08:00:00Z",
        )
        val store = FakeOfflineStore().apply {
            mapData = CachedValue(AssignmentMapData(1, features = listOf(building)), 1L)
            enqueueAssignmentMapFeatureMutation(
                "assignment-1",
                SyncEventKind.ASSIGNMENT_BUILDING_UPDATE,
                building.copy(status = BuildingStatus.DONE, isPendingSync = true),
                building,
            )
        }
        val remote = FakeRemote()

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.COMPLETE, outcome)
        assertEquals("building-1", remote.updatedBuildingId)
        assertEquals(BuildingStatus.DONE, remote.updatedBuildingStatus)
        assertEquals("event-1", remote.updatedBuildingClientEventKey)
        assertEquals("2026-07-22T08:00:00Z", remote.updatedBuildingKnownUpdatedAt)
        assertEquals(BuildingStatus.DONE, store.mapData?.value?.features?.single()?.status)
        assertEquals(listOf("building", "synced"), store.operationLog)
    }

    @Test
    fun `retryable sync failure stays pending for WorkManager backoff`() = runBlocking {
        val store = FakeOfflineStore().apply {
            enqueueAssignmentStatusUpdate(
                "assignment-1",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
        }
        val remote = FakeRemote(updateResult = networkFailure())

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.RETRY, outcome)
        assertEquals(SyncQueueStatus.PENDING, store.queue.value.single().status)
        assertFalse(store.queue.value.single().lastError.isNullOrBlank())
    }

    @Test
    fun `validation failure stays visible and does not request automatic retry`() = runBlocking {
        val store = FakeOfflineStore().apply {
            enqueueAssignmentStatusUpdate(
                "assignment-1",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
        }
        val remote = FakeRemote(
            updateResult = AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.VALIDATION,
                    422,
                    "Statusänderung abgelehnt.",
                ),
            ),
        )

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.COMPLETE, outcome)
        assertEquals(SyncQueueStatus.FAILED, store.queue.value.single().status)
        assertEquals(SyncFailureKind.VALIDATION, store.queue.value.single().failureKind)
    }

    @Test
    fun `authentication failure marks current event and stops queue`() = runBlocking {
        val store = FakeOfflineStore().apply {
            enqueueAssignmentStatusUpdate(
                "assignment-1",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
            enqueueAssignmentStatusUpdate(
                "assignment-2",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
        }
        val remote = FakeRemote(
            updateResult = AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.UNAUTHORIZED,
                    401,
                    "Sitzung abgelaufen.",
                ),
            ),
        )

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.AUTHENTICATION_REQUIRED, outcome)
        assertEquals(SyncQueueStatus.FAILED, store.queue.value[0].status)
        assertEquals(SyncFailureKind.AUTHENTICATION, store.queue.value[0].failureKind)
        assertEquals(SyncQueueStatus.PENDING, store.queue.value[1].status)
        assertEquals(0, store.queue.value[1].attempts)
    }

    @Test
    fun `conflict stays visible without blocking later runs`() = runBlocking {
        val store = FakeOfflineStore().apply {
            enqueueAssignmentStatusUpdate(
                "assignment-1",
                AssignmentStatus.READY,
                AssignmentStatus.ACTIVE,
            )
        }
        val remote = FakeRemote(
            updateResult = AssignmentResult.Failure(
                AssignmentFailure(
                    AssignmentFailureKind.CONFLICT,
                    409,
                    "Konflikt.",
                ),
            ),
        )

        val outcome = AssignmentSyncEngine(remote, store).processPending()

        assertEquals(SyncProcessOutcome.COMPLETE, outcome)
        assertEquals(SyncQueueStatus.FAILED, store.queue.value.single().status)
        assertEquals(SyncFailureKind.CONFLICT, store.queue.value.single().failureKind)
    }

    private fun profile(role: String = "lead") = UserProfile(
        id = "user-1",
        name = "Field User",
        email = "field@example.test",
        teams = listOf(TeamMembership("team-1", "Field Team", role)),
    )

    private fun summary(status: AssignmentStatus = AssignmentStatus.READY) = AssignmentSummary(
        id = "assignment-1",
        title = "Testauftrag",
        type = AssignmentType.STANDARD,
        status = status,
        team = TeamSummary("team-1", "Field Team"),
    )

    private fun detail(status: AssignmentStatus) = AssignmentDetail(
        summary = summary(status),
        permissions = AssignmentPermissions(start = true, pause = true, complete = true),
    )

    private fun <T> networkFailure(): AssignmentResult<T> = AssignmentResult.Failure(
        AssignmentFailure(
            kind = AssignmentFailureKind.NETWORK,
            httpStatus = null,
            userMessage = "Keine Verbindung.",
        ),
    )

    private class RecordingScheduler : SyncScheduler {
        var calls = 0
        override fun schedule(force: Boolean) {
            calls++
        }
    }

    private class FakeRemote(
        private val loadResult: AssignmentResult<AssignmentPage> = AssignmentResult.Success(
            AssignmentPage(emptyList()),
        ),
        private val updateResult: AssignmentResult<AssignmentDetail> = AssignmentResult.Success(
            AssignmentDetail(
                AssignmentSummary(
                    "assignment-1",
                    "Testauftrag",
                    AssignmentType.STANDARD,
                    AssignmentStatus.ACTIVE,
                ),
            ),
        ),
        private val mapResult: AssignmentResult<AssignmentMapData> = AssignmentResult.Success(
            AssignmentMapData(),
        ),
        private val mapDelayMillis: Long = 0,
    ) : AssignmentRemoteDataSource {
        var mapLoadCalls = 0
        var updatedBuildingId: String? = null
        var updatedBuildingStatus: BuildingStatus? = null
        var updatedBuildingClientEventKey: String? = null
        var updatedBuildingKnownUpdatedAt: String? = null
        var createdPosterInput: AssignmentLocationInput? = null
        override suspend fun loadAssignments(
            userId: String?,
            teamIds: List<String>,
        ): AssignmentResult<AssignmentPage> = loadResult

        override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
            updateResult

        override suspend fun loadAssignmentMapData(
            id: String,
            type: AssignmentType,
        ): AssignmentResult<AssignmentMapData> {
            mapLoadCalls++
            if (mapDelayMillis > 0) delay(mapDelayMillis)
            return mapResult
        }

        override suspend fun updateAssignmentStatus(
            id: String,
            status: AssignmentStatus,
        ): AssignmentResult<AssignmentDetail> = updateResult

        override suspend fun updateAssignmentBuildingStatus(
            id: String,
            status: BuildingStatus,
            clientEventKey: String?,
            knownUpdatedAt: String?,
        ): AssignmentResult<AssignmentMapFeature?> {
            updatedBuildingId = id
            updatedBuildingStatus = status
            updatedBuildingClientEventKey = clientEventKey
            updatedBuildingKnownUpdatedAt = knownUpdatedAt
            return AssignmentResult.Success(null)
        }

        override suspend fun updateAssignmentBuilding(
            id: String,
            status: BuildingStatus?,
            notes: String?,
            includeNotes: Boolean,
            clientEventKey: String?,
            knownUpdatedAt: String?,
        ): AssignmentResult<AssignmentMapFeature?> {
            updatedBuildingId = id
            updatedBuildingStatus = status
            updatedBuildingClientEventKey = clientEventKey
            updatedBuildingKnownUpdatedAt = knownUpdatedAt
            return AssignmentResult.Success(null)
        }

        override suspend fun createPosterLocation(
            assignmentId: String,
            input: AssignmentLocationInput,
        ): AssignmentResult<AssignmentMapFeature> {
            createdPosterInput = input
            return AssignmentResult.Success(
                AssignmentMapFeature(
                    id = "poster-server",
                    kind = AssignmentMapFeatureKind.POSTER,
                    geometryGeoJson = input.pointGeoJson(),
                    label = input.label,
                    canUpdate = true,
                ),
            )
        }

    }

    private class FakeOfflineStore : OfflineStore {
        var assignments: CachedValue<AssignmentPage>? = null
        var assignment: CachedValue<AssignmentDetail>? = null
        var mapData: CachedValue<AssignmentMapData>? = null
        val queue = MutableStateFlow<List<SyncQueueItem>>(emptyList())
        val operationLog = mutableListOf<String>()

        override fun observeAssignments(): Flow<CachedValue<AssignmentPage>?> = flowOf(assignments)
        override fun observeAssignment(id: String): Flow<CachedValue<AssignmentDetail>?> =
            flowOf(assignment)
        override fun observeAssignmentMapData(id: String): Flow<CachedValue<AssignmentMapData>?> =
            flowOf(mapData)
        override fun observeOfflineReadyAssignmentIds(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeQueue(): Flow<List<SyncQueueItem>> = queue
        override suspend fun readAssignments(): CachedValue<AssignmentPage>? = assignments
        override suspend fun readAssignment(id: String): CachedValue<AssignmentDetail>? = assignment
        override suspend fun readAssignmentMapData(id: String): CachedValue<AssignmentMapData>? = mapData
        override suspend fun storeAssignments(page: AssignmentPage) {
            assignments = CachedValue(page, 1L)
        }
        override suspend fun storeAssignment(detail: AssignmentDetail) {
            operationLog += "store"
            assignment = CachedValue(detail, 1L)
        }
        override suspend fun storeAssignmentMapData(id: String, mapData: AssignmentMapData) {
            this.mapData = CachedValue(mapData, 1L)
        }

        override suspend fun updateAssignmentBuildingStatus(
            assignmentId: String,
            buildingId: String,
            status: BuildingStatus,
        ) {
            operationLog += "building"
            mapData = mapData?.copy(
                value = mapData!!.value.copy(
                    features = mapData!!.value.features.map { feature ->
                        if (feature.id == buildingId) feature.copy(status = status) else feature
                    },
                ),
            )
        }

        override suspend fun mergeAssignmentMapFeature(
            assignmentId: String,
            previousFeatureId: String?,
            feature: AssignmentMapFeature,
        ) {
            if (feature.kind == AssignmentMapFeatureKind.BUILDING) {
                operationLog += "building"
            }
            val current = mapData?.value ?: AssignmentMapData()
            val features = current.features.filterNot {
                it.id == previousFeatureId || it.id == feature.id
            } + feature
            mapData = CachedValue(
                current.copy(
                    features = features,
                    buildingCount = features.count { it.kind == AssignmentMapFeatureKind.BUILDING },
                    posterCount = features.count { it.kind == AssignmentMapFeatureKind.POSTER },
                    campaignBoothCount = features.count {
                        it.kind == AssignmentMapFeatureKind.CAMPAIGN_BOOTH
                    },
                ),
                1L,
            )
        }

        override suspend fun removeAssignmentMapFeature(
            assignmentId: String,
            featureId: String,
        ) {
            mapData = mapData?.copy(
                value = mapData!!.value.copy(
                    features = mapData!!.value.features.filterNot { it.id == featureId },
                ),
            )
        }

        override suspend fun enqueueAssignmentStatusUpdate(
            assignmentId: String,
            previousStatus: AssignmentStatus,
            targetStatus: AssignmentStatus,
        ): SyncQueueItem {
            val event = SyncQueueItem(
                id = "event-${queue.value.size + 1}",
                assignmentId = assignmentId,
                kind = SyncEventKind.ASSIGNMENT_STATUS_UPDATE,
                targetStatus = targetStatus,
                previousStatus = previousStatus,
                status = SyncQueueStatus.PENDING,
                attempts = 0,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
            queue.value += event
            return event
        }


        override suspend fun enqueueAssignmentBuildingStatusUpdate(
            assignmentId: String,
            buildingId: String,
            previousStatus: BuildingStatus,
            targetStatus: BuildingStatus,
        ): SyncQueueItem {
            val event = SyncQueueItem(
                id = "event-${queue.value.size + 1}",
                assignmentId = assignmentId,
                kind = SyncEventKind.ASSIGNMENT_BUILDING_UPDATE,
                targetStatus = AssignmentStatus.UNKNOWN,
                previousStatus = AssignmentStatus.UNKNOWN,
                buildingId = buildingId,
                targetBuildingStatus = targetStatus,
                previousBuildingStatus = previousStatus,
                status = SyncQueueStatus.PENDING,
                attempts = 0,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
            queue.value += event
            return event
        }

        override suspend fun enqueueAssignmentMapFeatureMutation(
            assignmentId: String,
            kind: SyncEventKind,
            feature: AssignmentMapFeature,
            previousFeature: AssignmentMapFeature?,
        ): SyncQueueItem {
            val event = SyncQueueItem(
                id = "event-${queue.value.size + 1}",
                assignmentId = assignmentId,
                kind = kind,
                targetStatus = AssignmentStatus.UNKNOWN,
                previousStatus = AssignmentStatus.UNKNOWN,
                buildingId = feature.id.takeIf {
                    kind == SyncEventKind.ASSIGNMENT_BUILDING_UPDATE
                },
                subjectId = feature.id,
                payloadJson = Json.encodeToString(feature),
                targetBuildingStatus = feature.status.takeIf {
                    kind == SyncEventKind.ASSIGNMENT_BUILDING_UPDATE
                },
                previousBuildingStatus = previousFeature?.status.takeIf {
                    kind == SyncEventKind.ASSIGNMENT_BUILDING_UPDATE
                },
                status = SyncQueueStatus.PENDING,
                attempts = 0,
                createdAtEpochMillis = 1L,
                updatedAtEpochMillis = 1L,
            )
            queue.value += event
            return event
        }

        override suspend fun readPendingQueue(): List<SyncQueueItem> =
            queue.value.filter { it.status == SyncQueueStatus.PENDING }

        override suspend fun claimQueueEvent(eventId: String): SyncQueueItem? {
            val current = queue.value.firstOrNull { it.id == eventId && it.status == SyncQueueStatus.PENDING }
                ?: return null
            val claimed = current.copy(status = SyncQueueStatus.SYNCING, attempts = current.attempts + 1)
            replace(claimed)
            return claimed
        }

        override suspend fun markQueueEventSynced(eventId: String) {
            operationLog += "synced"
            replace(requireNotNull(queue.value.firstOrNull { it.id == eventId }).copy(status = SyncQueueStatus.SYNCED))
        }

        override suspend fun markQueueEventRetryable(eventId: String, message: String) {
            replace(
                requireNotNull(queue.value.firstOrNull { it.id == eventId }).copy(
                    status = SyncQueueStatus.PENDING,
                    lastError = message,
                ),
            )
        }

        override suspend fun markQueueEventFailed(
            eventId: String,
            failureKind: SyncFailureKind,
            message: String,
        ) {
            replace(
                requireNotNull(queue.value.firstOrNull { it.id == eventId }).copy(
                    status = SyncQueueStatus.FAILED,
                    failureKind = failureKind,
                    lastError = message,
                ),
            )
        }

        override suspend fun retryQueueEvent(eventId: String): Boolean = false
        override suspend fun resetStaleSyncingEvents(): Int = 0
        override suspend fun cleanupSyncedEvents(): Int = 0
        override fun clearBlocking(): Boolean = true

        private fun replace(event: SyncQueueItem) {
            queue.value = queue.value.map { if (it.id == event.id) event else it }
        }
    }
}
