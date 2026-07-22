package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.data.Repository
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.domain.forOperativeOverview
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult

interface AssignmentRepository : Repository {
    suspend fun loadAssignments(profile: UserProfile): AssignmentResult<AssignmentPage>
    suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail>
}

class DefaultAssignmentRepository(
    private val remote: AssignmentRemoteDataSource,
) : AssignmentRepository {
    override suspend fun loadAssignments(profile: UserProfile): AssignmentResult<AssignmentPage> {
        return when (
            val result = remote.loadAssignments(
                userId = profile.id,
                teamIds = profile.teams.mapNotNull { it.teamId },
            )
        ) {
            is AssignmentResult.Success -> {
                val teamsById = profile.teams
                    .mapNotNull { membership ->
                        membership.teamId?.let { teamId -> teamId to membership.teamName }
                    }
                    .toMap()
                val items = result.value.items
                    .map { assignment ->
                        val currentTeam = assignment.team
                        val teamId = currentTeam?.id
                        val teamName = teamId?.let(teamsById::get)
                        if (currentTeam != null && teamName != null && currentTeam.name.startsWith("Team #")) {
                            assignment.copy(team = currentTeam.copy(name = teamName))
                        } else {
                            assignment
                        }
                    }
                    .filter { assignment ->
                        teamsById.isEmpty() ||
                            assignment.team?.id?.let(teamsById::containsKey) == true
                    }
                    .forOperativeOverview()
                AssignmentResult.Success(result.value.copy(items = items))
            }
            is AssignmentResult.Failure -> result
        }
    }

    override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
        remote.loadAssignment(id)
}
