package de.oliveroehme.campaignfield.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import de.oliveroehme.campaignfield.CampaignFieldApplication
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.SyncFailureKind
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailure
import de.oliveroehme.campaignfield.network.assignment.AssignmentFailureKind
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult

class AssignmentSyncEngine(
    private val remote: AssignmentRemoteDataSource,
    private val offlineStore: OfflineStore,
) {
    suspend fun processPending(): SyncProcessOutcome {
        offlineStore.resetStaleSyncingEvents()
        offlineStore.cleanupSyncedEvents()

        for (queuedEvent in offlineStore.readPendingQueue()) {
            val event = offlineStore.claimQueueEvent(queuedEvent.id) ?: continue
            when (val result = syncEvent(event)) {
                is AssignmentResult.Success -> {
                    offlineStore.markQueueEventSynced(event.id)
                }
                is AssignmentResult.Failure -> when (result.failure.kind) {
                    AssignmentFailureKind.NETWORK,
                    AssignmentFailureKind.SERVER,
                    -> {
                        offlineStore.markQueueEventRetryable(event.id, result.failure.userMessage)
                        return SyncProcessOutcome.RETRY
                    }
                    AssignmentFailureKind.UNAUTHORIZED -> return SyncProcessOutcome.AUTHENTICATION_REQUIRED
                    AssignmentFailureKind.FORBIDDEN -> offlineStore.markQueueEventFailed(
                        event.id,
                        SyncFailureKind.PERMISSION,
                        result.failure.userMessage,
                    )
                    AssignmentFailureKind.VALIDATION -> offlineStore.markQueueEventFailed(
                        event.id,
                        SyncFailureKind.VALIDATION,
                        result.failure.userMessage,
                    )
                    AssignmentFailureKind.CONFLICT -> offlineStore.markQueueEventFailed(
                        event.id,
                        SyncFailureKind.CONFLICT,
                        result.failure.userMessage,
                    )
                    AssignmentFailureKind.INVALID_RESPONSE -> offlineStore.markQueueEventFailed(
                        event.id,
                        SyncFailureKind.INVALID_DATA,
                        result.failure.userMessage,
                    )
                    AssignmentFailureKind.NOT_FOUND,
                    AssignmentFailureKind.UNEXPECTED,
                    -> offlineStore.markQueueEventFailed(
                        event.id,
                        SyncFailureKind.UNKNOWN,
                        result.failure.userMessage,
                    )
                }
            }
        }

        offlineStore.cleanupSyncedEvents()
        return SyncProcessOutcome.COMPLETE
    }

    private suspend fun syncEvent(event: SyncQueueItem): AssignmentResult<Unit> = when (event.kind) {
        SyncEventKind.ASSIGNMENT_STATUS_UPDATE -> when (
            val result = remote.updateAssignmentStatus(event.assignmentId, event.targetStatus)
        ) {
            is AssignmentResult.Success -> {
                offlineStore.storeAssignment(result.value)
                AssignmentResult.Success(Unit)
            }
            is AssignmentResult.Failure -> result
        }
        SyncEventKind.ASSIGNMENT_BUILDING_UPDATE -> {
            val buildingId = event.buildingId
            val targetStatus = event.targetBuildingStatus
            if (buildingId == null || targetStatus == null || targetStatus == BuildingStatus.UNKNOWN) {
                AssignmentResult.Failure(
                    AssignmentFailure(
                        AssignmentFailureKind.INVALID_RESPONSE,
                        null,
                        "Die lokale Gebäudeänderung ist unvollständig.",
                    ),
                )
            } else {
                when (
                    val result = remote.updateAssignmentBuildingStatus(
                        buildingId,
                        targetStatus,
                        event.id,
                    )
                ) {
                    is AssignmentResult.Success -> {
                        offlineStore.updateAssignmentBuildingStatus(
                            event.assignmentId,
                            buildingId,
                            targetStatus,
                        )
                        AssignmentResult.Success(Unit)
                    }
                    is AssignmentResult.Failure -> result
                }
            }
        }
    }
}

enum class SyncProcessOutcome {
    COMPLETE,
    RETRY,
    AUTHENTICATION_REQUIRED,
}

class AssignmentSyncWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val application = applicationContext as? CampaignFieldApplication
            ?: return Result.failure()
        return when (application.appContainer.assignmentSyncEngine.processPending()) {
            SyncProcessOutcome.COMPLETE -> Result.success()
            SyncProcessOutcome.RETRY -> Result.retry()
            SyncProcessOutcome.AUTHENTICATION_REQUIRED -> Result.failure()
        }
    }
}
