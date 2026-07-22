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
import de.oliveroehme.campaignfield.map.FieldGeoJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
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
            description = assignment.text("description"),
            instructions = parseInstructions(typeConfig),
            permissions = parsePermissions(assignment["can"] as? JsonObject),
        )
    }

    fun parseArea(payload: String): AreaSummary {
        val root = json.parseToJsonElement(payload)
        val area = ((root as? JsonObject)?.get("data") as? JsonObject)
            ?: root as? JsonObject
            ?: throw IllegalArgumentException("Zielgebiet ist kein Objekt.")
        return parseAreaObject(area)
    }

    fun parseAreas(payload: String): List<AreaSummary> {
        val root = json.parseToJsonElement(payload)
        val rootObject = root as? JsonObject
        val nestedData = rootObject?.get("data") as? JsonObject
        val values = when (root) {
            is JsonArray -> root
            is JsonObject -> root["data"] as? JsonArray
                ?: nestedData?.get("data") as? JsonArray
                ?: root["areas"] as? JsonArray
            else -> null
        } ?: throw IllegalArgumentException("Zielgebietsliste fehlt.")
        return values.mapNotNull { value ->
            (value as? JsonObject)?.let { runCatching { parseAreaObject(it) }.getOrNull() }
        }
    }

    private fun parseSummary(assignment: JsonObject): AssignmentSummary {
        val id = assignment.text("id")
            ?: assignment.text("assignment_id")
            ?: throw IllegalArgumentException("Assignment-ID fehlt.")
        val campaign = relation(
            assignment = assignment,
            objectKeys = arrayOf("campaign"),
            idKeys = arrayOf("campaign_id"),
            labelKeys = arrayOf("title", "name", "label"),
            fallbackPrefix = "Kampagne",
        )?.let { CampaignSummary(it.id, it.name) }
        val team = relation(
            assignment = assignment,
            objectKeys = arrayOf("team"),
            idKeys = arrayOf("team_id"),
            labelKeys = arrayOf("name", "label"),
            fallbackPrefix = "Team",
        )?.let { TeamSummary(it.id, it.name) }
        val areaObject = (assignment["target_area"] as? JsonObject)
            ?: (assignment["area"] as? JsonObject)
        val area = areaObject?.let(::parseAreaObject)
            ?: assignment.firstText("target_area_id", "area_id")?.let { id ->
                AreaSummary(id = id, name = "Zielgebiet #$id")
            }
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
            startsAt = assignment.text("starts_at"),
            dueAt = assignment.text("due_at"),
            campaign = campaign,
            team = team,
            area = area,
        )
    }

    private fun parseAreaObject(area: JsonObject): AreaSummary {
        val id = area.text("id") ?: area.text("area_id")
        val name = area.firstText("name", "label") ?: "Zielgebiet #${id ?: "?"}"
        val geoJson = AREA_GEOMETRY_KEYS.firstNotNullOfOrNull { key ->
            FieldGeoJson.normalize(area[key])
        }
        return AreaSummary(
            id = id,
            name = name,
            geoJson = geoJson,
            centerLatitude = area.coordinate("center_latitude", "latitude", "lat"),
            centerLongitude = area.coordinate("center_longitude", "longitude", "lng", "lon"),
        )
    }

    private fun parseInstructions(typeConfig: JsonElement?): List<AssignmentInstruction> {
        val config = typeConfig as? JsonObject ?: return emptyList()
        return INSTRUCTION_KEYS
            .flatMap { key -> readInstructionValues(config[key]) }
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .map { AssignmentInstruction("Anweisung", it) }
    }

    private fun readInstructionValues(value: JsonElement?): List<String> = when (value) {
        is JsonPrimitive -> if (value.isString) {
            value.contentOrNull?.let(::listOf).orEmpty()
        } else {
            emptyList()
        }
        is JsonArray -> value.flatMap(::readInstructionValues)
        is JsonObject -> INSTRUCTION_TEXT_KEYS.firstNotNullOfOrNull { key ->
            (value[key] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.contentOrNull
                ?.trim()
                ?.takeIf(String::isNotEmpty)
        }?.let(::listOf).orEmpty()
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
        labelKeys: Array<String>,
        fallbackPrefix: String,
    ): Relation? {
        val objectValue = objectKeys.firstNotNullOfOrNull { assignment[it] as? JsonObject }
        val id = objectValue?.text("id") ?: assignment.firstText(*idKeys)
        val name = objectValue?.firstText(*labelKeys)
        if (id == null && name == null) return null
        return Relation(id, name ?: "$fallbackPrefix #$id")
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

    private fun JsonObject.coordinate(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
        text(key)?.replace(',', '.')?.toDoubleOrNull()
    }?.takeIf(Double::isFinite)

    private data class Relation(val id: String?, val name: String)

    private companion object {
        val INSTRUCTION_KEYS = listOf(
            "instructions",
            "required_instructions",
            "mandatory_instructions",
            "briefing",
            "checklist",
            "steps",
        )
        val INSTRUCTION_TEXT_KEYS = listOf(
            "instruction",
            "text",
            "label",
            "title",
            "description",
        )
        val AREA_GEOMETRY_KEYS = listOf("geometry", "geojson", "geo_json", "geometry_json")
    }
}
