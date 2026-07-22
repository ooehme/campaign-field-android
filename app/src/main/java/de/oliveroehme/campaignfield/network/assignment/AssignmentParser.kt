package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AreaSummary
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentInstruction
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentPermissions
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.CampaignSummary
import de.oliveroehme.campaignfield.domain.TeamSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

internal class AssignmentParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parsePage(payload: String): AssignmentPage {
        val root = json.parseToJsonElement(payload)
        val rootObject = root as? JsonObject
        val nestedData = rootObject?.get("data") as? JsonObject
        val rawItems = when (root) {
            is JsonArray -> root
            is JsonObject -> root["data"] as? JsonArray
                ?: root["assignments"] as? JsonArray
                ?: nestedData?.get("data") as? JsonArray
                ?: nestedData?.get("assignments") as? JsonArray
            else -> null
        } ?: throw IllegalArgumentException("Assignment-Liste fehlt.")

        val items = rawItems.mapNotNull { element ->
            (element as? JsonObject)?.let { runCatching { parseSummary(it) }.getOrNull() }
        }
        require(rawItems.isEmpty() || items.isNotEmpty()) {
            "Assignment-Liste enthält keine lesbaren Einträge."
        }

        val pagination = sequenceOf(
            rootObject?.get("meta") as? JsonObject,
            nestedData?.get("meta") as? JsonObject,
            nestedData,
            rootObject,
        ).filterNotNull().firstOrNull { candidate ->
            candidate.containsKey("current_page") || candidate.containsKey("last_page")
        }
        val currentPage = pagination.int("current_page") ?: 1
        val lastPage = pagination.int("last_page") ?: currentPage
        return AssignmentPage(
            items = items,
            currentPage = currentPage.coerceAtLeast(1),
            lastPage = lastPage.coerceAtLeast(currentPage.coerceAtLeast(1)),
            perPage = pagination.int("per_page") ?: items.size,
            total = pagination.int("total") ?: items.size,
        )
    }

    fun parseDetail(payload: String): AssignmentDetail {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: throw IllegalArgumentException("Assignment-Detail ist kein Objekt.")
        val assignment = (root["data"] as? JsonObject)
            ?.let { (it["assignment"] as? JsonObject) ?: it }
            ?: (root["assignment"] as? JsonObject)
            ?: root
        val typeConfig = assignment["type_config"]
        return AssignmentDetail(
            summary = parseSummary(assignment),
            description = assignment.text("description")
                ?: assignment.text("briefing")
                ?: assignment.text("notes"),
            instructions = parseInstructions(typeConfig),
            permissions = parsePermissions(assignment["can"] as? JsonObject),
        )
    }

    private fun parseSummary(assignment: JsonObject): AssignmentSummary {
        val id = assignment.text("id")
            ?: assignment.text("assignment_id")
            ?: throw IllegalArgumentException("Assignment-ID fehlt.")
        val campaign = relation(
            assignment = assignment,
            objectKeys = arrayOf("campaign"),
            idKeys = arrayOf("campaign_id"),
            fallbackPrefix = "Kampagne",
        )?.let { CampaignSummary(it.id, it.name) }
        val team = relation(
            assignment = assignment,
            objectKeys = arrayOf("team"),
            idKeys = arrayOf("team_id"),
            fallbackPrefix = "Team",
        )?.let { TeamSummary(it.id, it.name) }
        val area = relation(
            assignment = assignment,
            objectKeys = arrayOf("area", "target_area"),
            idKeys = arrayOf("area_id", "target_area_id"),
            fallbackPrefix = "Zielgebiet",
        )?.let { AreaSummary(it.id, it.name) }
        return AssignmentSummary(
            id = id,
            title = assignment.text("title")
                ?: assignment.text("name")
                ?: assignment.text("label")
                ?: campaign?.name
                ?: "Auftrag #$id",
            type = AssignmentType.fromApi(
                assignment.text("type") ?: assignment.text("assignment_type"),
            ),
            status = AssignmentStatus.fromApi(assignment.text("status")),
            startsAt = assignment.firstText("starts_at", "start_at", "start_date"),
            dueAt = assignment.firstText("due_at", "due_date", "ends_at", "end_date"),
            campaign = campaign,
            team = team,
            area = area,
        )
    }

    private fun parseInstructions(typeConfig: JsonElement?): List<AssignmentInstruction> = when (typeConfig) {
        is JsonPrimitive -> typeConfig.displayValue()?.let {
            listOf(AssignmentInstruction("Anweisungen", it))
        }.orEmpty()
        is JsonArray -> typeConfig.displayValue()?.let {
            listOf(AssignmentInstruction("Anweisungen", it))
        }.orEmpty()
        is JsonObject -> typeConfig.entries.mapNotNull { (key, value) ->
            if (key in HIDDEN_TYPE_CONFIG_KEYS) return@mapNotNull null
            value.displayValue()?.takeIf(String::isNotBlank)?.let {
                AssignmentInstruction(TYPE_CONFIG_LABELS[key] ?: humanize(key), it)
            }
        }
        else -> emptyList()
    }

    private fun parsePermissions(can: JsonObject?): AssignmentPermissions = AssignmentPermissions(
        update = can.strictBoolean("update"),
        start = can.strictBoolean("start"),
        pause = can.strictBoolean("pause"),
        complete = can.strictBoolean("complete"),
        cancel = can.strictBoolean("cancel"),
        reportIssue = can.strictBoolean("report_issue"),
        createProof = can.strictBoolean("create_proof"),
        managePosterLocations = can.strictBoolean("manage_poster_locations"),
        manageCampaignBoothLocation = can.strictBoolean("manage_campaign_booth_location"),
        viewTeamLocations = can.strictBoolean("view_team_locations"),
        reportTeamLocation = can.strictBoolean("report_team_location"),
    )

    private fun relation(
        assignment: JsonObject,
        objectKeys: Array<String>,
        idKeys: Array<String>,
        fallbackPrefix: String,
    ): Relation? {
        val objectValue = objectKeys.firstNotNullOfOrNull { assignment[it] as? JsonObject }
        val id = objectValue?.text("id") ?: assignment.firstText(*idKeys)
        val name = objectValue?.firstText("name", "title", "label")
        if (id == null && name == null) return null
        return Relation(id, name ?: "$fallbackPrefix #$id")
    }

    private fun JsonElement.displayValue(): String? = when (this) {
        JsonNull -> null
        is JsonPrimitive -> when {
            !isString && booleanOrNull != null -> if (booleanOrNull == true) "Ja" else "Nein"
            else -> contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        }
        is JsonArray -> mapNotNull { item ->
            when (item) {
                is JsonObject -> item.firstText("label", "name", "title", "value", "description")
                else -> item.displayValue()
            }
        }.joinToString(" · ").takeIf(String::isNotBlank)
        is JsonObject -> entries.mapNotNull { (key, value) ->
            value.displayValue()?.let { "${TYPE_CONFIG_LABELS[key] ?: humanize(key)}: $it" }
        }.joinToString(" · ").takeIf(String::isNotBlank)
    }

    private fun JsonObject?.strictBoolean(key: String): Boolean {
        val value = this?.get(key) as? JsonPrimitive ?: return false
        return !value.isString && value.booleanOrNull == true
    }

    private fun JsonObject?.int(key: String): Int? = this?.get(key)
        ?.let { it as? JsonPrimitive }
        ?.contentOrNull
        ?.toIntOrNull()

    private fun JsonObject.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> text(key) }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun humanize(value: String): String = value
        .split('_', '-')
        .filter(String::isNotBlank)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercase) }

    private data class Relation(val id: String?, val name: String)

    private companion object {
        val HIDDEN_TYPE_CONFIG_KEYS = setOf("id", "geojson", "geometry", "coordinates")
        val TYPE_CONFIG_LABELS = mapOf(
            "instructions" to "Anweisungen",
            "instruction" to "Anweisung",
            "material" to "Material",
            "materials" to "Material",
            "quantity" to "Menge",
            "count" to "Anzahl",
            "notes" to "Hinweise",
            "target" to "Ziel",
        )
    }
}
