package de.oliveroehme.campaignfield.network.auth

import de.oliveroehme.campaignfield.domain.auth.TeamMembership
import de.oliveroehme.campaignfield.domain.auth.UserPermissions
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.text.Collator
import java.util.Locale

internal class UserProfileParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parse(payload: String): UserProfile {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: throw IllegalArgumentException("Benutzerantwort ist kein Objekt.")
        val user = (root["data"] as? JsonObject) ?: root

        val id = user.text("id")
        val email = user.text("email").orEmpty()
        val name = user.text("name")
            ?: user.text("display_name")
            ?: listOfNotNull(user.text("first_name"), user.text("last_name"))
                .joinToString(" ")
                .ifBlank { null }
            ?: email.substringBefore('@').takeIf(String::isNotBlank)
            ?: "Unbekannter Benutzer"

        require(id != null || email.isNotBlank() || name != "Unbekannter Benutzer") {
            "Benutzerantwort enthält kein erkennbares Profil."
        }

        return UserProfile(
            id = id,
            name = name,
            email = email,
            appRole = user.text("app_role")
                ?: user.text("role")
                ?: firstLabel(user["roles"])
                ?: user.text("display_role"),
            roles = buildList {
                addLabels(user["roles"])
                addLabel(user["role"])
                addLabel(user["display_role"])
                addLabel(user["app_role"])
            }.distinct(),
            teams = parseTeams(user),
            permissions = UserPermissions(
                viewAssignments = (user["can"] as? JsonObject)
                    ?.get("view_assignments")
                    .asStrictBoolean(),
            ),
        )
    }

    private fun parseTeams(user: JsonObject): List<TeamMembership> {
        val memberships = linkedMapOf<String, TeamMembership>()
        val primaryTeam = user["team"] as? JsonObject
        val currentTeam = (user["current_team"] as? JsonObject)
            ?: (user["currentTeam"] as? JsonObject)
        val primaryTeamId = user.positiveId("team_id")
            ?: primaryTeam.recordId()
            ?: currentTeam.recordId()
        val currentTeamId = user.positiveId("current_team_id") ?: primaryTeamId

        fun addTeam(
            team: JsonObject?,
            teamId: String?,
            roleSource: JsonObject? = team,
            defaultRole: String? = null,
        ) {
            val id = teamId ?: return
            val explicitName = team?.text("name") ?: team?.text("label")
            val existing = memberships[id]
            memberships[id] = TeamMembership(
                teamId = id,
                teamName = explicitName ?: existing?.teamName ?: "Team #$id",
                role = primaryRole(roleSource) ?: defaultRole ?: existing?.role,
                isCurrent = existing?.isCurrent == true || id == currentTeamId,
            )
        }

        addTeam(primaryTeam, teamId = primaryTeamId)
        addTeam(
            currentTeam ?: primaryTeam,
            teamId = currentTeamId,
        )
        (user["team_ids"] as? JsonArray)?.forEach { element ->
            addTeam(team = null, teamId = element.positiveId())
        }

        sequenceOf("teams", "all_teams", "allTeams")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { addTeam(it, teamId = it.recordId()) }

        sequenceOf("owned_teams", "ownedTeams")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { addTeam(it, teamId = it.recordId(), defaultRole = "lead") }

        sequenceOf("memberships", "team_memberships", "teamMemberships")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { membership ->
                addTeam(
                    team = membership["team"] as? JsonObject,
                    teamId = membership.positiveId("team_id")
                        ?: (membership["team"] as? JsonObject).recordId(),
                    roleSource = membership,
                )
            }

        val collator = Collator.getInstance(Locale.GERMAN).apply {
            strength = Collator.PRIMARY
        }
        return memberships.values.sortedWith { left, right ->
            when {
                left.isCurrent != right.isCurrent -> if (left.isCurrent) -1 else 1
                else -> collator.compare(left.teamName, right.teamName)
            }
        }
    }

    private fun primaryRole(source: JsonObject?): String? = sequenceOf(
        source?.get("role_label"),
        source?.get("role_name"),
        source?.get("role"),
        (source?.get("pivot") as? JsonObject)?.get("role_label"),
        (source?.get("pivot") as? JsonObject)?.get("role_name"),
        (source?.get("pivot") as? JsonObject)?.get("role"),
        (source?.get("membership") as? JsonObject)?.get("role_label"),
        (source?.get("membership") as? JsonObject)?.get("role_name"),
        (source?.get("membership") as? JsonObject)?.get("role"),
        source?.get("roles"),
    ).mapNotNull(::firstLabel).firstOrNull()

    private fun MutableList<String>.addLabels(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach { addLabel(it) }
            else -> addLabel(element)
        }
    }

    private fun MutableList<String>.addLabel(element: JsonElement?) {
        firstLabel(element)?.let(::add)
    }

    private fun firstLabel(element: JsonElement?): String? = when (element) {
        is JsonArray -> element.firstNotNullOfOrNull(::firstLabel)
        is JsonPrimitive -> element.contentOrNull
        is JsonObject -> element.text("label")
            ?: element.text("title")
            ?: element.text("name")
            ?: element.text("display_name")
        else -> null
    }?.trim()?.takeIf(String::isNotEmpty)

    private fun JsonObject.text(key: String): String? = when (val value = get(key)) {
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        else -> null
    }

    private fun JsonObject.positiveId(key: String): String? = get(key).positiveId()

    private fun JsonObject?.recordId(): String? = this?.positiveId("id")

    private fun JsonElement?.positiveId(): String? = (this as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.toLongOrNull()
        ?.takeIf { it > 0 }
        ?.toString()

    private fun JsonElement?.asStrictBoolean(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return !primitive.isString && primitive.booleanOrNull == true
    }
}
