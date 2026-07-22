package de.oliveroehme.campaignfield.data.auth

import android.content.Context
import androidx.work.WorkManager
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.network.auth.PersistentCookieJar
import java.io.File

fun interface LocalSessionCleaner {
    /** Returns false if at least one local store could not be removed. */
    fun clear(): Boolean
}

internal class AndroidLocalSessionCleaner(
    context: Context,
    private val cookieJar: PersistentCookieJar,
    private val profileStore: UserProfileStore,
    private val locationState: InMemoryLocationSessionState,
) : LocalSessionCleaner {
    private val applicationContext = context.applicationContext

    @Synchronized
    override fun clear(): Boolean {
        var successful = true
        fun attempt(action: () -> Boolean) {
            successful = runCatching(action).getOrDefault(false) && successful
        }

        attempt {
            cookieJar.clear()
            true
        }
        attempt(profileStore::clear)
        attempt {
            locationState.clear()
            true
        }
        attempt {
            WorkManager.getInstance(applicationContext).cancelAllWorkByTag(SESSION_WORK_TAG)
            true
        }
        DATABASE_NAMES.forEach { databaseName ->
            val databaseFile = applicationContext.getDatabasePath(databaseName)
            if (databaseFile.exists()) attempt { applicationContext.deleteDatabase(databaseName) }
        }
        SESSION_DIRECTORIES.forEach { directoryName ->
            val directory = File(applicationContext.filesDir, directoryName)
            if (directory.exists()) attempt(directory::deleteRecursively)
        }
        applicationContext.cacheDir.listFiles().orEmpty().forEach { entry ->
            attempt(entry::deleteRecursively)
        }
        return successful
    }

    companion object {
        const val SESSION_WORK_TAG = "campaign-field-session"
        private val DATABASE_NAMES = listOf("campaign-field.db")
        private val SESSION_DIRECTORIES = listOf("offline", "photos", "proofs", "queue")
    }
}

class UnauthorizedSessionHandler(
    private val cleaner: LocalSessionCleaner,
) {
    @Volatile
    private var listener: ((Boolean) -> Unit)? = null

    fun attach(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    fun handle() {
        val cleanupSucceeded = cleaner.clear()
        listener?.invoke(cleanupSucceeded)
    }
}
