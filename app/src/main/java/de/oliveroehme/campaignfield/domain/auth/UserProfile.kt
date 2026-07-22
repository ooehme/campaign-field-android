package de.oliveroehme.campaignfield.domain.auth

import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String?,
    val name: String,
    val email: String,
    val appRole: String? = null,
    val roles: List<String> = emptyList(),
    val teams: List<TeamMembership> = emptyList(),
    val permissions: UserPermissions = UserPermissions(),
)

@Serializable
data class TeamMembership(
    val teamId: String?,
    val teamName: String,
    val role: String? = null,
    val isCurrent: Boolean = false,
)

@Serializable
data class UserPermissions(
    val viewAssignments: Boolean = false,
)

fun UserProfile.leadsAssignmentTeam(assignment: AssignmentDetail): Boolean {
    return leadsAssignmentTeam(assignment.summary)
}

fun UserProfile.leadsAssignmentTeam(assignment: AssignmentSummary): Boolean {
    val teamId = assignment.team?.id ?: return false
    return teams.any { membership ->
        membership.teamId == teamId && membership.role.isTeamLeadRole()
    }
}

private fun String?.isTeamLeadRole(): Boolean = when (
    this?.trim()?.lowercase()?.replace(Regex("[-\\s]+"), "_")
) {
    "lead", "leader", "teamlead", "team_lead", "team_leader" -> true
    else -> false
}
