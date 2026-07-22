package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentStatusAction
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.SyncQueueItem
import de.oliveroehme.campaignfield.domain.SyncEventKind
import de.oliveroehme.campaignfield.domain.SyncQueueStatus
import de.oliveroehme.campaignfield.domain.availableStatusActions
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailUiState
import de.oliveroehme.campaignfield.ui.assignment.AssignmentListUiState
import de.oliveroehme.campaignfield.ui.sync.SyncUiState
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEmptyState
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIconButton
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldMessagePanel
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.theme.FieldAmber
import de.oliveroehme.campaignfield.ui.theme.FieldBlackOverlay
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldGreen
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldPanel
import de.oliveroehme.campaignfield.ui.theme.FieldRed
import de.oliveroehme.campaignfield.ui.theme.FieldWhite
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun AssignmentsScreen(
    contentPadding: PaddingValues,
    state: AssignmentListUiState,
    syncState: SyncUiState,
    onRefresh: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenAssignment: (String) -> Unit,
    onChangeStatus: (AssignmentSummary, AssignmentStatus) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    FieldEyebrow("Einsatz")
                    Text(
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                        text = "Aufträge",
                        color = FieldWhite,
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                SyncStatusIcon(syncState)
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                items(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(FieldShape)
                            .background(FieldPanel)
                            .border(1.dp, FieldBorder, FieldShape),
                    )
                }
            }
            state.errorMessage != null && state.items.isEmpty() -> item {
                AssignmentError(message = state.errorMessage, onRetry = onRefresh)
            }
            state.items.isEmpty() -> item {
                FieldEmptyState(
                    title = "Keine Aufträge",
                    message = "Für dein Team sind aktuell keine Assignments verfügbar.",
                )
            }
            else -> {
                if (state.isUsingCachedData) {
                    item {
                        FieldMessagePanel(
                            message = buildString {
                                append("Offline-Daten")
                                state.cachedAtEpochMillis?.let { append(" vom ${formatEpochTime(it)}") }
                                append(" werden angezeigt. Änderungen bleiben lokal, bis die Verbindung wieder verfügbar ist.")
                            },
                            tint = FieldAmber,
                        )
                    }
                }
                state.errorMessage?.let { message ->
                    item { FieldMessagePanel(message = message, tint = FieldRed) }
                }
                items(state.items, key = AssignmentSummary::id) { assignment ->
                    val syncEvents = syncState.events.filter { it.assignmentId == assignment.id }
                    AssignmentCard(
                        assignment = assignment,
                        isOfflineReady = assignment.id in state.offlineReadyAssignmentIds,
                        syncEvents = syncEvents,
                        statusActions = if (assignment.id in state.teamLeadAssignmentIds) {
                            assignment.availableStatusActions()
                        } else emptyList(),
                        changingTargetStatus = if (
                            state.changingStatusAssignmentId == assignment.id
                        ) state.changingTargetStatus else null,
                        hasPendingStatusChange = syncEvents.any { event ->
                            event.kind == SyncEventKind.ASSIGNMENT_STATUS_UPDATE &&
                                (event.status == SyncQueueStatus.PENDING ||
                                    event.status == SyncQueueStatus.SYNCING)
                        },
                        statusError = state.statusErrors[assignment.id],
                        onChangeStatus = { status -> onChangeStatus(assignment, status) },
                        onOpenAssignment = onOpenAssignment,
                    )
                }
            }
        }

        item {
            FieldActionButton(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                text = "Sync prüfen",
                icon = FieldIcons.RefreshCcw,
                variant = FieldButtonVariant.Secondary,
                onClick = onOpenSync,
            )
        }
    }
}

@Composable
private fun AssignmentCard(
    assignment: AssignmentSummary,
    isOfflineReady: Boolean,
    syncEvents: List<SyncQueueItem>,
    statusActions: List<AssignmentStatusAction>,
    changingTargetStatus: AssignmentStatus?,
    hasPendingStatusChange: Boolean,
    statusError: String?,
    onChangeStatus: (AssignmentStatus) -> Unit,
    onOpenAssignment: (String) -> Unit,
) {
    val active = assignment.status == AssignmentStatus.ACTIVE
    FieldPanel(
        modifier = Modifier.fillMaxWidth(),
        borderColor = if (active) FieldAmber.copy(alpha = 0.70f) else FieldBorder,
        backgroundColor = if (active) FieldAmber.copy(alpha = 0.10f) else FieldPanel,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                FieldEyebrow(assignment.type.displayName, letterSpacing = androidx.compose.ui.unit.TextUnit(1.92f, androidx.compose.ui.unit.TextUnitType.Sp))
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = assignment.title,
                    color = FieldWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Spacer(Modifier.width(12.dp))
            FieldIconButton(
                modifier = Modifier.padding(top = 4.dp),
                icon = FieldIcons.ArrowRight,
                contentDescription = "Auftrag öffnen",
                onClick = { onOpenAssignment(assignment.id) },
            )
        }

        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FieldStatusPill(
                label = assignment.status.displayName,
                tone = assignment.status.tone(forceActiveWarning = active),
            )
            SmallStateIcon(
                FieldIcons.Database,
                if (isOfflineReady) FieldGreen else FieldAmber,
                if (isOfflineReady) {
                    "Auftrags- und Kartendaten offline verfügbar"
                } else {
                    "Auftrags- und Kartendaten werden vorbereitet"
                },
            )
            val syncVisual = assignmentSyncVisual(syncEvents)
            SmallStateIcon(FieldIcons.RefreshCcw, syncVisual.first, syncVisual.second)
        }

        Column(
            modifier = Modifier.padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssignmentMetaRow(
                icon = FieldIcons.MapPin,
                tint = FieldCyan,
                value = assignment.campaign?.name ?: "Keine Kampagne",
            )
            AssignmentMetaRow(
                icon = FieldIcons.UsersRound,
                tint = FieldGreen,
                value = assignment.team?.name ?: "Kein Team",
            )
            AssignmentMetaRow(
                icon = FieldIcons.CalendarClock,
                tint = FieldAmber,
                value = assignment.dueAt?.let(::formatDateTime) ?: "Keine Fälligkeit",
            )
        }

        if (statusActions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                statusActions.forEach { action ->
                    FieldActionButton(
                        modifier = Modifier.weight(1f),
                        text = action.label,
                        icon = statusActionIcon(action.targetStatus),
                        compact = statusActions.size > 2,
                        enabled = changingTargetStatus == null && !hasPendingStatusChange,
                        isLoading = changingTargetStatus == action.targetStatus,
                        variant = statusActionVariant(action.targetStatus),
                        onClick = { onChangeStatus(action.targetStatus) },
                    )
                }
            }
        }

        statusError?.let { message ->
            FieldMessagePanel(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                message = message,
                tint = FieldRed,
            )
        }
    }
}

private fun statusActionIcon(status: AssignmentStatus): androidx.compose.ui.graphics.vector.ImageVector =
    when (status) {
        AssignmentStatus.ACTIVE -> FieldIcons.CirclePlay
        AssignmentStatus.PAUSED -> FieldIcons.CirclePause
        AssignmentStatus.COMPLETED -> FieldIcons.CheckCheck
        AssignmentStatus.CANCELLED -> FieldIcons.Ban
        AssignmentStatus.READY -> FieldIcons.Flag
        else -> FieldIcons.ArrowRight
    }

private fun statusActionVariant(status: AssignmentStatus): FieldButtonVariant = when (status) {
    AssignmentStatus.CANCELLED -> FieldButtonVariant.Danger
    AssignmentStatus.COMPLETED -> FieldButtonVariant.Success
    AssignmentStatus.PAUSED -> FieldButtonVariant.Warning
    else -> FieldButtonVariant.Primary
}

@Composable
private fun SmallStateIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 28.dp, minHeight = 28.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(tint.copy(alpha = if (tint == FieldMuted) 0.05f else 0.10f))
            .border(1.dp, if (tint == FieldMuted) FieldBorder else tint.copy(alpha = 0.50f), androidx.compose.foundation.shape.CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(14.dp),
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
        )
    }
}

@Composable
private fun SyncStatusIcon(state: SyncUiState) {
    val (tint, label) = when {
        state.summary.failed > 0 -> FieldRed to "${state.summary.failed} Sync-Fehler"
        state.summary.syncing > 0 -> FieldCyan to "Sync läuft"
        state.summary.pending > 0 -> FieldAmber to "${state.summary.pending} Änderungen offen"
        else -> FieldGreen to "Sync bereit"
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(FieldShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.50f), FieldShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = FieldIcons.RefreshCcw,
            contentDescription = label,
            tint = tint,
        )
    }
}

@Composable
private fun AssignmentMetaRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    value: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(modifier = Modifier.size(16.dp), imageVector = icon, contentDescription = null, tint = tint)
        Text(
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            text = value,
            color = FieldMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
fun AssignmentDetailScreen(
    contentPadding: PaddingValues,
    state: AssignmentDetailUiState,
    syncState: SyncUiState,
    onRefresh: () -> Unit,
    onChangeStatus: (AssignmentStatus) -> Unit,
    onOpenMap: () -> Unit,
    onOpenProof: () -> Unit,
    onOpenSync: () -> Unit,
) {
    when {
        state.isLoading && state.assignment == null -> AssignmentDetailLoading(contentPadding)
        state.errorMessage != null && state.assignment == null -> Box(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        ) {
            AssignmentError(
                title = "Assignment konnte nicht geladen werden.",
                message = state.errorMessage,
                onRetry = onRefresh,
            )
        }
        state.assignment != null -> AssignmentDetailContent(
            contentPadding = contentPadding,
            detail = state.assignment,
            syncState = syncState,
            isRefreshing = state.isLoading,
            errorMessage = state.errorMessage,
            isUsingCachedData = state.isUsingCachedData,
            cachedAtEpochMillis = state.cachedAtEpochMillis,
            isChangingStatus = state.isChangingStatus,
            statusMessage = state.statusMessage,
            mapData = state.mapData,
            isMapDataLoading = state.isMapDataLoading,
            mapDataErrorMessage = state.mapDataErrorMessage,
            canChangeStatus = state.canChangeStatus,
            onChangeStatus = onChangeStatus,
            onOpenMap = onOpenMap,
            onOpenProof = onOpenProof,
            onOpenSync = onOpenSync,
        )
    }
}

@Composable
private fun AssignmentDetailContent(
    contentPadding: PaddingValues,
    detail: AssignmentDetail,
    syncState: SyncUiState,
    isRefreshing: Boolean,
    errorMessage: String?,
    isUsingCachedData: Boolean,
    cachedAtEpochMillis: Long?,
    isChangingStatus: Boolean,
    statusMessage: String?,
    mapData: AssignmentMapData?,
    isMapDataLoading: Boolean,
    mapDataErrorMessage: String?,
    canChangeStatus: Boolean,
    onChangeStatus: (AssignmentStatus) -> Unit,
    onOpenMap: () -> Unit,
    onOpenProof: () -> Unit,
    onOpenSync: () -> Unit,
) {
    val assignment = detail.summary
    val assignmentSyncEvents = syncState.events.filter { it.assignmentId == assignment.id }
    val hasPendingStatusChange = assignmentSyncEvents.any {
        it.status == SyncQueueStatus.PENDING || it.status == SyncQueueStatus.SYNCING
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                FieldEyebrow("Auftrag")
                Text(
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    text = assignment.title,
                    color = FieldWhite,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Spacer(Modifier.width(12.dp))
            FieldStatusPill(
                label = assignment.status.displayName,
                tone = assignment.status.tone(),
            )
        }

        if (isRefreshing) {
            FieldMessagePanel(
                message = "Auftragsdaten werden im Hintergrund aktualisiert …",
                tint = FieldCyan,
            )
        }
        if (isUsingCachedData) {
            FieldMessagePanel(
                message = buildString {
                    append("Gespeicherte Offline-Daten")
                    cachedAtEpochMillis?.let { append(" vom ${formatEpochTime(it)}") }
                    append(" werden angezeigt.")
                },
                tint = FieldAmber,
            )
        }
        errorMessage?.let { FieldMessagePanel(message = it, tint = FieldRed) }
        statusMessage?.let { FieldMessagePanel(message = it, tint = FieldGreen) }
        if (hasPendingStatusChange) {
            FieldMessagePanel(
                message = "Statusänderung lokal gespeichert – Synchronisierung steht noch aus.",
                tint = FieldAmber,
            )
        }

        FieldPanel(modifier = Modifier.fillMaxWidth()) {
            DetailRow("Typ", assignment.type.displayName)
            DetailRow("Kampagne", assignment.campaign?.name ?: "Keine Kampagne", Modifier.padding(top = 12.dp))
            DetailRow("Team", assignment.team?.name ?: "Kein Team", Modifier.padding(top = 12.dp))
            DetailRow("Zielgebiet", assignment.area?.name ?: "Kein Zielgebiet", Modifier.padding(top = 12.dp))
            DetailRow("Fällig", assignment.dueAt?.let(::formatDateTime) ?: "Keine Fälligkeit", Modifier.padding(top = 12.dp))
        }

        DetailPanel(title = "Briefing") {
            Text(
                text = detail.description?.takeIf(String::isNotBlank) ?: "Kein Briefing hinterlegt.",
                color = FieldWhite,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        DetailPanel(title = "Pflichtanweisungen") {
            if (detail.instructions.isEmpty()) {
                Text("Keine Pflichtanweisungen hinterlegt.", color = FieldMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.instructions.forEach { instruction ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FieldShape)
                                .background(FieldBlackOverlay.copy(alpha = 0.20f))
                                .border(1.dp, FieldBorder, FieldShape)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                text = instruction.value,
                                color = FieldWhite,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = "Karte",
                icon = FieldIcons.Map,
                variant = FieldButtonVariant.Secondary,
                onClick = onOpenMap,
            )
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = "Nachweis",
                icon = FieldIcons.FileText,
                enabled = detail.permissions.createProof,
                variant = FieldButtonVariant.Secondary,
                onClick = onOpenProof,
            )
        }

        AssignmentOperationalSummary(
            type = assignment.type,
            data = mapData,
            isLoading = isMapDataLoading,
            errorMessage = mapDataErrorMessage,
            onOpenMap = onOpenMap,
        )

        DetailPanel(title = "Status") {
            val actions = detail.availableStatusActions()
            if (actions.isNotEmpty() && !canChangeStatus) {
                Text(
                    text = "Nur die Teamleitung darf den Auftragsstatus ändern.",
                    color = FieldMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (actions.isEmpty()) {
                Text(
                    text = "Für diesen Status ist keine direkte Statusaktion verfügbar.",
                    color = FieldMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEach { action ->
                        FieldActionButton(
                            modifier = Modifier.fillMaxWidth(),
                            text = action.label,
                            enabled = !isChangingStatus && !hasPendingStatusChange,
                            isLoading = isChangingStatus,
                            variant = if (action.targetStatus == AssignmentStatus.CANCELLED) {
                                FieldButtonVariant.Danger
                            } else {
                                FieldButtonVariant.Primary
                            },
                            onClick = { onChangeStatus(action.targetStatus) },
                        )
                    }
                }
            }
        }

        FieldActionButton(
            text = "Synchronisieren",
            icon = FieldIcons.CheckCircle,
            variant = FieldButtonVariant.Secondary,
            onClick = onOpenSync,
        )
    }
}

@Composable
private fun AssignmentOperationalSummary(
    type: AssignmentType,
    data: AssignmentMapData?,
    isLoading: Boolean,
    errorMessage: String?,
    onOpenMap: () -> Unit,
) {
    val presentation = when (type) {
        AssignmentType.LETTERBOX_DISTRIBUTION ->
            Triple("Gebäude", data?.buildingCount ?: 0, "Briefkastenverteilung pro Adresse erfassen.")
        AssignmentType.POSTER_FREE,
        AssignmentType.POSTER_GUIDED,
        -> Triple("Poster", data?.posterCount ?: 0, "Posterstandorte auf der Karte bearbeiten.")
        else -> return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(FieldPanel)
            .border(1.dp, FieldBorder, FieldShape)
            .clickable(onClick = onOpenMap)
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(16.dp),
                imageVector = FieldIcons.ArrowRight,
                contentDescription = null,
                tint = FieldCyan,
            )
            FieldEyebrow(
                modifier = Modifier.weight(1f).padding(start = 8.dp),
                text = presentation.first,
                letterSpacing = androidx.compose.ui.unit.TextUnit(1.96f, androidx.compose.ui.unit.TextUnitType.Sp),
            )
            FieldStatusPill(
                label = when {
                    data != null -> presentation.second.toString()
                    isLoading -> "Lädt"
                    else -> "0"
                },
                tone = if (data == null && isLoading) {
                    FieldStatusTone.Neutral
                } else {
                    FieldStatusTone.Active
                },
            )
        }
        Text(
            modifier = Modifier.padding(top = 10.dp),
            text = presentation.third,
            color = FieldMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (isLoading && data != null) {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = "Kartendaten werden im Hintergrund aktualisiert …",
                color = FieldCyan,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        errorMessage?.let {
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = it,
                color = FieldAmber,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun DetailPanel(title: String, content: @Composable () -> Unit) {
    FieldPanel(modifier = Modifier.fillMaxWidth()) {
        FieldEyebrow(title, letterSpacing = androidx.compose.ui.unit.TextUnit(1.96f, androidx.compose.ui.unit.TextUnitType.Sp))
        Box(modifier = Modifier.padding(top = 12.dp)) { content() }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth()) {
        Text(modifier = Modifier.width(120.dp), text = label, color = FieldMuted, style = MaterialTheme.typography.bodyMedium)
        Text(modifier = Modifier.weight(1f), text = value, color = FieldWhite, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun AssignmentDetailLoading(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(padding = 20.dp) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.CenterHorizontally).size(28.dp),
                color = FieldCyan,
                strokeWidth = 2.dp,
            )
            Text(
                modifier = Modifier.padding(top = 12.dp),
                text = "Auftrag wird geladen",
                color = FieldMuted,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun AssignmentError(
    message: String,
    onRetry: () -> Unit,
    title: String = "Aufträge konnten nicht geladen werden.",
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(FieldRed.copy(alpha = 0.10f))
            .border(1.dp, FieldRed.copy(alpha = 0.50f), FieldShape)
            .padding(16.dp),
    ) {
        Text(title, color = FieldRed, style = MaterialTheme.typography.titleSmall)
        Text(modifier = Modifier.padding(top = 8.dp), text = message, color = FieldRed, style = MaterialTheme.typography.bodyMedium)
        FieldActionButton(
            modifier = Modifier.padding(top = 16.dp),
            text = "Erneut versuchen",
            variant = FieldButtonVariant.Danger,
            onClick = onRetry,
        )
    }
}

private fun assignmentSyncVisual(events: List<SyncQueueItem>): Pair<Color, String> = when {
    events.any { it.status == SyncQueueStatus.FAILED } -> FieldRed to "Sync-Fehler"
    events.any { it.status == SyncQueueStatus.SYNCING } -> FieldCyan to "Sync läuft"
    events.any { it.status == SyncQueueStatus.PENDING } -> FieldAmber to "Lokale Änderung offen"
    events.any { it.status == SyncQueueStatus.SYNCED } -> FieldGreen to "Gesynct"
    else -> FieldGreen to "Sync bereit"
}

private fun AssignmentStatus.tone(forceActiveWarning: Boolean = false): FieldStatusTone = when {
    this == AssignmentStatus.ACTIVE && forceActiveWarning -> FieldStatusTone.Warning
    this == AssignmentStatus.ACTIVE -> FieldStatusTone.Active
    this == AssignmentStatus.READY || this == AssignmentStatus.COMPLETED -> FieldStatusTone.Ready
    this == AssignmentStatus.PAUSED -> FieldStatusTone.Warning
    this == AssignmentStatus.CANCELLED -> FieldStatusTone.Danger
    else -> FieldStatusTone.Neutral
}

private fun formatDateTime(value: String): String {
    val instant = runCatching { Instant.parse(value) }
        .recoverCatching { OffsetDateTime.parse(value).toInstant() }
        .getOrNull()
    if (instant != null) {
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.GERMANY)
            .format(instant.atZone(ZoneId.systemDefault()))
    }
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    return date?.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMANY)) ?: value
}

private fun formatEpochTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(Locale.GERMANY)
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
