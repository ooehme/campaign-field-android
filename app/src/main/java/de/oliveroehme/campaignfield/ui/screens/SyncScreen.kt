package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncQueueStatus
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEmptyState
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldMessagePanel
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.sync.SyncUiState
import de.oliveroehme.campaignfield.ui.theme.FieldBlackOverlay
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldRed
import de.oliveroehme.campaignfield.ui.theme.FieldWhite
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun SyncScreen(
    contentPadding: PaddingValues,
    state: SyncUiState,
    onSynchronize: () -> Unit,
    onRetry: (String) -> Unit,
) {
    val visibleEvents = state.events
        .filterNot { it.status == SyncQueueStatus.SYNCED }
        .sortedByDescending(SyncQueueItem::updatedAtEpochMillis)
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                FieldEyebrow("Offline")
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "Sync",
                    color = FieldWhite,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
        item {
            FieldPanel(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FieldStatusPill(
                        label = syncStatusLabel(state),
                        tone = syncStatusTone(state),
                    )
                    FieldActionButton(
                        text = "Sync starten",
                        icon = FieldIcons.RefreshCcw,
                        enabled = state.summary.unresolved > 0 && state.summary.syncing == 0,
                        isLoading = state.summary.syncing > 0,
                        onClick = onSynchronize,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SyncMetric(Modifier.weight(1f), "Offen", state.summary.unresolved)
                    SyncMetric(Modifier.weight(1f), "Fehler", state.summary.failed)
                    SyncMetric(Modifier.weight(1f), "Gesynct", state.summary.synced)
                }
            }
        }
        state.message?.let { message ->
            item { FieldMessagePanel(message = message, tint = FieldCyan) }
        }
        if (visibleEvents.isEmpty()) {
            item {
                FieldEmptyState(
                    title = "Keine lokalen Änderungen",
                    message = "Offline-Aktionen erscheinen hier, bis sie erfolgreich gesynct sind.",
                )
            }
        } else {
            items(visibleEvents, key = SyncQueueItem::id) { event ->
                SyncEventCard(
                    event = event,
                    isRetrying = state.retryingEventId == event.id,
                    onRetry = { onRetry(event.id) },
                )
            }
        }
    }
}

@Composable
private fun SyncEventCard(
    event: SyncQueueItem,
    isRetrying: Boolean,
    onRetry: () -> Unit,
) {
    FieldPanel(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                FieldEyebrow(event.kind.displayName)
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = "Auftrag #${event.assignmentId}",
                    color = FieldWhite,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            FieldStatusPill(
                label = event.status.displayName,
                tone = event.status.tone(),
            )
        }
        Text(
            modifier = Modifier.padding(top = 12.dp),
            text = when (event.kind) {
                SyncEventKind.ASSIGNMENT_STATUS_UPDATE ->
                    "Status: ${event.previousStatus.displayName} → ${event.targetStatus.displayName}"
                SyncEventKind.ASSIGNMENT_BUILDING_UPDATE ->
                    if (event.payloadJson != null) {
                        "Gebäudedaten #${event.subjectId} warten auf Synchronisation."
                    } else {
                        "Gebäude #${event.buildingId}: " +
                            "${event.previousBuildingStatus?.displayName} → " +
                            event.targetBuildingStatus?.displayName
                    }
                SyncEventKind.POSTER_LOCATION_CREATE ->
                    "Neuer Poster-Standort wartet auf Synchronisation."
                SyncEventKind.POSTER_LOCATION_UPDATE ->
                    "Poster #${event.subjectId} wartet auf Synchronisation."
                SyncEventKind.CAMPAIGN_BOOTH_LOCATION_UPDATE ->
                    "Aktionsstandort wartet auf Synchronisation."
            },
            color = FieldWhite,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            modifier = Modifier.padding(top = 6.dp),
            text = "Versuche: ${event.attempts} · Erstellt: ${formatSyncTime(event.createdAtEpochMillis)}",
            color = FieldMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        event.lastError?.takeIf(String::isNotBlank)?.let { error ->
            FieldMessagePanel(
                modifier = Modifier.padding(top = 12.dp),
                message = error,
                tint = if (event.status == SyncQueueStatus.FAILED) FieldRed else FieldCyan,
            )
        }
        if (event.status == SyncQueueStatus.FAILED) {
            FieldActionButton(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                text = "Erneut versuchen",
                icon = FieldIcons.RefreshCcw,
                isLoading = isRetrying,
                variant = FieldButtonVariant.Secondary,
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun SyncMetric(modifier: Modifier, label: String, value: Int) {
    Column(
        modifier = modifier
            .clip(FieldShape)
            .background(FieldBlackOverlay.copy(alpha = 0.20f))
            .border(1.dp, FieldBorder, FieldShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label.uppercase(), color = FieldMuted, style = MaterialTheme.typography.labelSmall)
        Text(
            modifier = Modifier.padding(top = 4.dp),
            text = value.toString(),
            color = FieldWhite,
            style = MaterialTheme.typography.titleLarge,
        )
    }
}

private fun syncStatusLabel(state: SyncUiState): String = when {
    state.summary.failed > 0 -> "${state.summary.failed} Sync-Fehler"
    state.summary.syncing > 0 -> "Sync läuft"
    state.summary.pending > 0 -> "${state.summary.pending} offen"
    else -> "Sync bereit"
}

private fun syncStatusTone(state: SyncUiState): FieldStatusTone = when {
    state.summary.failed > 0 -> FieldStatusTone.Danger
    state.summary.syncing > 0 -> FieldStatusTone.Active
    state.summary.pending > 0 -> FieldStatusTone.Warning
    else -> FieldStatusTone.Ready
}

private fun SyncQueueStatus.tone(): FieldStatusTone = when (this) {
    SyncQueueStatus.PENDING -> FieldStatusTone.Warning
    SyncQueueStatus.SYNCING -> FieldStatusTone.Active
    SyncQueueStatus.SYNCED -> FieldStatusTone.Ready
    SyncQueueStatus.FAILED -> FieldStatusTone.Danger
}

private fun formatSyncTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.GERMANY)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
