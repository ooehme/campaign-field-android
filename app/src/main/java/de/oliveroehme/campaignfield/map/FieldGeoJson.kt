package de.oliveroehme.campaignfield.map

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    /** Returns null when the GeoJSON has no polygon geometry. */
    fun contains(encoded: String?, coordinate: MapCoordinate): Boolean? {
        if (encoded.isNullOrBlank()) return null
        val root = runCatching { json.parseToJsonElement(encoded) }.getOrNull() ?: return null
        val polygons = polygons(root)
        if (polygons.isEmpty()) return null
        return polygons.any { rings ->
            rings.firstOrNull()?.let { pointInRing(coordinate, it) } == true &&
                rings.drop(1).none { pointInRing(coordinate, it) }
        }
    }

    fun distanceMeters(encoded: String?, coordinate: MapCoordinate): Double? {
        if (contains(encoded, coordinate) == true) return 0.0
        return positions(encoded)
            .minOfOrNull { position -> haversineMeters(coordinate, position) }
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

    private fun polygons(element: JsonElement?): List<List<List<MapCoordinate>>> {
        val value = element as? JsonObject ?: return emptyList()
        return when ((value["type"] as? JsonPrimitive)?.contentOrNull) {
            "FeatureCollection" -> (value["features"] as? JsonArray)?.flatMap(::polygons).orEmpty()
            "Feature" -> polygons(value["geometry"])
            "GeometryCollection" -> (value["geometries"] as? JsonArray)?.flatMap(::polygons).orEmpty()
            "Polygon" -> listOf(readRings(value["coordinates"]))
            "MultiPolygon" -> (value["coordinates"] as? JsonArray)
                ?.map(::readRings)
                .orEmpty()
            else -> emptyList()
        }.filter { it.isNotEmpty() }
    }

    private fun readRings(element: JsonElement?): List<List<MapCoordinate>> =
        (element as? JsonArray)?.mapNotNull { ring ->
            val coordinates = (ring as? JsonArray)?.mapNotNull(::readCoordinate).orEmpty()
            coordinates.takeIf { it.size >= 3 }
        }.orEmpty()

    private fun readCoordinate(element: JsonElement): MapCoordinate? {
        val values = element as? JsonArray ?: return null
        val longitude = (values.getOrNull(0) as? JsonPrimitive)?.doubleOrNull ?: return null
        val latitude = (values.getOrNull(1) as? JsonPrimitive)?.doubleOrNull ?: return null
        return MapCoordinate(latitude, longitude).takeIf {
            latitude in -90.0..90.0 && longitude in -180.0..180.0
        }
    }

    private fun pointInRing(point: MapCoordinate, ring: List<MapCoordinate>): Boolean {
        var inside = false
        var previous = ring.last()
        ring.forEach { current ->
            if (pointOnSegment(point, previous, current)) return true
            val intersects = (current.latitude > point.latitude) != (previous.latitude > point.latitude) &&
                point.longitude < (previous.longitude - current.longitude) *
                (point.latitude - current.latitude) /
                (previous.latitude - current.latitude) + current.longitude
            if (intersects) inside = !inside
            previous = current
        }
        return inside
    }

    private fun pointOnSegment(
        point: MapCoordinate,
        start: MapCoordinate,
        end: MapCoordinate,
    ): Boolean {
        val cross = (point.latitude - start.latitude) * (end.longitude - start.longitude) -
            (point.longitude - start.longitude) * (end.latitude - start.latitude)
        if (kotlin.math.abs(cross) > 1e-10) return false
        return point.longitude in minOf(start.longitude, end.longitude)..maxOf(start.longitude, end.longitude) &&
            point.latitude in minOf(start.latitude, end.latitude)..maxOf(start.latitude, end.latitude)
    }

    private fun haversineMeters(start: MapCoordinate, end: MapCoordinate): Double {
        val latitudeDistance = Math.toRadians(end.latitude - start.latitude)
        val longitudeDistance = Math.toRadians(end.longitude - start.longitude)
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)
        val a = sin(latitudeDistance / 2) * sin(latitudeDistance / 2) +
            cos(startLatitude) * cos(endLatitude) *
            sin(longitudeDistance / 2) * sin(longitudeDistance / 2)
        return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    private const val EARTH_RADIUS_METERS = 6_371_000.0
}
