package de.oliveroehme.campaignfield.network.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileSupplementParserTest {
    private val parser = ProfileSupplementParser()

    @Test
    fun `parses wrapped team and member display data`() {
        val team = parser.parseTeam(
            """
            {"data":{"id":7,"name":"Oberbürger","users":[
              {"id":10,"name":"Oliver Oehme","email":"oliver@example.test","pivot":{"role":"member"}},
              {"id":"11","name":"Ulrich Oehme","membership":{"role_label":"Teamlead"}}
            ]}}
            """.trimIndent(),
        )

        assertEquals("7", team.id)
        assertEquals("Oberbürger", team.name)
        assertEquals(listOf("Oliver Oehme", "Ulrich Oehme"), team.members.map { it.name })
        assertEquals("member", team.members.first().role)
        assertEquals("Teamlead", team.members.last().role)
    }

    @Test
    fun `parses nested invitations and removes completed entries`() {
        val invitations = parser.parseInvitations(
            """
            {"data":{"data":[
              {"id":4,"team_id":9,"role":"member","status":"pending"},
              {"id":5,"team":{"id":10,"name":"Süd"},"status":"accepted"}
            ]}}
            """.trimIndent(),
        )

        assertEquals(1, invitations.size)
        assertEquals("4", invitations.single().id)
        assertEquals("Team #9", invitations.single().teamName)
    }
}
