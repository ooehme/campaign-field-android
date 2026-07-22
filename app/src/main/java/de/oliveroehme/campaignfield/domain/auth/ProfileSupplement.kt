package de.oliveroehme.campaignfield.domain.auth

data class TeamDetail(
    val id: String,
    val name: String,
    val members: List<TeamMember> = emptyList(),
)

data class TeamMember(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: String? = null,
)

data class TeamInvitation(
    val id: String,
    val teamName: String,
    val role: String? = null,
    val status: String? = null,
)
