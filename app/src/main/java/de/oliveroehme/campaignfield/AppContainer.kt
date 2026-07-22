package de.oliveroehme.campaignfield

import android.content.Context
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.data.assignment.DefaultAssignmentRepository
import de.oliveroehme.campaignfield.data.auth.AndroidLocalSessionCleaner
import de.oliveroehme.campaignfield.data.auth.AndroidUserProfileStore
import de.oliveroehme.campaignfield.data.auth.SessionRepository
import de.oliveroehme.campaignfield.data.auth.UnauthorizedSessionHandler
import de.oliveroehme.campaignfield.data.sync.SyncRepository
import de.oliveroehme.campaignfield.database.CampaignFieldDatabase
import de.oliveroehme.campaignfield.database.RoomOfflineStore
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.location.AndroidCurrentLocationRequester
import de.oliveroehme.campaignfield.network.ApiConfiguration
import de.oliveroehme.campaignfield.network.CoreApiHealthClient
import de.oliveroehme.campaignfield.network.CoreApiHealthSource
import de.oliveroehme.campaignfield.network.AndroidNetworkStateProvider
import de.oliveroehme.campaignfield.network.auth.AndroidEncryptedCookiePersistence
import de.oliveroehme.campaignfield.network.auth.PersistentCookieJar
import de.oliveroehme.campaignfield.network.auth.SanctumHttpClient
import de.oliveroehme.campaignfield.network.auth.SanctumSessionClient
import de.oliveroehme.campaignfield.network.assignment.AssignmentHttpClient
import de.oliveroehme.campaignfield.sync.AssignmentSyncEngine
import de.oliveroehme.campaignfield.sync.WorkManagerSyncScheduler

class AppContainer(context: Context) {
    val sessionRepository: SessionRepository
    val assignmentRepository: AssignmentRepository
    val syncRepository: SyncRepository
    val assignmentSyncEngine: AssignmentSyncEngine
    val coreApiHealthSource: CoreApiHealthSource
    val locationSessionState: InMemoryLocationSessionState
    val currentLocationRequester: AndroidCurrentLocationRequester

    init {
        val applicationContext = context.applicationContext
        val configuration = ApiConfiguration.fromBuildConfig()
        val cookieJar = PersistentCookieJar(
            configuration.originUrl,
            AndroidEncryptedCookiePersistence(applicationContext),
        )
        val profileStore = AndroidUserProfileStore(applicationContext)
        val database = CampaignFieldDatabase.create(applicationContext)
        val offlineStore = RoomOfflineStore(database)
        val syncScheduler = WorkManagerSyncScheduler(applicationContext)
        locationSessionState = InMemoryLocationSessionState()
        currentLocationRequester = AndroidCurrentLocationRequester(applicationContext)
        val cleaner = AndroidLocalSessionCleaner(
            context = applicationContext,
            cookieJar = cookieJar,
            profileStore = profileStore,
            locationState = locationSessionState,
            clearOfflineData = offlineStore::clearBlocking,
        )
        val unauthorizedHandler = UnauthorizedSessionHandler(cleaner)
        val httpClient = SanctumHttpClient.create(
            configuration = configuration,
            cookieJar = cookieJar,
            onUnauthorized = unauthorizedHandler::handle,
        )
        sessionRepository = SessionRepository(
            remote = SanctumSessionClient(configuration, httpClient, cookieJar),
            profileStore = profileStore,
            cleaner = cleaner,
            unauthorizedHandler = unauthorizedHandler,
        )
        val assignmentRemote = AssignmentHttpClient(configuration, httpClient)
        assignmentSyncEngine = AssignmentSyncEngine(assignmentRemote, offlineStore)
        syncRepository = SyncRepository(offlineStore, syncScheduler)
        assignmentRepository = DefaultAssignmentRepository(
            remote = assignmentRemote,
            offlineStore = offlineStore,
            syncScheduler = syncScheduler,
            networkStateProvider = AndroidNetworkStateProvider(applicationContext),
        )
        coreApiHealthSource = CoreApiHealthClient(configuration, httpClient)
    }
}
