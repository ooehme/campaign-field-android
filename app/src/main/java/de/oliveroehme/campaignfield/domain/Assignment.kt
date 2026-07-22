package de.oliveroehme.campaignfield.domain

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

@Serializable
data class AssignmentSummary(
    val id: String,
    val title: String,
    val type: AssignmentType,
    val status: AssignmentStatus,
    val startsAt: String? = null,
    val dueAt: String? = null,
    val campaign: CampaignSummary? = null,
    val team: TeamSummary? = null,
    val area: AreaSummary? = null,
)

@Serializable
data class AssignmentDetail(
    val summary: AssignmentSummary,
    val description: String? = null,
    val instructions: List<AssignmentInstruction> = emptyList(),
    val permissions: AssignmentPermissions = AssignmentPermissions(),
)

@Serializable
data class CampaignSummary(
    val id: String? = null,
    val name: String,
)

@Serializable
data class TeamSummary(
    val id: String? = null,
    val name: String,
)

@Serializable
data class AreaSummary(
    val id: String? = null,
    val name: String,
)

@Serializable
data class AssignmentInstruction(
    val label: String,
    val value: String,
)

@Serializable
data class AssignmentPermissions(
    val update: Boolean = false,
    val start: Boolean = false,
    val pause: Boolean = false,
    val complete: Boolean = false,
    val cancel: Boolean = false,
    val reportIssue: Boolean = false,
    val createProof: Boolean = false,
    val managePosterLocations: Boolean = false,
    val manageCampaignBoothLocation: Boolean = false,
    val viewTeamLocations: Boolean = false,
    val reportTeamLocation: Boolean = false,
)

@Serializable
data class AssignmentPage(
    val items: List<AssignmentSummary>,
    val currentPage: Int = 1,
    val lastPage: Int = 1,
    val perPage: Int = items.size,
    val total: Int = items.size,
)

@Serializable
enum class AssignmentType(val apiValue: String, val displayName: String) {
    STANDARD("standard", "Standard"),
    LETTERBOX_DISTRIBUTION("letterbox_distribution", "Briefkastenverteilung"),
    POSTER_FREE("poster_free", "Freie Plakatierung"),
    POSTER_GUIDED("poster_guided", "Geführte Plakatierung"),
    CAMPAIGN_BOOTH("campaign_booth", "Aktionsstand"),
    UNKNOWN("unknown", "Sonstiger Auftrag");

    companion object {
        fun fromApi(value: String?): AssignmentType = entries.firstOrNull {
            it.apiValue == value?.trim()?.lowercase()
        } ?: UNKNOWN
    }
}

@Serializable
enum class AssignmentStatus(
    val apiValue: String,
    val displayName: String,
    internal val sortOrder: Int,
) {
    ACTIVE("active", "Aktiv", 0),
    PAUSED("paused", "Pausiert", 1),
    READY("ready", "Bereit", 2),
    COMPLETED("completed", "Erledigt", 3),
    CANCELLED("cancelled", "Abgebrochen", 4),
    DRAFT("draft", "Entwurf", 5),
    UNKNOWN("unknown", "Unbekannt", 6);

    companion object {
        fun fromApi(value: String?): AssignmentStatus = entries.firstOrNull {
            it.apiValue == value?.trim()?.lowercase()
        } ?: UNKNOWN
    }
}

fun List<AssignmentSummary>.forOperativeOverview(): List<AssignmentSummary> =
    asSequence()
        .filterNot { it.status == AssignmentStatus.DRAFT }
        .sortedWith(
            compareBy<AssignmentSummary> { it.status.sortOrder }
                .thenBy { dueSortValue(it.dueAt) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title },
        )
        .toList()

private fun dueSortValue(value: String?): Long {
    if (value.isNullOrBlank()) return Long.MAX_VALUE
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching {
            LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        }
        .getOrDefault(Long.MAX_VALUE)
}
