package de.oliveroehme.campaignfield.domain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import kotlinx.serialization.Serializable

@Serializable
data class AssignmentMapData(
    val buildingCount: Int = 0,
    val posterCount: Int = 0,
    val campaignBoothCount: Int = 0,
    val features: List<AssignmentMapFeature> = emptyList(),
)

@Serializable
data class AssignmentMapFeature(
    val id: String,
    val kind: AssignmentMapFeatureKind,
    val geometryGeoJson: String,
    val status: BuildingStatus? = null,
    val resourceStatus: String? = null,
    val canUpdate: Boolean = false,
    val canDelete: Boolean = false,
    val label: String? = null,
    val note: String? = null,
    val serverUpdatedAt: String? = null,
    val isPendingSync: Boolean = false,
)

@Serializable
enum class AssignmentMapFeatureKind {
    BUILDING,
    POSTER,
    CAMPAIGN_BOOTH,
}

@Serializable
data class AssignmentLocationInput(
    val latitude: Double,
    val longitude: Double,
    val label: String? = null,
    val note: String? = null,
    val status: String? = null,
) {
    init {
        require(latitude in -90.0..90.0)
        require(longitude in -180.0..180.0)
    }

    fun pointGeoJson(): String = buildJsonObject {
        put("type", "Point")
        put("coordinates", buildJsonArray { add(longitude); add(latitude) })
    }.toString()
}

@Serializable
enum class BuildingStatus(val apiValue: String, val displayName: String) {
    OPEN("open", "Offen"),
    DONE("done", "Erledigt"),
    BLOCKED("blocked", "Blockiert"),
    UNREACHABLE("unreachable", "Nicht erreichbar"),
    SKIPPED("skipped", "Übersprungen"),
    PROBLEM("problem", "Problem"),
    UNKNOWN("unknown", "Unbekannt");

    companion object {
        val actionStatuses = listOf(DONE, BLOCKED, UNREACHABLE, SKIPPED, PROBLEM)

        fun fromApi(value: String?): BuildingStatus = entries.firstOrNull {
            it.apiValue == value?.trim()?.lowercase()
        } ?: UNKNOWN
    }
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
                                        put("featureId", feature.id)
                                        when (feature.kind) {
                                            AssignmentMapFeatureKind.BUILDING -> {
                                                put("kind", "building")
                                                put("color", feature.status.mapColor())
                                                put("fillOpacity", 0.72)
                                                put("extrusionHeight", 5.0)
                                                put("radius", 4.0)
                                                put("status", feature.status?.apiValue ?: BuildingStatus.OPEN.apiValue)
                                                put("pendingSync", feature.isPendingSync)
                                            }
                                            AssignmentMapFeatureKind.POSTER -> {
                                                put("kind", "poster")
                                                put("color", "#FFB84D")
                                                put("fillOpacity", 0.9)
                                                put("extrusionHeight", 0.0)
                                                put("radius", 5.0)
                                                put("pendingSync", feature.isPendingSync)
                                            }
                                            AssignmentMapFeatureKind.CAMPAIGN_BOOTH -> {
                                                put("kind", "booth")
                                                put("color", "#38FF9C")
                                                put("fillOpacity", 0.95)
                                                put("extrusionHeight", 0.0)
                                                put("radius", 7.0)
                                                put("pendingSync", feature.isPendingSync)
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

private fun BuildingStatus?.mapColor(): String = when (this) {
    BuildingStatus.DONE -> "#38FF9C"
    BuildingStatus.BLOCKED, BuildingStatus.SKIPPED -> "#FFB84D"
    BuildingStatus.UNREACHABLE, BuildingStatus.PROBLEM -> "#FF5C7A"
    BuildingStatus.OPEN, BuildingStatus.UNKNOWN, null -> "#16414D"
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
