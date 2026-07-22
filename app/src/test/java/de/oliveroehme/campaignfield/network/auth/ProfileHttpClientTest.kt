package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.network.ApiConfiguration
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ProfileHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ProfileHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val configuration = ApiConfiguration.forHttpTest(server.url("/api").toString())
        val cookieJar = PersistentCookieJar(configuration.originUrl, TestCookiePersistence())
        client = ProfileHttpClient(
            configuration,
            SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loads team details and invitations from reference routes`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"id":7,"name":"Nord","users":[]}}"""))
        server.enqueue(jsonResponse("""{"data":[{"id":3,"team":{"id":7,"name":"Nord"}}]}"""))

        val team = client.loadTeam("7") as ProfileResult.Success
        val invitations = client.loadInvitations() as ProfileResult.Success

        assertEquals("Nord", team.value.name)
        assertEquals("Nord", invitations.value.single().teamName)
        assertEquals("/api/teams/7", server.takeRequest().path)
        assertEquals("/api/user/invitations", server.takeRequest().path)
    }

    @Test
    fun `posts invitation response`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        client.acceptInvitation("3")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/team-invitations/3/accept", request.path)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
