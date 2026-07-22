package de.oliveroehme.campaignfield.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import de.oliveroehme.campaignfield.data.auth.AndroidLocalSessionCleaner
import java.util.concurrent.TimeUnit

interface SyncScheduler {
    fun schedule(force: Boolean = false)
}

object NoOpSyncScheduler : SyncScheduler {
    override fun schedule(force: Boolean) = Unit
}

class WorkManagerSyncScheduler(context: Context) : SyncScheduler {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    override fun schedule(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<AssignmentSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(AndroidLocalSessionCleaner.SESSION_WORK_TAG)
            .addTag(SYNC_WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(
            SYNC_WORK_NAME,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val SYNC_WORK_NAME = "campaign-field-assignment-sync"
        const val SYNC_WORK_TAG = "campaign-field-sync"
    }
}
