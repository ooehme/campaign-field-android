package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.availableStatusActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentParserTest {
    private val parser = AssignmentParser()

    @Test
    fun `parses laravel pagination and assignment relations`() {
        val page = parser.parsePage(
            """
            {
              "data": [{
                "id": 41,
                "title": "Flyer Nord",
                "type": "letterbox_distribution",
                "status": "active",
                "due_at": "2026-08-03",
                "campaign": {"id": 5, "name": "Sommer"},
                "team": {"id": 8, "name": "Team Nord"},
                "area": {"id": 9, "label": "Altstadt"},
                "can": {"pause": true, "complete": true, "cancel": true}
              }],
              "meta": {"current_page": 2, "last_page": 3, "per_page": 100, "total": 201}
            }
            """.trimIndent(),
        )

        assertEquals(2, page.currentPage)
        assertEquals(3, page.lastPage)
        assertEquals(201, page.total)
        with(page.items.single()) {
            assertEquals("41", id)
            assertEquals(AssignmentType.LETTERBOX_DISTRIBUTION, type)
            assertEquals(AssignmentStatus.ACTIVE, status)
            assertEquals("Sommer", campaign?.name)
            assertEquals("8", team?.id)
            assertEquals("Altstadt", area?.name)
            assertTrue(permissions.pause)
            assertTrue(permissions.complete)
            assertTrue(permissions.cancel)
        }
    }

    @Test
    fun `parses detail instructions and only strict can flags`() {
        val detail = parser.parseDetail(
            """
            {"data": {
              "id": "a-7",
                "name": "Plakatierung",
                "type": "poster_guided",
                "status": "ready",
                "description": "Nur freigegebene Flächen nutzen.",
                "type_config": {
                "instructions": [
                  "Route von Nord nach Süd",
                  {"text": "Nur markierte Flächen"}
                ],
                "materials": ["Plakate", "Kabelbinder"]
                },
              "can": {"update": true, "start": "true", "create_proof": false}
            }}
            """.trimIndent(),
        )

        assertEquals("Nur freigegebene Flächen nutzen.", detail.description)
        assertEquals(
            listOf("Route von Nord nach Süd", "Nur markierte Flächen"),
            detail.instructions.map { it.value },
        )
        assertTrue(detail.permissions.update)
        assertFalse(detail.permissions.start)
        assertFalse(detail.permissions.createProof)
        assertFalse(detail.permissions.managePosterLocations)
        assertEquals(
            listOf(AssignmentStatus.ACTIVE, AssignmentStatus.CANCELLED),
            detail.availableStatusActions().map { it.targetStatus },
        )
    }

    @Test
    fun `accepts direct arrays and maps unknown values safely`() {
        val page = parser.parsePage(
            """[{"id":1,"title":"Sonderfall","type":"future_type","status":"future_status"}]""",
        )

        assertEquals(AssignmentType.UNKNOWN, page.items.single().type)
        assertEquals(AssignmentStatus.UNKNOWN, page.items.single().status)
    }

    @Test
    fun `normalizes reference area geometry aliases and center coordinates`() {
        val detail = parser.parseDetail(
            """
            {"data":{"id":12,"title":"Nord","status":"active","target_area":{
              "id":4,"label":"Altstadt",
              "geo_json":"{\"type\":\"Polygon\",\"coordinates\":[[[11.1,51.1],[11.2,51.1],[11.1,51.1]]]}",
              "center_latitude":"51,15","center_longitude":11.15
            }}}
            """.trimIndent(),
        )

        val area = requireNotNull(detail.summary.area)
        assertEquals("Altstadt", area.name)
        assertEquals(51.15, area.centerLatitude!!, 0.0)
        assertEquals(11.15, area.centerLongitude!!, 0.0)
        assertTrue(area.geoJson!!.startsWith("{\"type\":\"Polygon\""))
    }
}
