package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEmptyState
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.theme.FieldBlackOverlay
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldGreen
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldWhite

@Composable
fun MapScreen(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(
                if (includeStatusBarInset) {
                    Modifier.windowInsetsPadding(WindowInsets.statusBars)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(
            modifier = Modifier.fillMaxWidth(),
            padding = 16.dp,
        ) {
            Icon(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(36.dp),
                imageVector = FieldIcons.Radar,
                contentDescription = null,
                tint = FieldCyan,
            )
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp),
                text = "Scanner wird vorbereitet",
                color = FieldWhite,
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                text = "MapLibre und Standortfunktionen folgen in der nächsten Ausbaustufe.",
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

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
            FieldStatusPill(
                label = "Lokal geplant",
                tone = FieldStatusTone.Warning,
            )
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "Foto, Notiz und lokaler Sync folgen in einer späteren Ausbaustufe.",
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
