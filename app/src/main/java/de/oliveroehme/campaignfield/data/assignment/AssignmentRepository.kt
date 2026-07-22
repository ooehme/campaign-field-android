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
            is AssignmentResult.Success -> AssignmentResult.Success(
                result.value.copy(items = result.value.items.forOperativeOverview()),
            )
            is AssignmentResult.Failure -> result
        }
    }

    override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
        remote.loadAssignment(id)
}
