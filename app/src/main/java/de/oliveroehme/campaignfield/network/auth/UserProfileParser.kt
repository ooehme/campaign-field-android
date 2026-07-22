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
        val entries = sequenceOf("teams", "memberships", "team_memberships")
            .mapNotNull { user[it] as? JsonArray }
            .flatMap(JsonArray::asSequence)

        return entries.mapNotNull { element ->
            val membership = element as? JsonObject ?: return@mapNotNull null
            val team = membership["team"] as? JsonObject ?: membership
            val name = team.text("name") ?: team.text("label") ?: return@mapNotNull null
            val roles = buildList {
                addLabels(membership["roles"])
                addLabel(membership["role"])
                addLabel(membership["role_name"])
                addLabel((membership["pivot"] as? JsonObject)?.get("role"))
                addLabel((membership["membership"] as? JsonObject)?.get("role"))
            }.distinct()
            TeamMembership(
                teamId = team.text("id"),
                teamName = name,
                roles = roles,
            )
        }.distinctBy { it.teamId ?: it.teamName }.toList()
    }

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

    private fun JsonElement?.asStrictBoolean(): Boolean {
        val primitive = this as? JsonPrimitive ?: return false
        return !primitive.isString && primitive.booleanOrNull == true
    }
}
