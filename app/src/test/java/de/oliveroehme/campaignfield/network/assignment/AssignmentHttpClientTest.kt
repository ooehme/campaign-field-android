package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.network.ApiConfiguration
import de.oliveroehme.campaignfield.network.auth.PersistentCookieJar
import de.oliveroehme.campaignfield.network.auth.SanctumHttpClient
import de.oliveroehme.campaignfield.network.auth.TestCookiePersistence
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AssignmentHttpClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: AssignmentHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val configuration = ApiConfiguration.forHttpTest(server.url("/api").toString())
        val cookieJar = PersistentCookieJar(configuration.originUrl, TestCookiePersistence())
        client = AssignmentHttpClient(
            configuration,
            SanctumHttpClient.create(configuration, cookieJar, allowCleartextForTests = true),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `loads and merges every team page`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":1,"title":"A","status":"active"}],"current_page":1,"last_page":2,"total":2}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":2,"title":"B","status":"ready"}],"current_page":2,"last_page":2,"total":2}""",
            ),
        )

        val result = client.loadAssignments(userId = "7", teamIds = listOf("3"))

        assertEquals(listOf("1", "2"), (result as AssignmentResult.Success).value.items.map { it.id })
        assertEquals("/api/teams/3/assignments?per_page=100&page=1", server.takeRequest().path)
        assertEquals("/api/teams/3/assignments?per_page=100&page=2", server.takeRequest().path)
    }

    @Test
    fun `falls back from forbidden team route to user route`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403))
        server.enqueue(jsonResponse("""{"data":[{"id":3,"title":"Benutzerauftrag","status":"ready"}]}"""))

        val result = client.loadAssignments(userId = "user/7", teamIds = listOf("north", "south"))

        assertEquals(listOf("3"), (result as AssignmentResult.Success).value.items.map { it.id })
        assertEquals("/api/teams/north/assignments?per_page=100&page=1", server.takeRequest().path)
        assertEquals("/api/users/user%2F7/assignments?per_page=100&page=1", server.takeRequest().path)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `uses user route when no team is known`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":[{"id":5,"title":"Benutzerauftrag","status":"active"}]}"""))

        val result = client.loadAssignments(userId = "7", teamIds = emptyList())

        assertEquals(listOf("5"), (result as AssignmentResult.Success).value.items.map { it.id })
        assertEquals("/api/users/7/assignments?per_page=100&page=1", server.takeRequest().path)
    }

    @Test
    fun `loads detail and normalizes server errors`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"id":9,"title":"Detail","status":"active"}}"""))
        val success = client.loadAssignment("9")
        assertEquals("Detail", (success as AssignmentResult.Success).value.summary.title)
        assertEquals("/api/assignments/9", server.takeRequest().path)

        server.enqueue(MockResponse().setResponseCode(503))
        val failure = client.loadAssignment("9") as AssignmentResult.Failure
        assertEquals(AssignmentFailureKind.SERVER, failure.failure.kind)
        assertTrue(failure.failure.userMessage.contains("vorübergehend"))
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
