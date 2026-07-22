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

@Composable
fun SyncScreen(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column {
            FieldEyebrow("Offline")
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "Sync",
                color = FieldWhite,
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        FieldPanel(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier.size(18.dp),
                        imageVector = FieldIcons.RefreshCcw,
                        contentDescription = null,
                        tint = FieldGreen,
                    )
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "Sync bereit",
                        color = FieldGreen,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(FieldShape)
                        .background(FieldCyan.copy(alpha = 0.15f))
                        .border(1.dp, FieldCyan, FieldShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        modifier = Modifier.size(16.dp),
                        imageVector = FieldIcons.RefreshCcw,
                        contentDescription = "Sync starten",
                        tint = FieldCyan.copy(alpha = 0.55f),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SyncMetric(modifier = Modifier.weight(1f), label = "Offen", value = "0")
                SyncMetric(modifier = Modifier.weight(1f), label = "Fehler", value = "0")
                SyncMetric(modifier = Modifier.weight(1f), label = "Gesynct", value = "0")
            }
        }

        FieldEmptyState(
            title = "Keine lokalen Änderungen",
            message = "Offline-Aktionen erscheinen hier, bis sie erfolgreich gesynct sind.",
        )
    }
}

@Composable
private fun SyncMetric(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(FieldShape)
            .background(FieldBlackOverlay.copy(alpha = 0.20f))
            .border(1.dp, FieldBorder, FieldShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(text = label.uppercase(), color = FieldMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            text = value,
            color = FieldWhite,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}
