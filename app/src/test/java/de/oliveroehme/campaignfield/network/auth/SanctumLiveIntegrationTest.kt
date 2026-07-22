package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.network.ApiConfiguration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class SanctumLiveIntegrationTest {
    @Test
    fun `safe test instance supports native cookie session`() = runBlocking {
        val baseUrl = System.getenv("CAMPAIGN_FIELD_TEST_API_BASE_URL")
        val clientOrigin = System.getenv("CAMPAIGN_FIELD_TEST_SANCTUM_CLIENT_ORIGIN")
        val email = System.getenv("CAMPAIGN_FIELD_TEST_EMAIL")
        val password = System.getenv("CAMPAIGN_FIELD_TEST_PASSWORD")
        assumeTrue(
            "Live-Spike benötigt API-URL, Sanctum-Client-Origin, E-Mail und Passwort.",
            !baseUrl.isNullOrBlank() &&
                !clientOrigin.isNullOrBlank() &&
                !email.isNullOrBlank() &&
                !password.isNullOrBlank(),
        )

        val configuration = ApiConfiguration.from(checkNotNull(baseUrl), checkNotNull(clientOrigin))
        val persistence = TestCookiePersistence()
        var cookieJar = PersistentCookieJar(configuration.originUrl, persistence)
        var client = SanctumSessionClient(
            configuration,
            SanctumHttpClient.create(configuration, cookieJar),
            cookieJar,
        )

        assertTrue(client.signIn(checkNotNull(email), checkNotNull(password)) is SessionResult.Authenticated)

        cookieJar = PersistentCookieJar(configuration.originUrl, persistence)
        client = SanctumSessionClient(
            configuration,
            SanctumHttpClient.create(configuration, cookieJar),
            cookieJar,
        )
        assertTrue(client.checkSession() is SessionResult.Authenticated)
        assertEquals(SessionResult.LoggedOut, client.logout())
        assertEquals(null, persistence.value)
        assertEquals(
            SessionResult.Failure(SessionErrorNormalizer.from(SessionStage.USER, 401)),
            client.checkSession(),
        )
    }
}
