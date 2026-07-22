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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.oliveroehme.campaignfield.location.CompassSource
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.location.LocationSource
import de.oliveroehme.campaignfield.map.FieldGeoJson
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.map.MapCoordinate
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailUiState
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.map.BasemapState
import de.oliveroehme.campaignfield.ui.map.MapLibreMapView
import de.oliveroehme.campaignfield.ui.status.LocationAccessState
import de.oliveroehme.campaignfield.ui.theme.FieldAmber
import de.oliveroehme.campaignfield.ui.theme.FieldBorder
import de.oliveroehme.campaignfield.ui.theme.FieldCyan
import de.oliveroehme.campaignfield.ui.theme.FieldGreen
import de.oliveroehme.campaignfield.ui.theme.FieldMuted
import de.oliveroehme.campaignfield.ui.theme.FieldPanel as FieldPanelColor
import de.oliveroehme.campaignfield.ui.theme.FieldRed
import de.oliveroehme.campaignfield.ui.theme.FieldWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch

@Composable
fun MapScreen(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
    assignmentState: AssignmentDetailUiState?,
    onRefreshAssignment: () -> Unit,
    configuration: MapConfiguration,
    locationAccessState: LocationAccessState,
    locationSessionState: InMemoryLocationSessionState,
    locationSource: LocationSource,
    compassSource: CompassSource,
    networkStateProvider: NetworkStateProvider,
) {
    val lastLocation by locationSessionState.lastLocation.collectAsStateWithLifecycle()
    val isOnline by produceState(networkStateProvider.isOnline(), networkStateProvider) {
        while (true) {
            value = networkStateProvider.isOnline()
            delay(NETWORK_REFRESH_INTERVAL_MS)
        }
    }
    var liveTracking by remember { mutableStateOf(false) }
    var startLiveWhenPositionArrives by remember { mutableStateOf(false) }
    var compassEnabled by remember { mutableStateOf(false) }
    var bearing by remember { mutableStateOf<Double?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var compassError by remember { mutableStateOf<String?>(null) }
    var basemapState by remember { mutableStateOf(BasemapState.LOADING) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(lastLocation, startLiveWhenPositionArrives) {
        if (lastLocation != null && startLiveWhenPositionArrives) {
            startLiveWhenPositionArrives = false
            liveTracking = true
        }
    }
    LaunchedEffect(liveTracking, locationSource) {
        if (!liveTracking) return@LaunchedEffect
        locationError = null
        locationSource.locations
            .catch {
                locationError = "Live-Standort konnte nicht gestartet werden. Die Karte bleibt nutzbar."
                liveTracking = false
            }
            .collect { sample ->
                if (sample.accuracyMeters == null || sample.accuracyMeters <= MAX_USABLE_ACCURACY_METERS) {
                    locationSessionState.update(sample)
                } else {
                    locationError = "GPS-Genauigkeit ist schlechter als 500 m."
                }
            }
    }
    LaunchedEffect(compassEnabled, compassSource) {
        if (!compassEnabled) {
            bearing = null
            return@LaunchedEffect
        }
        compassError = null
        compassSource.bearings
            .catch {
                compassError = "Kompass ist nicht verfügbar. Die Karte bleibt nach Norden ausgerichtet."
                compassEnabled = false
            }
            .collect { bearing = it }
    }

    val detail = assignmentState?.assignment
    if (assignmentState != null && detail == null) {
        MapAssignmentGate(
            contentPadding,
            includeStatusBarInset,
            assignmentState.isLoading,
            assignmentState.errorMessage,
            onRefreshAssignment,
        )
        return
    }
    val area = detail?.summary?.area
    val geometryPositions = remember(area?.geoJson) { FieldGeoJson.positions(area?.geoJson) }
    val areaCenter = remember(area) {
        val latitude = area?.centerLatitude
        val longitude = area?.centerLongitude
        if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            MapCoordinate(latitude, longitude)
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(
                if (includeStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                else Modifier,
            )
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            FieldEyebrow(if (detail == null) "Scanner" else "Assignment-Karte")
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = detail?.summary?.title ?: "Operative Karte",
                color = FieldWhite,
                style = MaterialTheme.typography.titleLarge,
            )
        }
        when {
            detail == null -> MapNotice("Öffne einen Auftrag, um dessen Zielgebiet zu sehen.", FieldMuted)
            geometryPositions.isEmpty() -> MapNotice(
                "Keine Kartengeometrie im Auftrag gefunden. Standort und Karte bleiben nutzbar.",
                FieldAmber,
            )
        }
        assignmentState?.errorMessage?.let { MapNotice(it, FieldRed) }
        if (!isOnline) {
            MapNotice("Kein Netz – Zielgebiet und Standort werden ohne Basiskarte angezeigt.", FieldAmber)
        } else if (basemapState == BasemapState.UNAVAILABLE) {
            MapNotice("Basiskarte konnte nicht geladen werden.", FieldAmber)
        }
        locationError?.let { MapNotice(it, FieldRed) }
        compassError?.let { MapNotice(it, FieldAmber) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(FieldShape)
                .background(FieldPanelColor)
                .border(1.dp, FieldBorder, FieldShape),
        ) {
            MapLibreMapView(
                configuration = configuration,
                areaGeoJson = area?.geoJson,
                areaCenter = areaCenter,
                currentLocation = lastLocation,
                bearing = bearing,
                followLocation = liveTracking,
                isOnline = isOnline,
                reloadKey = reloadKey,
                onBasemapStateChanged = { basemapState = it },
            )
            if (basemapState == BasemapState.LOADING) {
                Text(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                        .clip(FieldShape)
                        .background(FieldPanelColor.copy(alpha = 0.90f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = "Karte wird geladen …",
                    color = FieldCyan,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(FieldPanelColor.copy(alpha = 0.82f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                text = "© OpenFreeMap · © OpenStreetMap",
                color = FieldMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = "Standort",
                icon = FieldIcons.MapPin,
                isLoading = locationAccessState.isRequesting,
                variant = FieldButtonVariant.Secondary,
                onClick = {
                    locationError = null
                    locationAccessState.requestPosition()
                },
            )
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = if (liveTracking) "Live aus" else "Live an",
                icon = FieldIcons.Radar,
                variant = if (liveTracking) FieldButtonVariant.Danger else FieldButtonVariant.Secondary,
                onClick = {
                    if (liveTracking) liveTracking = false
                    else if (lastLocation == null) {
                        startLiveWhenPositionArrives = true
                        locationAccessState.requestPosition()
                    } else liveTracking = true
                },
            )
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = if (compassEnabled) "Nord" else "Kompass",
                icon = FieldIcons.RefreshCcw,
                enabled = compassSource.isAvailable,
                variant = FieldButtonVariant.Secondary,
                onClick = { compassEnabled = !compassEnabled },
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = when {
                    liveTracking -> "Live-Standort aktiv"
                    lastLocation != null -> "Standort erfasst"
                    else -> locationAccessState.statusLabel
                },
                color = if (lastLocation != null) FieldGreen else FieldMuted,
                style = MaterialTheme.typography.labelMedium,
            )
            if (isOnline && basemapState == BasemapState.UNAVAILABLE) {
                FieldActionButton(
                    text = "Neu laden",
                    variant = FieldButtonVariant.Secondary,
                    onClick = { reloadKey++ },
                )
            }
        }
    }
}

@Composable
private fun MapAssignmentGate(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
    isLoading: Boolean,
    message: String?,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .then(
                if (includeStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars)
                else Modifier,
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(Modifier.fillMaxWidth()) {
            Text(
                text = if (isLoading) "Auftrag und Kartengeometrie werden geladen …"
                else message ?: "Auftrag konnte nicht geladen werden.",
                color = if (isLoading) FieldCyan else FieldRed,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isLoading) {
                FieldActionButton(
                    modifier = Modifier.padding(top = 12.dp),
                    text = "Erneut versuchen",
                    variant = FieldButtonVariant.Secondary,
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun MapNotice(message: String, tint: androidx.compose.ui.graphics.Color) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.45f), FieldShape)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        text = message,
        color = tint,
        style = MaterialTheme.typography.bodySmall,
    )
}

private const val MAX_USABLE_ACCURACY_METERS = 500f
private const val NETWORK_REFRESH_INTERVAL_MS = 2_000L
