package de.oliveroehme.campaignfield.network.assignment

import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.map.FieldGeoJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put

internal data class AssignmentMapFeaturePage(
    val features: List<AssignmentMapFeature>,
    val currentPage: Int,
    val lastPage: Int,
    val total: Int,
)

internal class AssignmentMapDataParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun parseBuildings(payload: String): AssignmentMapFeaturePage = parsePage(
        payload = payload,
        kind = AssignmentMapFeatureKind.BUILDING,
        nestedKeys = listOf("assignment_buildings", "buildings"),
    )

    fun parsePosters(payload: String): AssignmentMapFeaturePage = parsePage(
        payload = payload,
        kind = AssignmentMapFeatureKind.POSTER,
        nestedKeys = listOf("poster_locations", "posters"),
    )

    private fun parsePage(
        payload: String,
        kind: AssignmentMapFeatureKind,
        nestedKeys: List<String>,
    ): AssignmentMapFeaturePage {
        val parsed = json.parseToJsonElement(payload)
        val root = parsed as? JsonObject
        val dataObject = root?.get("data") as? JsonObject
        val values = when (parsed) {
            is JsonArray -> parsed
            is JsonObject -> parsed["data"] as? JsonArray
                ?: nestedKeys.firstNotNullOfOrNull { parsed[it] as? JsonArray }
                ?: dataObject?.get("data") as? JsonArray
                ?: nestedKeys.firstNotNullOfOrNull { dataObject?.get(it) as? JsonArray }
            else -> null
        } ?: throw IllegalArgumentException("Kartenelementliste fehlt.")
        val pagination = sequenceOf(root?.get("meta") as? JsonObject, dataObject?.get("meta") as? JsonObject, dataObject, root)
            .filterNotNull()
            .firstOrNull { "current_page" in it || "last_page" in it }
        val currentPage = pagination.int("current_page") ?: 1
        val lastPage = pagination.int("last_page") ?: currentPage
        val features = values.mapNotNull { value ->
            (value as? JsonObject)?.let { parseFeature(it, kind) }
        }
        return AssignmentMapFeaturePage(
            features = features,
            currentPage = currentPage.coerceAtLeast(1),
            lastPage = lastPage.coerceAtLeast(currentPage.coerceAtLeast(1)),
            total = pagination.int("total") ?: values.size,
        )
    }

    private fun parseFeature(
        item: JsonObject,
        kind: AssignmentMapFeatureKind,
    ): AssignmentMapFeature? {
        val nested = when (kind) {
            AssignmentMapFeatureKind.BUILDING -> (item["area_building"] as? JsonObject)
                ?: (item["building"] as? JsonObject)
            AssignmentMapFeatureKind.POSTER -> item["poster"] as? JsonObject
        }
        val id = item.firstText(
            "id",
            "assignment_building_id",
            "area_building_id",
            "poster_location_id",
        )
            ?: nested?.text("id")
            ?: return null
        val geometry = geometry(item) ?: geometry(nested) ?: pointGeometry(item) ?: pointGeometry(nested)
            ?: return null
        return AssignmentMapFeature(id, kind, geometry)
    }

    private fun geometry(source: JsonObject?): String? = source?.let { value ->
        GEOMETRY_KEYS.firstNotNullOfOrNull { key -> FieldGeoJson.normalize(value[key]) }
    }

    private fun pointGeometry(source: JsonObject?): String? {
        source ?: return null
        val latitude = source.firstDouble("lat", "latitude", "y") ?: return null
        val longitude = source.firstDouble("lng", "lon", "longitude", "x") ?: return null
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return buildJsonObject {
            put("type", "Point")
            put("coordinates", buildJsonArray { add(longitude); add(latitude) })
        }.toString()
    }

    private fun JsonObject.firstText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> text(key) }

    private fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun JsonObject.firstDouble(vararg keys: String): Double? = keys.firstNotNullOfOrNull { key ->
        text(key)?.replace(',', '.')?.toDoubleOrNull()
    }?.takeIf(Double::isFinite)

    private fun JsonObject?.int(key: String): Int? = (this?.get(key) as? JsonPrimitive)
        ?.contentOrNull
        ?.toIntOrNull()

    private companion object {
        val GEOMETRY_KEYS = listOf("geometry", "geojson", "geo_json", "geometry_json", "footprint")
    }
}
