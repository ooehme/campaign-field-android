package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(contentPadding: PaddingValues, onContinue: () -> Unit) {
    PlaceholderScreen(
        title = "Login",
        message = "Sanctum-Cookie-Authentifizierung folgt in Roadmap-Phase 2 und 3.",
        contentPadding = contentPadding,
        action = {
            Button(onClick = onContinue) {
                Text("App-Shell öffnen")
            }
        },
    )
}

@Composable
fun AssignmentsScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Aufträge",
    message = "Assignment-Liste und Details folgen in Roadmap-Phase 4.",
    contentPadding = contentPadding,
)

@Composable
fun MapScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Karte",
    message = "MapLibre und LocationManager folgen in Roadmap-Phase 5.",
    contentPadding = contentPadding,
)

@Composable
fun SyncScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Sync",
    message = "Room-Cache, Queue und WorkManager folgen in Roadmap-Phase 7.",
    contentPadding = contentPadding,
)

@Composable
fun ProfileScreen(contentPadding: PaddingValues) = PlaceholderScreen(
    title = "Profil",
    message = "Session, Benutzerprofil und Logout folgen in Roadmap-Phase 3.",
    contentPadding = contentPadding,
)

@Composable
private fun PlaceholderScreen(
    title: String,
    message: String,
    contentPadding: PaddingValues,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        action?.invoke()
    }
}
