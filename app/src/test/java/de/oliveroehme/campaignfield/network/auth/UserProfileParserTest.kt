package de.oliveroehme.campaignfield.network.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileParserTest {
    private val parser = UserProfileParser()

    @Test
    fun `parses wrapped profile roles teams and strict permissions`() {
        val profile = parser.parse(
            """
            {
              "data": {
                "id": 42,
                "name": "Erika Feld",
                "email": "erika@example.test",
                "roles": [{"name":"field_agent"}],
                "display_role": "Außendienst",
                "can": {"view_assignments": true},
                "memberships": [
                  {"team":{"id":"7","name":"Nord"},"pivot":{"role":"Mitglied"}}
                ]
              }
            }
            """.trimIndent(),
        )

        assertEquals("42", profile.id)
        assertEquals(listOf("field_agent", "Außendienst"), profile.roles)
        assertTrue(profile.permissions.viewAssignments)
        assertEquals("Nord", profile.teams.single().teamName)
        assertEquals(listOf("Mitglied"), profile.teams.single().roles)
    }

    @Test
    fun `missing or non boolean can flag is false`() {
        val missing = parser.parse("""{"id":1,"name":"A"}""")
        val stringValue = parser.parse(
            """{"id":1,"name":"A","can":{"view_assignments":"true"}}""",
        )

        assertFalse(missing.permissions.viewAssignments)
        assertFalse(stringValue.permissions.viewAssignments)
    }

    @Test
    fun `rejects payload without recognizable profile`() {
        assertTrue(runCatching { parser.parse("{\"data\":{}}") }.isFailure)
    }
}
