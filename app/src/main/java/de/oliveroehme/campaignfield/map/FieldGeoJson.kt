package de.oliveroehme.campaignfield.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

data class MapCoordinate(
    val latitude: Double,
    val longitude: Double,
)

/** Defensive GeoJSON handling shared by the API mapper and the map renderer. */
object FieldGeoJson {
    private val json = Json { ignoreUnknownKeys = true }
    private val supportedTypes = setOf(
        "FeatureCollection",
        "Feature",
        "Point",
        "MultiPoint",
        "LineString",
        "MultiLineString",
        "Polygon",
        "MultiPolygon",
        "GeometryCollection",
    )

    fun normalize(value: JsonElement?): String? {
        val candidate = when (value) {
            is JsonPrimitive -> value.contentOrNull
                ?.takeIf { value.isString }
                ?.let { encoded -> runCatching { json.parseToJsonElement(encoded) }.getOrNull() }
            else -> value
        } as? JsonObject ?: return null
        val type = (candidate["type"] as? JsonPrimitive)?.contentOrNull
        return candidate.toString().takeIf { type in supportedTypes }
    }

    fun positions(encoded: String?): List<MapCoordinate> {
        if (encoded.isNullOrBlank()) return emptyList()
        val root = runCatching { json.parseToJsonElement(encoded) }.getOrNull() ?: return emptyList()
        return positions(root)
    }

    private fun positions(element: JsonElement?): List<MapCoordinate> {
        val value = element as? JsonObject ?: return emptyList()
        return when ((value["type"] as? JsonPrimitive)?.contentOrNull) {
            "FeatureCollection" -> (value["features"] as? JsonArray)
                ?.flatMap(::positions)
                .orEmpty()
            "Feature" -> positions(value["geometry"])
            "GeometryCollection" -> (value["geometries"] as? JsonArray)
                ?.flatMap(::positions)
                .orEmpty()
            "Point", "MultiPoint", "LineString", "MultiLineString", "Polygon", "MultiPolygon" ->
                coordinatePositions(value["coordinates"])
            else -> emptyList()
        }
    }

    private fun coordinatePositions(element: JsonElement?): List<MapCoordinate> {
        val values = element as? JsonArray ?: return emptyList()
        val longitude = (values.getOrNull(0) as? JsonPrimitive)?.doubleOrNull
        val latitude = (values.getOrNull(1) as? JsonPrimitive)?.doubleOrNull
        if (
            longitude != null && latitude != null &&
            longitude.isFinite() && latitude.isFinite() &&
            longitude in -180.0..180.0 && latitude in -90.0..90.0
        ) {
            return listOf(MapCoordinate(latitude, longitude))
        }
        return values.flatMap(::coordinatePositions)
    }
}
