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
        assertEquals("field_agent", profile.appRole)
        assertTrue(profile.permissions.viewAssignments)
        assertEquals("Nord", profile.teams.single().teamName)
        assertEquals("Mitglied", profile.teams.single().role)
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
    fun `reads the same team variants as the web reference`() {
        val profile = parser.parse(
            """
            {
              "id": 1,
              "name": "A",
              "team_id": 7,
              "current_team_id": 8,
              "currentTeam": {"id": 8, "label": "Aktuelles Team"},
              "team_ids": [9, "10"],
              "all_teams": [{"id": 7, "name": "Stammteam"}],
              "ownedTeams": [{"id": 11, "name": "Geleitetes Team"}],
              "teamMemberships": [
                {"team_id": 12, "team": {"id": 12, "name": "Projektteam"}, "role_label": "Mitglied"}
              ]
            }
            """.trimIndent(),
        )

        val teams = profile.teams.associateBy { it.teamId }
        assertEquals(setOf("7", "8", "9", "10", "11", "12"), teams.keys)
        assertEquals("Stammteam", teams["7"]?.teamName)
        assertEquals("8", profile.teams.first().teamId)
        assertTrue(teams["8"]?.isCurrent == true)
        assertEquals("lead", teams["11"]?.role)
        assertEquals("Mitglied", teams["12"]?.role)
    }

    @Test
    fun `does not mistake membership record id for a team id`() {
        val profile = parser.parse(
            """
            {
              "id": 1,
              "name": "A",
              "memberships": [
                {"id": 99, "role": "member"},
                {"id": 100, "team_id": 7, "team": {"id": 8, "name": "Nord"}}
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("7"), profile.teams.mapNotNull { it.teamId })
        assertEquals("Nord", profile.teams.single().teamName)
    }

    @Test
    fun `rejects payload without recognizable profile`() {
        assertTrue(runCatching { parser.parse("{\"data\":{}}") }.isFailure)
    }
}
