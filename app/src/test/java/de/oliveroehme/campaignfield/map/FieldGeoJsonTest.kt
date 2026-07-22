package de.oliveroehme.campaignfield.map

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
