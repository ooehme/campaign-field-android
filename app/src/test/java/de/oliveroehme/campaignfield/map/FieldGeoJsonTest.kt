package de.oliveroehme.campaignfield.map

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldGeoJsonTest {
    @Test
    fun `extracts valid positions from feature collections and ignores invalid coordinates`() {
        val positions = FieldGeoJson.positions(
            """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[11.2,51.3]}},
              {"type":"Feature","geometry":{"type":"LineString","coordinates":[[11.4,51.5],[999,51.6]]}}
            ]}
            """.trimIndent(),
        )

        assertEquals(
            listOf(MapCoordinate(51.3, 11.2), MapCoordinate(51.5, 11.4)),
            positions,
        )
    }

    @Test
    fun `rejects non geojson objects`() {
        val element = Json.parseToJsonElement("""{"type":"NotGeoJson","coordinates":[11,51]}""")

        assertNull(FieldGeoJson.normalize(element))
    }

    @Test
    fun `checks polygon boundaries and holes`() {
        val polygon = """
            {"type":"Polygon","coordinates":[
              [[10,50],[12,50],[12,52],[10,52],[10,50]],
              [[10.5,50.5],[11.5,50.5],[11.5,51.5],[10.5,51.5],[10.5,50.5]]
            ]}
        """.trimIndent()

        assertTrue(FieldGeoJson.contains(polygon, MapCoordinate(50.25, 10.25)) == true)
        assertFalse(FieldGeoJson.contains(polygon, MapCoordinate(51.0, 11.0)) == true)
        assertTrue(FieldGeoJson.contains(polygon, MapCoordinate(50.0, 10.0)) == true)
    }
}
