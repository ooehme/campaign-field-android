package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.oliveroehme.campaignfield.domain.AssignmentDetail
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentSummary
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailUiState
import de.oliveroehme.campaignfield.ui.assignment.AssignmentListUiState
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
    onRefresh: () -> Unit,
    onSelectTeam: (String?) -> Unit,
    onOpenAssignment: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f).semantics { heading() },
                text = "Aufträge",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = onRefresh, enabled = !state.isLoading) { Text("Neu laden") }
        }

        if (state.teamFilters.size > 1) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(
                        selected = state.selectedTeamId == null,
                        onClick = { onSelectTeam(null) },
                        label = { Text("Alle Teams") },
                    )
                }
                items(state.teamFilters, key = { it.id.orEmpty() }) { team ->
                    FilterChip(
                        selected = state.selectedTeamId == team.id,
                        onClick = { onSelectTeam(team.id) },
                        label = { Text(team.name) },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        if (state.isLoading && state.items.isNotEmpty()) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        when {
            state.isLoading && state.items.isEmpty() -> LoadingAssignments()
            state.errorMessage != null && state.items.isEmpty() -> AssignmentError(
                message = state.errorMessage,
                onRetry = onRefresh,
            )
            state.visibleItems.isEmpty() -> EmptyAssignments(
                filtered = state.selectedTeamId != null,
                onRefresh = onRefresh,
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.errorMessage?.let { message ->
                    item {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                items(state.visibleItems, key = AssignmentSummary::id) { assignment ->
                    AssignmentCard(assignment, onOpenAssignment)
                }
            }
        }
    }
}

@Composable
private fun AssignmentCard(
    assignment: AssignmentSummary,
    onOpenAssignment: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { onOpenAssignment(assignment.id) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    modifier = Modifier.weight(1f),
                    text = assignment.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.width(12.dp))
                StatusPill(assignment.status)
            }
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = assignment.type.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            assignment.team?.let {
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = it.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = assignment.dueAt?.let { "Fällig: ${formatDate(it)}" } ?: "Keine Fälligkeit angegeben",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun AssignmentDetailScreen(
    contentPadding: PaddingValues,
    state: AssignmentDetailUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack) { Text("← Zurück") }
        when {
            state.isLoading && state.assignment == null -> LoadingAssignments()
            state.errorMessage != null && state.assignment == null -> AssignmentError(
                message = state.errorMessage,
                onRetry = onRefresh,
            )
            state.assignment != null -> AssignmentDetailContent(
                detail = state.assignment,
                isRefreshing = state.isLoading,
                errorMessage = state.errorMessage,
                onRefresh = onRefresh,
                onOpenMap = onOpenMap,
            )
        }
    }
}

@Composable
private fun AssignmentDetailContent(
    detail: AssignmentDetail,
    isRefreshing: Boolean,
    errorMessage: String?,
    onRefresh: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val assignment = detail.summary
    Row(verticalAlignment = Alignment.Top) {
        Text(
            modifier = Modifier.weight(1f).semantics { heading() },
            text = assignment.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        StatusPill(assignment.status)
    }
    Text(
        modifier = Modifier.padding(top = 8.dp),
        text = assignment.type.displayName,
        style = MaterialTheme.typography.titleMedium,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(
            enabled = !isRefreshing,
            onClick = onRefresh,
        ) { Text("Neu laden") }
    }
    if (isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
    errorMessage?.let {
        Text(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            text = it,
            color = MaterialTheme.colorScheme.error,
        )
    }

    DetailSection("Briefing") {
        Text(detail.description ?: "Kein Briefing hinterlegt.")
    }
    DetailSection("Rahmendaten") {
        MetadataRow("Status", assignment.status.displayName)
        MetadataRow("Start", assignment.startsAt?.let(::formatDate) ?: "Nicht angegeben")
        MetadataRow("Fälligkeit", assignment.dueAt?.let(::formatDate) ?: "Nicht angegeben")
        assignment.campaign?.let { MetadataRow("Kampagne", it.name) }
        assignment.team?.let { MetadataRow("Team", it.name) }
        assignment.area?.let { MetadataRow("Zielgebiet", it.name) }
    }
    DetailSection("Anweisungen") {
        if (detail.instructions.isEmpty()) {
            Text("Keine zusätzlichen Anweisungen hinterlegt.")
        } else {
            detail.instructions.forEachIndexed { index, instruction ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text(instruction.label, fontWeight = FontWeight.SemiBold)
                Text(text = instruction.value, modifier = Modifier.padding(top = 3.dp))
            }
        }
    }
    DetailSection("Weiterarbeiten") {
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onOpenMap) {
            Text("Zielgebiet auf Karte öffnen")
        }
        if (assignment.type in BUILDING_TYPES) {
            FutureTarget("Gebäude", "Verfügbar ab Phase 6")
        }
        if (assignment.type in POSTER_TYPES) {
            FutureTarget("Posterstandorte", "Verfügbar ab Phase 6")
        }
        if (assignment.type == AssignmentType.CAMPAIGN_BOOTH) {
            FutureTarget("Aktionsstand", "Verfügbar ab Phase 6")
        }
        FutureTarget("Nachweise", "Verfügbar ab Phase 8")
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Text(
        modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        text = title,
        style = MaterialTheme.typography.titleMedium,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            modifier = Modifier.weight(0.38f),
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(modifier = Modifier.weight(0.62f), text = value)
    }
}

@Composable
private fun FutureTarget(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun StatusPill(status: AssignmentStatus) {
    val color = when (status) {
        AssignmentStatus.ACTIVE -> MaterialTheme.colorScheme.primaryContainer
        AssignmentStatus.PAUSED, AssignmentStatus.READY -> MaterialTheme.colorScheme.tertiaryContainer
        AssignmentStatus.CANCELLED, AssignmentStatus.UNKNOWN -> MaterialTheme.colorScheme.errorContainer
        AssignmentStatus.COMPLETED, AssignmentStatus.DRAFT -> MaterialTheme.colorScheme.secondaryContainer
    }
    Surface(color = color, shape = MaterialTheme.shapes.small) {
        Text(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            text = status.displayName,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun LoadingAssignments() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AssignmentError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
        Button(modifier = Modifier.padding(top = 16.dp), onClick = onRetry) { Text("Erneut versuchen") }
    }
}

@Composable
private fun EmptyAssignments(filtered: Boolean, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(if (filtered) "Keine Aufträge für dieses Team." else "Keine operativen Aufträge vorhanden.")
        if (!filtered) {
            OutlinedButton(modifier = Modifier.padding(top = 16.dp), onClick = onRefresh) {
                Text("Neu laden")
            }
        }
    }
}

private fun formatDate(value: String): String {
    val date = runCatching { Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate() }
        .recoverCatching { OffsetDateTime.parse(value).toLocalDate() }
        .recoverCatching { LocalDate.parse(value) }
        .getOrNull()
    return date?.format(DATE_FORMATTER) ?: value
}

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.GERMANY)
private val BUILDING_TYPES = setOf(AssignmentType.STANDARD, AssignmentType.LETTERBOX_DISTRIBUTION)
private val POSTER_TYPES = setOf(AssignmentType.POSTER_FREE, AssignmentType.POSTER_GUIDED)
