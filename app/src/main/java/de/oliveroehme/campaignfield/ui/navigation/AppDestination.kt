package de.oliveroehme.campaignfield.ui.navigation

import android.net.Uri
import androidx.compose.ui.graphics.vector.ImageVector
import de.oliveroehme.campaignfield.ui.components.FieldIcons

sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    data object Assignments : AppDestination("assignments", "Aufträge", FieldIcons.ClipboardList)
    data object Map : AppDestination("scanner", "Scanner", FieldIcons.Radar)
    data object Sync : AppDestination("sync", "Sync", FieldIcons.RefreshCcw)
    data object Profile : AppDestination("profile", "Profil", FieldIcons.UserRound)
    data object AssignmentDetail : AppDestination("assignments/{assignmentId}", "Auftrag", FieldIcons.ClipboardList)
    data object AssignmentMap : AppDestination("assignments/{assignmentId}/map", "Karte", FieldIcons.Map)
    data object AssignmentProof : AppDestination("assignments/{assignmentId}/proof", "Nachweis", FieldIcons.FileText)

    companion object {
        val shellItems: List<AppDestination>
            get() = listOf(Assignments, Map, Sync, Profile)

        fun assignmentDetailRoute(id: String): String = "assignments/${Uri.encode(id)}"
        fun assignmentMapRoute(id: String): String = "assignments/${Uri.encode(id)}/map"
        fun assignmentProofRoute(id: String): String = "assignments/${Uri.encode(id)}/proof"
    }
}
