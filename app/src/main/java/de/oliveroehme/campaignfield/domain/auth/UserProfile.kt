package de.oliveroehme.campaignfield.domain.auth

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
