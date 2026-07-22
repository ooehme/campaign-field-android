package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.database.CachedValue
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentPermissions
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.domain.SyncFailureKind
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncQueueStatus
import de.oliveroehme.campaignfield.domain.auth.UserProfile
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
import kotlinx.coroutines.runBlocking
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

        val result = repository.changeStatus(detail, AssignmentStatus.ACTIVE)
            as AssignmentResult.Success

        assertTrue(result.value.queued)
        assertEquals(AssignmentStatus.ACTIVE, result.value.assignment.summary.status)
        assertEquals(AssignmentStatus.ACTIVE, store.queue.value.single().targetStatus)
        assertEquals(1, scheduler.calls)
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

    private fun profile() = UserProfile(
        id = "user-1",
        name = "Field User",
        email = "field@example.test",
    )

    private fun summary(status: AssignmentStatus = AssignmentStatus.READY) = AssignmentSummary(
        id = "assignment-1",
        title = "Testauftrag",
        type = AssignmentType.STANDARD,
        status = status,
    )

    private fun detail(status: AssignmentStatus) = AssignmentDetail(
        summary = summary(status),
        permissions = AssignmentPermissions(start = true, pause = true),
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
    ) : AssignmentRemoteDataSource {
        override suspend fun loadAssignments(
            userId: String?,
            teamIds: List<String>,
        ): AssignmentResult<AssignmentPage> = loadResult

        override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
            updateResult

        override suspend fun updateAssignmentStatus(
            id: String,
            status: AssignmentStatus,
        ): AssignmentResult<AssignmentDetail> = updateResult
    }

    private class FakeOfflineStore : OfflineStore {
        var assignments: CachedValue<AssignmentPage>? = null
        var assignment: CachedValue<AssignmentDetail>? = null
        val queue = MutableStateFlow<List<SyncQueueItem>>(emptyList())
        val operationLog = mutableListOf<String>()

        override fun observeAssignments(): Flow<CachedValue<AssignmentPage>?> = flowOf(assignments)
        override fun observeAssignment(id: String): Flow<CachedValue<AssignmentDetail>?> =
            flowOf(assignment)
        override fun observeOfflineReadyAssignmentIds(): Flow<Set<String>> = flowOf(emptySet())
        override fun observeQueue(): Flow<List<SyncQueueItem>> = queue
        override suspend fun readAssignments(): CachedValue<AssignmentPage>? = assignments
        override suspend fun readAssignment(id: String): CachedValue<AssignmentDetail>? = assignment
        override suspend fun storeAssignments(page: AssignmentPage) {
            assignments = CachedValue(page, 1L)
        }
        override suspend fun storeAssignment(detail: AssignmentDetail) {
            operationLog += "store"
            assignment = CachedValue(detail, 1L)
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
