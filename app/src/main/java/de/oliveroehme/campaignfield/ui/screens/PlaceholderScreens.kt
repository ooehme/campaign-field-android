package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

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
private fun PlaceholderScreen(
    title: String,
    message: String,
    contentPadding: PaddingValues,
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
    }
}
