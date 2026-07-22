package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.domain.BuildingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentMapDataParserTest {
    private val parser = AssignmentMapDataParser()

    @Test
    fun `parses paginated building geometry and total`() {
        val page = parser.parseBuildings(
            """
            {"data":[
              {"id":4,"building":{"id":40,"geometry":{"type":"Polygon","coordinates":[[[12,50],[13,50],[13,51],[12,50]]]}}},
              {"id":5,"building":{"id":41}}
            ],"meta":{"current_page":1,"last_page":8,"per_page":100,"total":713}}
            """.trimIndent(),
        )

        assertEquals(1, page.currentPage)
        assertEquals(8, page.lastPage)
        assertEquals(713, page.total)
        assertEquals(1, page.features.size)
        assertEquals(AssignmentMapFeatureKind.BUILDING, page.features.single().kind)
        assertTrue(page.features.single().geometryGeoJson.contains("Polygon"))
    }

    @Test
    fun `creates poster point from coordinate aliases`() {
        val page = parser.parsePosters(
            """[{"id":"9","latitude":"50.8","longitude":"12.9"}]""",
        )

        assertEquals(1, page.total)
        assertEquals(AssignmentMapFeatureKind.POSTER, page.features.single().kind)
        assertTrue(page.features.single().geometryGeoJson.contains("12.9"))
    }

    @Test
    fun `reads geometry from canonical area building relation`() {
        val page = parser.parseBuildings(
            """
            {"data":[{
              "id":17,
              "area_building_id":40,
              "area_building":{"id":40,"geometry":{"type":"Polygon","coordinates":[[[12,50],[13,50],[13,51],[12,50]]]}}
            }],"current_page":1,"last_page":1,"per_page":100,"total":1}
            """.trimIndent(),
        )

        assertEquals(1, page.features.size)
        assertEquals("17", page.features.single().id)
        assertTrue(page.features.single().geometryGeoJson.contains("Polygon"))
    }

    @Test
    fun `parses building status and update permission`() {
        val page = parser.parseBuildings(
            """{"data":[{"id":17,"status":"blocked","updated_at":"2026-07-22T08:00:00Z","can":{"update":true},"geometry":{"type":"Point","coordinates":[12,50]}}]}""",
        )

        assertEquals(BuildingStatus.BLOCKED, page.features.single().status)
        assertEquals("2026-07-22T08:00:00Z", page.features.single().serverUpdatedAt)
        assertTrue(page.features.single().canUpdate)
    }

    @Test
    fun `normalizes building address notes and delete permission`() {
        val page = parser.parseBuildings(
            """{"data":[{"id":17,"notes":"Bitte klingeln","can":{"delete":true},"area_building":{"address":{"street":"Markt","housenumber":"4","postcode":"06108","city":"Halle"},"geometry":{"type":"Point","coordinates":[12,50]}}}]}""",
        )

        val building = page.features.single()
        assertEquals("Markt 4, 06108 Halle", building.label)
        assertEquals("Bitte klingeln", building.note)
        assertTrue(building.canDelete)
    }

    @Test
    fun `parses poster and campaign booth permissions`() {
        val poster = parser.parsePoster(
            """{"data":{"id":9,"label":"Laterne","status":"planned","note":"Nordseite","latitude":50.8,"longitude":12.9,"can":{"update":true,"delete":true}}}""",
        )
        val booth = parser.parseCampaignBooth(
            """{"id":10,"label":"Infostand","geometry":{"type":"Point","coordinates":[12.8,50.7]},"can":{"update":true}}""",
        )

        assertEquals("planned", poster.resourceStatus)
        assertEquals("Nordseite", poster.note)
        assertTrue(poster.canDelete)
        assertEquals(AssignmentMapFeatureKind.CAMPAIGN_BOOTH, booth.kind)
        assertTrue(booth.canUpdate)
    }
}
