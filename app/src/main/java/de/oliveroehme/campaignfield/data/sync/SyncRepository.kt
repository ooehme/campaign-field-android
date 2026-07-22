package de.oliveroehme.campaignfield.data.sync

import de.oliveroehme.campaignfield.data.Repository
import de.oliveroehme.campaignfield.database.OfflineStore
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow

class SyncRepository(
    private val offlineStore: OfflineStore,
    private val scheduler: SyncScheduler,
) : Repository {
    val events: Flow<List<SyncQueueItem>> = offlineStore.observeQueue()

    fun synchronize() {
        scheduler.schedule(force = true)
    }

    suspend fun retry(eventId: String): Boolean {
        val didRetry = offlineStore.retryQueueEvent(eventId)
        if (didRetry) scheduler.schedule(force = true)
        return didRetry
    }
}
