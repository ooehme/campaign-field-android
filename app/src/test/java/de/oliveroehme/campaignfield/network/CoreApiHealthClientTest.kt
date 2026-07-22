package de.oliveroehme.campaignfield.network

import de.oliveroehme.campaignfield.network.auth.PersistentCookieJar
import de.oliveroehme.campaignfield.network.auth.SanctumHttpClient
import de.oliveroehme.campaignfield.network.auth.TestCookiePersistence
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CoreApiHealthClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: CoreApiHealthClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val configuration = ApiConfiguration.forHttpTest(server.url("/api").toString())
        val cookieJar = PersistentCookieJar(configuration.originUrl, TestCookiePersistence())
        client = CoreApiHealthClient(
            configuration,
            SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `reports reachable only for successful health response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(CoreApiStatus.Reachable, client.check())
        assertEquals(CoreApiStatus.Unreachable, client.check())
        assertEquals("/api/health", server.takeRequest().path)
        assertEquals("/api/health", server.takeRequest().path)
    }
}
