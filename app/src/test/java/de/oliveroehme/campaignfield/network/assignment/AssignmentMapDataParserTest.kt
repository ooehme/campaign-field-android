package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
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
}
