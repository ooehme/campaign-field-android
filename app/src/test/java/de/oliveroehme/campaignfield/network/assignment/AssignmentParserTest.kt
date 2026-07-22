package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentType
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
                "area": {"id": 9, "label": "Altstadt"}
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
              "briefing": "Nur freigegebene Flächen nutzen.",
              "type_config": {
                "instructions": "Route von Nord nach Süd",
                "materials": ["Plakate", "Kabelbinder"],
                "geometry": {"type": "Polygon"}
              },
              "can": {"update": true, "start": "true", "create_proof": false}
            }}
            """.trimIndent(),
        )

        assertEquals("Nur freigegebene Flächen nutzen.", detail.description)
        assertEquals(listOf("Anweisungen", "Material"), detail.instructions.map { it.label })
        assertTrue(detail.permissions.update)
        assertFalse(detail.permissions.start)
        assertFalse(detail.permissions.createProof)
        assertFalse(detail.permissions.managePosterLocations)
    }

    @Test
    fun `accepts direct arrays and maps unknown values safely`() {
        val page = parser.parsePage(
            """[{"id":1,"title":"Sonderfall","type":"future_type","status":"future_status"}]""",
        )

        assertEquals(AssignmentType.UNKNOWN, page.items.single().type)
        assertEquals(AssignmentStatus.UNKNOWN, page.items.single().status)
    }
}
