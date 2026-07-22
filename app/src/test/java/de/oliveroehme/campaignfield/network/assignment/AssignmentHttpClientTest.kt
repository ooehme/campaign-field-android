package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.AssignmentLocationInput
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

        val assignments = (result as AssignmentResult.Success).value.items
        assertEquals(listOf("1", "2"), assignments.map { it.id })
        assertTrue(assignments.all { it.team?.id == "3" })
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

    @Test
    fun `patches assignment status and parses authoritative response`() = runBlocking {
        server.enqueue(jsonResponse("""{"data":{"id":9,"title":"Detail","status":"paused"}}"""))

        val result = client.updateAssignmentStatus("9", AssignmentStatus.PAUSED)

        assertEquals(AssignmentStatus.PAUSED, (result as AssignmentResult.Success).value.summary.status)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/assignments/9", request.path)
        assertEquals("""{"status":"paused"}""", request.body.readUtf8())
    }

    @Test
    fun `patches assignment building status`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))

        val result = client.updateAssignmentBuildingStatus(
            "17",
            BuildingStatus.UNREACHABLE,
            "queue-1",
            "2026-07-22T08:00:00Z",
        )

        assertTrue(result is AssignmentResult.Success)
        val request = server.takeRequest()
        assertEquals("PATCH", request.method)
        assertEquals("/api/assignment-buildings/17", request.path)
        assertEquals(
            """{"status":"unreachable","client_event_key":"queue-1","updated_at":"2026-07-22T08:00:00Z"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `reloads assignment after status patch without response body`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(jsonResponse("""{"data":{"id":9,"title":"Detail","status":"active"}}"""))

        val result = client.updateAssignmentStatus("9", AssignmentStatus.ACTIVE)

        assertEquals(AssignmentStatus.ACTIVE, (result as AssignmentResult.Success).value.summary.status)
        assertEquals("PATCH", server.takeRequest().method)
        assertEquals("/api/assignments/9", server.takeRequest().path)
    }

    @Test
    fun `loads all building pages for letterbox map`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":1,"geometry":{"type":"Point","coordinates":[12,50]}}],"current_page":1,"last_page":2,"total":101}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":2,"geometry":{"type":"Point","coordinates":[13,51]}}],"current_page":2,"last_page":2,"total":101}""",
            ),
        )

        val result = client.loadAssignmentMapData("8", AssignmentType.LETTERBOX_DISTRIBUTION)

        val data = (result as AssignmentResult.Success).value
        assertEquals(101, data.buildingCount)
        assertEquals(2, data.features.size)
        assertEquals("/api/assignments/8/buildings?per_page=100&page=1", server.takeRequest().path)
        assertEquals("/api/assignments/8/buildings?per_page=100&page=2", server.takeRequest().path)
    }

    @Test
    fun `creates poster location with reference payload`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":{"id":21,"label":"Mast","latitude":50.8,"longitude":12.9,"can":{"update":true}}}""",
            ),
        )

        val result = client.createPosterLocation(
            "8",
            AssignmentLocationInput(50.8, 12.9, label = "Mast", note = "Nordseite"),
        )

        assertEquals("21", (result as AssignmentResult.Success).value.id)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/assignments/8/poster-locations", request.path)
        assertEquals(
            """{"lat":50.8,"lng":12.9,"label":"Mast","notes":"Nordseite"}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `reads authoritative building version after update`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":{"id":17,"status":"done","updated_at":"2026-07-22T08:01:00Z","geometry":{"type":"Point","coordinates":[12,50]}}}""",
            ),
        )

        val result = client.updateAssignmentBuildingStatus(
            id = "17",
            status = BuildingStatus.DONE,
            clientEventKey = "queue-2",
            knownUpdatedAt = "2026-07-22T08:00:00Z",
        ) as AssignmentResult.Success

        assertEquals(BuildingStatus.DONE, result.value?.status)
        assertEquals("2026-07-22T08:01:00Z", result.value?.serverUpdatedAt)
    }

    @Test
    fun `treats missing campaign booth as empty map data`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        val result = client.loadAssignmentMapData("8", AssignmentType.CAMPAIGN_BOOTH)

        val data = (result as AssignmentResult.Success).value
        assertEquals(0, data.campaignBoothCount)
        assertTrue(data.features.isEmpty())
        assertEquals("/api/assignments/8/campaign-booth-location", server.takeRequest().path)
    }

    @Test
    fun `loads missing target area geometry from canonical area route`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":{"id":9,"title":"Detail","status":"active","target_area_id":4,"campaign_id":2}}""",
            ),
        )
        server.enqueue(
            jsonResponse(
                """{"data":{"id":4,"name":"Altstadt","geometry":{"type":"Polygon","coordinates":[[[11.1,51.1],[11.2,51.1],[11.1,51.1]]]}}}""",
            ),
        )

        val result = client.loadAssignment("9") as AssignmentResult.Success

        assertTrue(result.value.summary.area?.geoJson?.contains("Polygon") == true)
        assertEquals("/api/assignments/9", server.takeRequest().path)
        assertEquals("/api/areas/4", server.takeRequest().path)
    }

    @Test
    fun `uses campaign areas when direct area has no geometry`() = runBlocking {
        server.enqueue(
            jsonResponse(
                """{"data":{"id":9,"title":"Detail","status":"active","target_area_id":4,"campaign_id":2}}""",
            ),
        )
        server.enqueue(jsonResponse("""{"data":{"id":4,"name":"Altstadt"}}"""))
        server.enqueue(
            jsonResponse(
                """{"data":[{"id":4,"geometry_json":{"type":"Point","coordinates":[11.2,51.2]}}]}""",
            ),
        )

        val result = client.loadAssignment("9") as AssignmentResult.Success

        assertTrue(result.value.summary.area?.geoJson?.contains("Point") == true)
        server.takeRequest()
        server.takeRequest()
        assertEquals("/api/campaigns/2/areas?per_page=100", server.takeRequest().path)
    }

    private fun jsonResponse(body: String): MockResponse = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body)
}
