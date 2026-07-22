package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldWhite

@Composable
fun ProofScreen(
    contentPadding: PaddingValues,
    assignmentId: String,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            FieldEyebrow("Nachweis")
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "Auftrag #$assignmentId",
                color = FieldWhite,
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        FieldPanel(modifier = Modifier.fillMaxWidth()) {
            FieldStatusPill(label = "Lokal geplant", tone = FieldStatusTone.Warning)
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "Foto, Notiz und lokaler Sync folgen in einer späteren Ausbaustufe.",
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
