package de.oliveroehme.campaignfield.ui.navigation

import android.net.Uri

sealed class AppDestination(
    val route: String,
    val label: String,
    val marker: String,
) {
    data object Assignments : AppDestination("assignments", "Aufträge", "A")
    data object Map : AppDestination("map", "Karte", "K")
    data object Sync : AppDestination("sync", "Sync", "S")
    data object Profile : AppDestination("profile", "Profil", "P")
    data object AssignmentDetail : AppDestination("assignments/{assignmentId}", "Auftrag", "A")

    companion object {
        val shellItems: List<AppDestination>
            get() = listOf(Assignments, Map, Sync, Profile)

        fun assignmentDetailRoute(id: String): String = "assignments/${Uri.encode(id)}"
    }
}
