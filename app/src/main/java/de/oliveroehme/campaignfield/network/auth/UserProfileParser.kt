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

        fun addTeam(
            team: JsonObject?,
            fallbackId: String? = null,
            roleSource: JsonObject? = team,
            defaultRole: String? = null,
        ) {
            val id = team?.text("id") ?: fallbackId
            val name = team?.text("name")
                ?: team?.text("label")
                ?: id?.let { "Team #$it" }
                ?: return
            val roles = rolesFrom(roleSource, defaultRole)
            val key = id ?: "name:$name"
            val existing = memberships[key]
            memberships[key] = TeamMembership(
                teamId = id ?: existing?.teamId,
                teamName = when {
                    existing == null -> name
                    existing.teamName.startsWith("Team #") && !name.startsWith("Team #") -> name
                    else -> existing.teamName
                },
                roles = (existing?.roles.orEmpty() + roles).distinct(),
            )
        }

        addTeam(user["team"] as? JsonObject, fallbackId = user.text("team_id"))
        addTeam(
            (user["current_team"] as? JsonObject) ?: (user["currentTeam"] as? JsonObject),
            fallbackId = user.text("current_team_id"),
        )
        (user["team_ids"] as? JsonArray)?.forEach { element ->
            addTeam(team = null, fallbackId = element.primitiveText())
        }

        sequenceOf("teams", "all_teams", "allTeams")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { addTeam(it) }

        sequenceOf("owned_teams", "ownedTeams")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { addTeam(it, defaultRole = "lead") }

        sequenceOf("memberships", "team_memberships", "teamMemberships")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)
            .mapNotNull { it as? JsonObject }
            .forEach { membership ->
                addTeam(
                    team = membership["team"] as? JsonObject,
                    fallbackId = membership.text("team_id") ?: membership.text("id"),
                    roleSource = membership,
                )
            }

        return memberships.values.toList()
    }

    private fun rolesFrom(source: JsonObject?, defaultRole: String?): List<String> = buildList {
        addLabels(source?.get("roles"))
        addLabel(source?.get("role_label"))
        addLabel(source?.get("role_name"))
        addLabel(source?.get("role"))
        addLabel((source?.get("pivot") as? JsonObject)?.get("role_label"))
        addLabel((source?.get("pivot") as? JsonObject)?.get("role_name"))
        addLabel((source?.get("pivot") as? JsonObject)?.get("role"))
        addLabel((source?.get("membership") as? JsonObject)?.get("role_label"))
        addLabel((source?.get("membership") as? JsonObject)?.get("role_name"))
        addLabel((source?.get("membership") as? JsonObject)?.get("role"))
        if (isEmpty()) defaultRole?.let(::add)
    }.distinct()

    private fun MutableList<String>.addLabels(element: JsonElement?) {
        when (element) {
            is JsonArray -> element.forEach { addLabel(it) }
            else -> addLabel(element)
        }
    }

    private fun MutableList<String>.addLabel(element: JsonElement?) {
        val label = when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> element.text("display_name")
                ?: element.text("name")
                ?: element.text("label")
            else -> null
        }
        label?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
    }

    private fun JsonObject.text(key: String): String? = when (val value = get(key)) {
        is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        else -> null
    }

    private fun JsonElement.primitiveText(): String? = (this as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun JsonElement?.asStrictBoolean(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return !primitive.isString && primitive.booleanOrNull == true
    }
}
