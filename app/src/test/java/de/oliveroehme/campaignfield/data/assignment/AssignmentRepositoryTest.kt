package de.oliveroehme.campaignfield.data.assignment

import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentPage
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.TeamSummary
import de.oliveroehme.campaignfield.domain.auth.TeamMembership
import de.oliveroehme.campaignfield.domain.auth.UserPermissions
import de.oliveroehme.campaignfield.domain.auth.UserProfile
import de.oliveroehme.campaignfield.network.assignment.AssignmentRemoteDataSource
import de.oliveroehme.campaignfield.network.assignment.AssignmentResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentRepositoryTest {
    @Test
    fun `filters drafts and applies reference ordering`() = runBlocking {
        val remote = FakeRemote(
            AssignmentPage(
                listOf(
                    assignment("draft", AssignmentStatus.DRAFT),
                    assignment("ready-late", AssignmentStatus.READY, "2026-10-01"),
                    assignment("paused", AssignmentStatus.PAUSED),
                    assignment("active-late", AssignmentStatus.ACTIVE, "2026-09-01"),
                    assignment("active-early", AssignmentStatus.ACTIVE, "2026-08-01"),
                    assignment("ready-early", AssignmentStatus.READY, "2026-08-01"),
                ),
            ),
        )
        val repository = DefaultAssignmentRepository(remote)

        val result = repository.loadAssignments(profile(canView = true)) as AssignmentResult.Success

        assertEquals(
            listOf("active-early", "active-late", "paused", "ready-early", "ready-late"),
            result.value.items.map { it.id },
        )
        assertEquals("42", remote.userId)
        assertEquals(listOf("team-1"), remote.teamIds)
    }

    @Test
    fun `does not call backend without view permission`() = runBlocking {
        val remote = FakeRemote(AssignmentPage(emptyList()))
        val result = DefaultAssignmentRepository(remote).loadAssignments(profile(canView = false))

        assertTrue(result is AssignmentResult.Failure)
        assertEquals(0, remote.calls)
    }

    private fun profile(canView: Boolean) = UserProfile(
        id = "42",
        name = "Field User",
        email = "field@example.test",
        teams = listOf(TeamMembership("team-1", "Nord")),
        permissions = UserPermissions(viewAssignments = canView),
    )

    private fun assignment(
        id: String,
        status: AssignmentStatus,
        dueAt: String? = null,
    ) = AssignmentSummary(
        id = id,
        title = id,
        type = AssignmentType.STANDARD,
        status = status,
        dueAt = dueAt,
        team = TeamSummary("team-1", "Nord"),
    )

    private class FakeRemote(private val page: AssignmentPage) : AssignmentRemoteDataSource {
        var calls = 0
        var userId: String? = null
        var teamIds: List<String> = emptyList()

        override suspend fun loadAssignments(
            userId: String?,
            teamIds: List<String>,
        ): AssignmentResult<AssignmentPage> {
            calls++
            this.userId = userId
            this.teamIds = teamIds
            return AssignmentResult.Success(page)
        }

        override suspend fun loadAssignment(id: String): AssignmentResult<AssignmentDetail> =
            error("not used")
    }
}
