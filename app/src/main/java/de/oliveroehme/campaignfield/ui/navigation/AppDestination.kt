package de.oliveroehme.campaignfield.ui.navigation

sealed class AppDestination(
    val route: String,
    val label: String,
    val marker: String,
) {
    data object Assignments : AppDestination("assignments", "Aufträge", "A")
    data object Map : AppDestination("map", "Karte", "K")
    data object Sync : AppDestination("sync", "Sync", "S")
    data object Profile : AppDestination("profile", "Profil", "P")

    companion object {
        val shellItems = listOf(Assignments, Map, Sync, Profile)
    }
}
