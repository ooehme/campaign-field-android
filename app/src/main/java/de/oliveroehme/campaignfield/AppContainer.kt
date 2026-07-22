package de.oliveroehme.campaignfield

import android.content.Context
import de.oliveroehme.campaignfield.data.assignment.AssignmentRepository
import de.oliveroehme.campaignfield.data.assignment.DefaultAssignmentRepository
import de.oliveroehme.campaignfield.data.auth.AndroidLocalSessionCleaner
import de.oliveroehme.campaignfield.data.auth.AndroidUserProfileStore
import de.oliveroehme.campaignfield.data.auth.SessionRepository
import de.oliveroehme.campaignfield.data.auth.UnauthorizedSessionHandler
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.network.ApiConfiguration
import de.oliveroehme.campaignfield.network.auth.AndroidEncryptedCookiePersistence
import de.oliveroehme.campaignfield.network.auth.PersistentCookieJar
import de.oliveroehme.campaignfield.network.auth.SanctumHttpClient
import de.oliveroehme.campaignfield.network.auth.SanctumSessionClient
import de.oliveroehme.campaignfield.network.assignment.AssignmentHttpClient

class AppContainer(context: Context) {
    val sessionRepository: SessionRepository
    val assignmentRepository: AssignmentRepository

    init {
        val applicationContext = context.applicationContext
        val configuration = ApiConfiguration.fromBuildConfig()
        val cookieJar = PersistentCookieJar(
            configuration.originUrl,
            AndroidEncryptedCookiePersistence(applicationContext),
        )
        val profileStore = AndroidUserProfileStore(applicationContext)
        val cleaner = AndroidLocalSessionCleaner(
            context = applicationContext,
            cookieJar = cookieJar,
            profileStore = profileStore,
            locationState = InMemoryLocationSessionState(),
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
        assignmentRepository = DefaultAssignmentRepository(
            AssignmentHttpClient(configuration, httpClient),
        )
    }
}
