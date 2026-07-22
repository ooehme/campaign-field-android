package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.domain.auth.TeamDetail
import de.oliveroehme.campaignfield.domain.auth.TeamInvitation
import de.oliveroehme.campaignfield.domain.auth.TeamMember
import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class ProfileSupplementParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parseTeam(payload: String): TeamDetail {
        val parsed = json.parseToJsonElement(payload)
        val root = parsed as? JsonObject
            ?: throw IllegalArgumentException("Teamantwort ist kein Objekt.")
        val team = (root["data"] as? JsonObject) ?: root
        val id = team.positiveId("id")
            ?: throw IllegalArgumentException("Team-ID fehlt.")
        val members = sequenceOf("users", "members")
            .mapNotNull { team[it] as? JsonArray }
            .firstOrNull()
            ?.mapNotNull { element ->
                (element as? JsonObject)?.let { runCatching { parseMember(it) }.getOrNull() }
            }
            .orEmpty()
            .distinctBy(TeamMember::id)
            .sortedWith(memberComparator())
        return TeamDetail(
            id = id,
            name = team.firstText("name", "label") ?: "Team #$id",
            members = members,
        )
    }

    fun parseInvitations(payload: String): List<TeamInvitation> {
        val parsed = json.parseToJsonElement(payload)
        val root = parsed as? JsonObject
        val nestedData = root?.get("data") as? JsonObject
        val values = when (parsed) {
            is JsonArray -> parsed
            is JsonObject -> parsed["data"] as? JsonArray
                ?: parsed["invitations"] as? JsonArray
                ?: nestedData?.get("data") as? JsonArray
                ?: nestedData?.get("invitations") as? JsonArray
            else -> null
        } ?: throw IllegalArgumentException("Einladungsliste fehlt.")

        return values.mapNotNull { element ->
            (element as? JsonObject)?.let { runCatching { parseInvitation(it) }.getOrNull() }
        }.filterNot { invitation ->
            invitation.status?.trim()?.lowercase() in setOf("accepted", "declined")
        }
    }

    private fun parseMember(member: JsonObject): TeamMember {
        val id = member.positiveId("id")
            ?: throw IllegalArgumentException("Mitglieds-ID fehlt.")
        val pivot = member["pivot"] as? JsonObject
        val membership = member["membership"] as? JsonObject
        return TeamMember(
            id = id,
            name = pivot?.firstText("display_name")
                ?: member.firstText("name", "display_name")
                ?: "Benutzer #$id",
            email = member.text("email"),
            role = sequenceOf(pivot, membership, member)
                .filterNotNull()
                .firstNotNullOfOrNull { it.firstText("role_label", "role_name", "role") },
        )
    }

    private fun parseInvitation(invitation: JsonObject): TeamInvitation {
        val id = invitation.positiveId("id")
            ?: throw IllegalArgumentException("Einladungs-ID fehlt.")
        val team = invitation["team"] as? JsonObject
        val teamId = invitation.positiveId("team_id") ?: team?.positiveId("id")
        return TeamInvitation(
            id = id,
            teamName = team?.firstText("name", "label")
                ?: teamId?.let { "Team #$it" }
                ?: "Team",
            role = invitation.firstText("role_label", "role_name", "role"),
            status = invitation.text("status"),
        )
    }

    private fun memberComparator(): Comparator<TeamMember> {
        val collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.PRIMARY }
        return Comparator { left, right -> collator.compare(left.name, right.name) }
    }

    private fun JsonObject.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> text(key) }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun JsonObject.positiveId(key: String): String? = (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.toString()
}
