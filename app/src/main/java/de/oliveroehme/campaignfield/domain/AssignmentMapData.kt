package de.oliveroehme.campaignfield.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class AssignmentMapData(
    val buildingCount: Int = 0,
    val posterCount: Int = 0,
    val features: List<AssignmentMapFeature> = emptyList(),
)

data class AssignmentMapFeature(
    val id: String,
    val kind: AssignmentMapFeatureKind,
    val geometryGeoJson: String,
)

enum class AssignmentMapFeatureKind {
    BUILDING,
    POSTER,
}

fun AssignmentMapData.toGeoJson(): String {
    val json = Json { ignoreUnknownKeys = true }
    return buildJsonObject {
        put("type", "FeatureCollection")
        put(
            "features",
            buildJsonArray {
                features.forEach { feature ->
                    val parsed = runCatching { json.parseToJsonElement(feature.geometryGeoJson) }.getOrNull()
                    extractGeometries(parsed).forEachIndexed { index, geometry ->
                        add(
                            buildJsonObject {
                                put("type", "Feature")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("id", if (index == 0) feature.id else "${feature.id}-$index")
                                        when (feature.kind) {
                                            AssignmentMapFeatureKind.BUILDING -> {
                                                put("kind", "building")
                                                put("color", "#16414D")
                                                put("fillOpacity", 0.72)
                                                put("extrusionHeight", 5.0)
                                                put("radius", 4.0)
                                            }
                                            AssignmentMapFeatureKind.POSTER -> {
                                                put("kind", "poster")
                                                put("color", "#FFB84D")
                                                put("fillOpacity", 0.9)
                                                put("extrusionHeight", 0.0)
                                                put("radius", 5.0)
                                            }
                                        }
                                    },
                                )
                                put("geometry", geometry)
                            },
                        )
                    }
                }
            },
        )
    }.toString()
}

private fun extractGeometries(value: JsonElement?): List<JsonElement> {
    val objectValue = value as? JsonObject ?: return emptyList()
    return when ((objectValue["type"] as? JsonPrimitive)?.content) {
        "Feature" -> listOfNotNull(objectValue["geometry"])
        "FeatureCollection" -> (objectValue["features"] as? JsonArray)
            ?.flatMap { feature -> extractGeometries(feature) }
            .orEmpty()
        else -> listOf(objectValue)
    }
}
