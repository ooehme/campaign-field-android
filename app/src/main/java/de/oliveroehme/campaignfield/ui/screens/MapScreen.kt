package de.oliveroehme.campaignfield.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.oliveroehme.campaignfield.domain.AssignmentStatus
import de.oliveroehme.campaignfield.domain.AssignmentMapData
import de.oliveroehme.campaignfield.domain.AssignmentMapFeature
import de.oliveroehme.campaignfield.domain.AssignmentMapFeatureKind
import de.oliveroehme.campaignfield.domain.AssignmentLocationInput
import de.oliveroehme.campaignfield.domain.AssignmentType
import de.oliveroehme.campaignfield.domain.BuildingStatus
import de.oliveroehme.campaignfield.domain.toGeoJson
import de.oliveroehme.campaignfield.location.CompassSource
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState
import de.oliveroehme.campaignfield.location.LocationSource
import de.oliveroehme.campaignfield.map.FieldGeoJson
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.map.MapCoordinate
import de.oliveroehme.campaignfield.network.NetworkStateProvider
import de.oliveroehme.campaignfield.ui.assignment.AssignmentDetailUiState
import de.oliveroehme.campaignfield.ui.assignment.ScannerUiState
import de.oliveroehme.campaignfield.ui.components.FieldActionButton
import de.oliveroehme.campaignfield.ui.components.FieldButtonVariant
import de.oliveroehme.campaignfield.ui.components.FieldEyebrow
import de.oliveroehme.campaignfield.ui.components.FieldIcons
import de.oliveroehme.campaignfield.ui.components.FieldPanel
import de.oliveroehme.campaignfield.ui.components.FieldShape
import de.oliveroehme.campaignfield.ui.components.FieldStatusPill
import de.oliveroehme.campaignfield.ui.components.FieldStatusTone
import de.oliveroehme.campaignfield.ui.map.BasemapState
import de.oliveroehme.campaignfield.ui.map.FieldMapMode
import de.oliveroehme.campaignfield.ui.map.MapLibreMapView
import de.oliveroehme.campaignfield.ui.map.MapZoomRequest
import de.oliveroehme.campaignfield.ui.map.ScannerMapTheme
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
    assignmentState: AssignmentDetailUiState?,
    scannerState: ScannerUiState?,
    userLabel: String?,
    onRefreshAssignment: () -> Unit,
    onOpenAssignmentDetails: () -> Unit,
    onOpenProof: () -> Unit,
    onChangeBuildingStatus: (AssignmentMapFeature, BuildingStatus) -> Unit = { _, _ -> },
    onChangeScannerBuildingStatus: (String, AssignmentMapFeature, BuildingStatus) -> Unit =
        { _, _, _ -> },
    onCreatePosterLocation: (AssignmentLocationInput) -> Unit = {},
    onCreateCampaignBoothLocation: (AssignmentLocationInput) -> Unit = {},
    onUpdateMapFeature: (AssignmentMapFeature, AssignmentLocationInput) -> Unit = { _, _ -> },
    onDeleteMapFeature: (AssignmentMapFeature) -> Unit = {},
    configuration: MapConfiguration,
    locationAccessState: LocationAccessState,
    locationSessionState: InMemoryLocationSessionState,
    locationSource: LocationSource,
    compassSource: CompassSource,
    networkStateProvider: NetworkStateProvider,
) {
    val isScanner = assignmentState == null
    val lastLocation by locationSessionState.lastLocation.collectAsStateWithLifecycle()
    val isOnline by produceState(networkStateProvider.isOnline(), networkStateProvider) {
        while (true) {
            value = networkStateProvider.isOnline()
            delay(NETWORK_REFRESH_INTERVAL_MS)
        }
    }
    var liveTracking by remember(isScanner) { mutableStateOf(false) }
    var startLiveWhenPositionArrives by remember { mutableStateOf(false) }
    var bearing by remember { mutableStateOf<Double?>(null) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var compassError by remember { mutableStateOf<String?>(null) }
    var basemapState by remember { mutableStateOf(BasemapState.LOADING) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var zoomRequest by remember { mutableStateOf<MapZoomRequest?>(null) }
    var zoomRequestId by remember { mutableIntStateOf(0) }
    var showActions by remember { mutableStateOf(false) }
    var captureKind by remember { mutableStateOf<LocationCaptureKind?>(null) }
    var pendingCapture by remember { mutableStateOf<PendingLocationCapture?>(null) }
    var captureError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isScanner, locationAccessState.isPermissionGranted, locationAccessState.isLocationEnabled) {
        if (!isScanner || !locationAccessState.isPermissionGranted || !locationAccessState.isLocationEnabled) {
            return@LaunchedEffect
        }
        if (lastLocation == null) {
            startLiveWhenPositionArrives = true
            locationAccessState.requestPosition()
        } else {
            liveTracking = true
        }
    }
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
    LaunchedEffect(compassSource) {
        if (!compassSource.isAvailable) return@LaunchedEffect
        compassError = null
        compassSource.bearings
            .catch { compassError = "Kompass ist nicht verfügbar. Die Karte bleibt nach Norden ausgerichtet." }
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

    val activeAssignments = scannerState?.activeAssignments.orEmpty()
    val area = if (isScanner) {
        activeAssignments.singleOrNull()?.summary?.area
    } else {
        detail?.summary?.area
    }
    val geometryPositions = remember(area?.geoJson) { FieldGeoJson.positions(area?.geoJson) }
    val areaCenter = remember(area) {
        val latitude = area?.centerLatitude
        val longitude = area?.centerLongitude
        if (latitude != null && longitude != null && latitude in -90.0..90.0 && longitude in -180.0..180.0) {
            MapCoordinate(latitude, longitude)
        } else null
    }
    val scannerTheme = remember(activeAssignments, lastLocation) {
        val position = lastLocation?.let { MapCoordinate(it.latitude, it.longitude) }
        val checks = if (position == null) emptyList() else activeAssignments.mapNotNull { assignment ->
            FieldGeoJson.contains(assignment.summary.area?.geoJson, position)
        }
        when {
            checks.any { !it } -> ScannerMapTheme.ALERT
            checks.isNotEmpty() && checks.all { it } -> ScannerMapTheme.BLUE
            else -> ScannerMapTheme.DEFAULT
        }
    }
    val requestZoom: (Double) -> Unit = { delta ->
        zoomRequestId++
        zoomRequest = MapZoomRequest(zoomRequestId, delta)
    }

    if (isScanner) {
        ScannerMapFace(
            contentPadding = contentPadding,
            includeStatusBarInset = includeStatusBarInset,
            userLabel = userLabel,
            scannerState = scannerState,
            configuration = configuration,
            areaGeoJson = area?.geoJson,
            areaCenter = areaCenter,
            theme = scannerTheme,
            lastLocation = lastLocation,
            bearing = bearing,
            liveTracking = liveTracking,
            isOnline = isOnline,
            reloadKey = reloadKey,
            zoomRequest = zoomRequest,
            basemapState = basemapState,
            locationAccessState = locationAccessState,
            locationError = locationError,
            compassError = compassError,
            onBasemapStateChanged = { basemapState = it },
            onRefresh = onRefreshAssignment,
            onReload = { reloadKey++ },
            onZoom = requestZoom,
            onToggleLive = {
                if (liveTracking) {
                    liveTracking = false
                } else if (lastLocation == null) {
                    startLiveWhenPositionArrives = true
                    locationAccessState.requestPosition()
                } else {
                    liveTracking = true
                }
            },
            onChangeBuildingStatus = onChangeScannerBuildingStatus,
        )
        return
    }

    AssignmentMapFace(
        contentPadding = contentPadding,
        includeStatusBarInset = includeStatusBarInset,
        detailState = requireNotNull(assignmentState),
        configuration = configuration,
        areaGeoJson = area?.geoJson,
        areaCenter = areaCenter,
        geometryPositions = geometryPositions,
        lastLocation = lastLocation,
        bearing = bearing,
        liveTracking = liveTracking,
        isOnline = isOnline,
        reloadKey = reloadKey,
        zoomRequest = zoomRequest,
        basemapState = basemapState,
        locationAccessState = locationAccessState,
        locationError = locationError,
        compassError = compassError,
        onBasemapStateChanged = { basemapState = it },
        onReload = { reloadKey++ },
        onZoom = requestZoom,
        onToggleLive = {
            if (liveTracking) {
                liveTracking = false
            } else if (lastLocation == null) {
                startLiveWhenPositionArrives = true
                locationAccessState.requestPosition()
            } else {
                liveTracking = true
            }
        },
        onShowActions = { showActions = true },
        onChangeBuildingStatus = onChangeBuildingStatus,
        onMapClick = { coordinate ->
            val kind = captureKind ?: return@AssignmentMapFace
            val inside = FieldGeoJson.contains(area?.geoJson, coordinate)
            if (inside == false) {
                captureError = "Der gewÃ¤hlte Punkt liegt auÃŸerhalb des Zielgebiets."
            } else {
                pendingCapture = PendingLocationCapture(kind, coordinate)
                captureKind = null
                captureError = null
            }
        },
        captureKind = captureKind,
        captureError = captureError,
        onCancelCapture = {
            captureKind = null
            captureError = null
        },
        onUpdateMapFeature = onUpdateMapFeature,
        onDeleteMapFeature = onDeleteMapFeature,
    )

    if (showActions) {
        ModalBottomSheet(
            onDismissRequest = { showActions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FieldPanelColor,
            contentColor = FieldWhite,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Karten-Aktionen", style = MaterialTheme.typography.titleLarge)
                FieldActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Details",
                    icon = FieldIcons.ArrowLeft,
                    variant = FieldButtonVariant.Secondary,
                    onClick = {
                        showActions = false
                        onOpenAssignmentDetails()
                    },
                )
                val canCreatePoster = detail?.summary?.type == AssignmentType.POSTER_FREE &&
                    detail.summary.status == AssignmentStatus.ACTIVE &&
                    detail.permissions.managePosterLocations
                val hasBooth = assignmentState.mapData?.features?.any {
                    it.kind == AssignmentMapFeatureKind.CAMPAIGN_BOOTH
                } == true
                val canCreateBooth = detail?.summary?.type == AssignmentType.CAMPAIGN_BOOTH &&
                    detail.summary.status == AssignmentStatus.ACTIVE &&
                    detail.permissions.manageCampaignBoothLocation && !hasBooth
                if (canCreatePoster || canCreateBooth) {
                    val kind = if (canCreatePoster) LocationCaptureKind.POSTER else LocationCaptureKind.BOOTH
                    FieldActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (canCreatePoster) "Poster am GPS-Standort" else "Aktionsstand am GPS-Standort",
                        icon = FieldIcons.MapPin,
                        enabled = lastLocation != null,
                        variant = FieldButtonVariant.Secondary,
                        onClick = {
                            lastLocation?.let { location ->
                                val coordinate = MapCoordinate(location.latitude, location.longitude)
                                val inside = FieldGeoJson.contains(area?.geoJson, coordinate)
                                if (inside == false) {
                                    captureError = "Der aktuelle Standort liegt auÃŸerhalb des Zielgebiets."
                                } else {
                                    pendingCapture = PendingLocationCapture(kind, coordinate)
                                    captureError = null
                                }
                            }
                            showActions = false
                        },
                    )
                    FieldActionButton(
                        modifier = Modifier.fillMaxWidth(),
                        text = if (canCreatePoster) "Poster auf Karte setzen" else "Aktionsstand auf Karte setzen",
                        icon = FieldIcons.MapPinned,
                        variant = FieldButtonVariant.Secondary,
                        onClick = {
                            captureKind = kind
                            captureError = null
                            showActions = false
                        },
                    )
                }
                FieldActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "Nachweis",
                    icon = FieldIcons.FileText,
                    enabled = detail?.permissions?.createProof == true,
                    variant = FieldButtonVariant.Secondary,
                    onClick = {
                        showActions = false
                        onOpenProof()
                    },
                )
            }
        }
    }

    pendingCapture?.let { capture ->
        LocationCreateConfirmationSheet(
            capture = capture,
            isSaving = assignmentState?.changingMapFeatureId != null,
            onDismiss = { pendingCapture = null },
            onConfirm = { input ->
                when (capture.kind) {
                    LocationCaptureKind.POSTER -> onCreatePosterLocation(input)
                    LocationCaptureKind.BOOTH -> onCreateCampaignBoothLocation(input)
                }
                pendingCapture = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScannerMapFace(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
    userLabel: String?,
    scannerState: ScannerUiState?,
    configuration: MapConfiguration,
    areaGeoJson: String?,
    areaCenter: MapCoordinate?,
    theme: ScannerMapTheme,
    lastLocation: de.oliveroehme.campaignfield.location.LocationSample?,
    bearing: Double?,
    liveTracking: Boolean,
    isOnline: Boolean,
    reloadKey: Int,
    zoomRequest: MapZoomRequest?,
    basemapState: BasemapState,
    locationAccessState: LocationAccessState,
    locationError: String?,
    compassError: String?,
    onBasemapStateChanged: (BasemapState) -> Unit,
    onRefresh: () -> Unit,
    onReload: () -> Unit,
    onZoom: (Double) -> Unit,
    onToggleLive: () -> Unit,
    onChangeBuildingStatus: (String, AssignmentMapFeature, BuildingStatus) -> Unit,
) {
    var showAssignmentStatus by remember { mutableStateOf(false) }
    var selectedBuildingKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    val mapEntries = scannerState?.mapEntries.orEmpty()
    val featureGeoJson = remember(mapEntries) {
        AssignmentMapData(
            buildingCount = mapEntries.sumOf { it.mapData.buildingCount },
            posterCount = mapEntries.sumOf { it.mapData.posterCount },
            features = mapEntries.flatMap { it.mapData.features },
        ).toGeoJson()
    }
    val selectedBuilding = selectedBuildingKey?.let { (assignmentId, buildingId) ->
        mapEntries.firstOrNull { it.assignment.summary.id == assignmentId }?.let { entry ->
            entry.mapData.features.firstOrNull {
                it.kind == AssignmentMapFeatureKind.BUILDING && it.id == buildingId
            }?.let { building -> entry to building }
        }
    }?.takeIf { (entry, _) ->
        lastLocation?.let { location ->
            FieldGeoJson.contains(
                entry.assignment.summary.area?.geoJson,
                MapCoordinate(location.latitude, location.longitude),
            )
        } == true
    }
    if (scannerState?.isLoading == true) {
        ScannerGate(
            contentPadding = contentPadding,
            title = "Scanner wird vorbereitet",
            message = "Aktiver Auftrag und Zielgebiet werden geladen.",
        )
        return
    }
    if (!locationAccessState.isPermissionGranted || !locationAccessState.isLocationEnabled) {
        ScannerGate(
            contentPadding = contentPadding,
            title = "Scanner deaktiviert",
            message = locationAccessState.statusLabel,
            actionLabel = "Standort aktivieren",
            onAction = locationAccessState.requestPosition,
        )
        return
    }

    val topInsets = if (includeStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(FieldPanelColor),
    ) {
        MapLibreMapView(
            mode = FieldMapMode.SCANNER,
            theme = theme,
            configuration = configuration,
            areaGeoJson = areaGeoJson,
            featureGeoJson = featureGeoJson,
            areaCenter = areaCenter,
            currentLocation = lastLocation,
            bearing = bearing,
            followLocation = liveTracking,
            useBaseBuildingExtrusions = scannerState?.activeAssignments.isNullOrEmpty(),
            isOnline = isOnline,
            reloadKey = reloadKey,
            zoomRequest = zoomRequest,
            onBasemapStateChanged = onBasemapStateChanged,
            onFeatureClick = lastLocation?.let { location ->
                val position = MapCoordinate(location.latitude, location.longitude)
                fun(featureId: String) {
                    val entry = mapEntries.firstOrNull { candidate ->
                        candidate.assignment.summary.status == AssignmentStatus.ACTIVE &&
                            FieldGeoJson.contains(
                                candidate.assignment.summary.area?.geoJson,
                                position,
                            ) == true &&
                            candidate.mapData.features.any { feature ->
                                feature.kind == AssignmentMapFeatureKind.BUILDING &&
                                    feature.id == featureId
                            }
                    }
                    if (entry != null) {
                        selectedBuildingKey = entry.assignment.summary.id to featureId
                    }
                }
            },
        )
        ScannerGrid(theme)

        if (lastLocation == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xE605070A)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Standort wird ermittelt.",
                    color = FieldCyan,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        userLabel?.trim()?.takeIf(String::isNotEmpty)?.let { label ->
            Text(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(topInsets)
                    .padding(top = 14.dp)
                    .widthIn(max = 220.dp)
                    .clip(FieldShape)
                    .background(FieldPanelColor.copy(alpha = 0.90f))
                    .border(1.dp, FieldCyan.copy(alpha = 0.25f), FieldShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = FieldWhite,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .then(topInsets)
                .fillMaxWidth()
                .padding(start = 12.dp, top = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Row(
                modifier = Modifier
                    .clip(FieldShape)
                    .background(FieldPanelColor.copy(alpha = 0.90f))
                    .border(1.dp, FieldCyan.copy(alpha = 0.30f), FieldShape)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        liveTracking -> "Live"
                        locationAccessState.isRequesting -> "Suche"
                        lastLocation != null -> "Standort"
                        else -> "Bereit"
                    },
                    color = FieldCyan,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                lastLocation?.accuracyMeters?.let { accuracy ->
                    Text(
                        modifier = Modifier.padding(start = 8.dp),
                        text = "${accuracy.toInt()} m",
                        color = FieldMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            FieldMapControl(
                icon = FieldIcons.Navigation2,
                tint = if (liveTracking) FieldGreen else FieldCyan,
                selected = liveTracking,
                description = if (liveTracking) "Live-Standort pausieren" else "Live-Standort starten",
                onClick = onToggleLive,
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .then(topInsets)
                .padding(top = 68.dp, end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FieldMapControl(FieldIcons.Plus, FieldCyan, false, "Vergrößern") { onZoom(0.5) }
            FieldMapControl(FieldIcons.Minus, FieldCyan, false, "Verkleinern") { onZoom(-0.5) }
        }

        if (scannerState?.activeAssignments?.isNotEmpty() == true) {
            val statusTint = when {
                theme == ScannerMapTheme.ALERT -> FieldRed
                lastLocation == null || scannerState.activeAssignments.none {
                    FieldGeoJson.positions(it.summary.area?.geoJson).isNotEmpty()
                } -> FieldAmber
                else -> FieldCyan
            }
            if (showAssignmentStatus) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            bottom = contentPadding.calculateBottomPadding() + 12.dp,
                        )
                        .widthIn(max = 384.dp)
                        .clip(FieldShape)
                        .background(FieldPanelColor.copy(alpha = 0.95f))
                        .border(1.dp, statusTint.copy(alpha = 0.55f), FieldShape)
                        .clickable { showAssignmentStatus = false }
                        .padding(14.dp),
                ) {
                    FieldEyebrow("Assignmentstatus")
                    Text(
                        modifier = Modifier.padding(top = 5.dp),
                        text = "${scannerState.activeAssignments.size} aktiv",
                        color = FieldWhite,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        modifier = Modifier.padding(top = 5.dp),
                        text = when {
                            lastLocation == null -> "Standort wird ermittelt."
                            theme == ScannerMapTheme.ALERT -> "Außerhalb des aktiven Zielgebiets"
                            theme == ScannerMapTheme.BLUE -> "Im aktiven Zielgebiet"
                            else -> "Kein prüfbares Zielgebiet"
                        },
                        color = statusTint,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = 12.dp,
                            bottom = contentPadding.calculateBottomPadding() + 12.dp,
                        ),
                ) {
                    FieldMapControl(
                        icon = FieldIcons.Info,
                        tint = statusTint,
                        selected = false,
                        description = "Assignmentstatus anzeigen",
                        onClick = { showAssignmentStatus = true },
                    )
                }
            }
        }

        val notice = locationError
            ?: compassError
            ?: scannerState?.errorMessage
            ?: scannerState?.buildingStatusMessage
            ?: when {
                !isOnline -> "Kein Netz – Zielgebiet und Standort bleiben sichtbar."
                basemapState == BasemapState.UNAVAILABLE -> "MapLibre-Karte konnte nicht geladen werden."
                else -> null
            }
        notice?.let {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = contentPadding.calculateBottomPadding() + 12.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MapNotice(
                    it,
                    when {
                        locationError != null -> FieldRed
                        scannerState?.buildingStatusMessage != null -> FieldGreen
                        else -> FieldAmber
                    },
                )
                if (basemapState == BasemapState.UNAVAILABLE && isOnline) {
                    FieldActionButton(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "Neu laden",
                        variant = FieldButtonVariant.Secondary,
                        onClick = onReload,
                    )
                } else if (scannerState?.errorMessage != null) {
                    FieldActionButton(
                        modifier = Modifier.padding(top = 8.dp),
                        text = "Erneut versuchen",
                        variant = FieldButtonVariant.Secondary,
                        onClick = onRefresh,
                    )
                }
            }
        }
    }

    selectedBuilding?.let { (entry, building) ->
        BuildingStatusSheet(
            building = building,
            changingBuildingId = scannerState?.changingBuildingId,
            onDismiss = { selectedBuildingKey = null },
            onChangeStatus = { status ->
                onChangeBuildingStatus(entry.assignment.summary.id, building, status)
                selectedBuildingKey = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignmentMapFace(
    contentPadding: PaddingValues,
    includeStatusBarInset: Boolean,
    detailState: AssignmentDetailUiState,
    configuration: MapConfiguration,
    areaGeoJson: String?,
    areaCenter: MapCoordinate?,
    geometryPositions: List<MapCoordinate>,
    lastLocation: de.oliveroehme.campaignfield.location.LocationSample?,
    bearing: Double?,
    liveTracking: Boolean,
    isOnline: Boolean,
    reloadKey: Int,
    zoomRequest: MapZoomRequest?,
    basemapState: BasemapState,
    locationAccessState: LocationAccessState,
    locationError: String?,
    compassError: String?,
    onBasemapStateChanged: (BasemapState) -> Unit,
    onReload: () -> Unit,
    onZoom: (Double) -> Unit,
    onToggleLive: () -> Unit,
    onShowActions: () -> Unit,
    onChangeBuildingStatus: (AssignmentMapFeature, BuildingStatus) -> Unit,
    onMapClick: (MapCoordinate) -> Unit,
    captureKind: LocationCaptureKind?,
    captureError: String?,
    onCancelCapture: () -> Unit,
    onUpdateMapFeature: (AssignmentMapFeature, AssignmentLocationInput) -> Unit,
    onDeleteMapFeature: (AssignmentMapFeature) -> Unit,
) {
    val detail = requireNotNull(detailState.assignment)
    val assignment = detail.summary
    val layoutDirection = LocalLayoutDirection.current
    val distance = remember(areaGeoJson, lastLocation) {
        lastLocation?.let {
            FieldGeoJson.distanceMeters(areaGeoJson, MapCoordinate(it.latitude, it.longitude))
        }
    }
    val featureGeoJson = remember(detailState.mapData) { detailState.mapData?.toGeoJson() }
    var selectedFeatureId by remember { mutableStateOf<String?>(null) }
    val selectedFeature = detailState.mapData?.features?.firstOrNull { it.id == selectedFeatureId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding(),
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            )
            .then(if (includeStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 20.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                FieldEyebrow("Karte")
                Text(
                    modifier = Modifier.padding(top = 8.dp),
                    text = assignment.title,
                    color = FieldWhite,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    modifier = Modifier.padding(top = 4.dp),
                    text = assignment.type.displayName,
                    color = FieldMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            FieldStatusPill(
                label = assignment.status.displayName,
                tone = assignment.status.mapTone(),
            )
        }

        if (geometryPositions.isEmpty()) {
            MapNotice(
                "Keine Kartengeometrie im Auftrag gefunden. Die Karte bleibt nutzbar und zeigt deinen Standort, sobald du ihn freigibst.",
                FieldAmber,
            )
        }
        detailState.errorMessage?.let { MapNotice(it, FieldRed) }
        detailState.buildingStatusMessage?.let { MapNotice(it, FieldGreen) }
        detailState.mapObjectMessage?.let { MapNotice(it, FieldGreen) }
        captureError?.let { MapNotice(it, FieldRed) }
        if (captureKind != null) {
            MapNotice(
                if (captureKind == LocationCaptureKind.POSTER) {
                    "Tippe auf die Karte, um den Poster-Standort zu setzen."
                } else {
                    "Tippe auf die Karte, um den Aktionsstand zu setzen."
                },
                FieldCyan,
            )
        }
        if (assignment.status != AssignmentStatus.ACTIVE && detailState.mapData?.buildingCount != 0) {
            MapNotice("Gebäudestatus kann geändert werden, sobald der Auftrag aktiv ist.", FieldMuted)
        }
        if (!isOnline) {
            MapNotice("Kein Netz – Zielgebiet und Standort werden ohne Basiskarte angezeigt.", FieldAmber)
        } else if (basemapState == BasemapState.UNAVAILABLE) {
            MapNotice("MapLibre-Karte konnte nicht geladen werden.", FieldAmber)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(14.dp, FieldShape)
                .clip(FieldShape)
                .background(FieldPanelColor)
                .border(1.dp, FieldCyan.copy(alpha = 0.40f), FieldShape),
        ) {
            MapLibreMapView(
                mode = FieldMapMode.ASSIGNMENT,
                theme = ScannerMapTheme.DEFAULT,
                configuration = configuration,
                areaGeoJson = areaGeoJson,
                featureGeoJson = featureGeoJson,
                areaCenter = areaCenter,
                currentLocation = lastLocation,
                bearing = bearing,
                followLocation = false,
                useBaseBuildingExtrusions = false,
                isOnline = isOnline,
                reloadKey = reloadKey,
                zoomRequest = zoomRequest,
                onBasemapStateChanged = onBasemapStateChanged,
                onFeatureClick = { featureId -> selectedFeatureId = featureId },
                onMapClick = if (captureKind != null) onMapClick else null,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FieldMapControl(FieldIcons.Plus, FieldCyan, false, "Vergrößern") { onZoom(1.0) }
                FieldMapControl(FieldIcons.Minus, FieldCyan, false, "Verkleinern") { onZoom(-1.0) }
            }
            if (basemapState == BasemapState.LOADING) {
                Text(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(12.dp)
                        .clip(FieldShape)
                        .background(FieldPanelColor.copy(alpha = 0.90f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    text = "Karte wird geladen …",
                    color = FieldCyan,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MapMetric(
                Modifier.weight(1f),
                "Gebäude",
                when {
                    detailState.mapData != null -> detailState.mapData.buildingCount.toString()
                    detailState.isMapDataLoading -> "…"
                    else -> "0"
                },
            )
            MapMetric(
                Modifier.weight(1f),
                "Poster",
                when {
                    detailState.mapData != null -> detailState.mapData.posterCount.toString()
                    detailState.isMapDataLoading -> "…"
                    else -> "0"
                },
            )
            MapMetric(
                Modifier.weight(1f),
                "Ziel",
                when {
                    distance == null -> "Standort fehlt"
                    distance == 0.0 -> "Im Gebiet"
                    distance < 1_000 -> "${distance.toInt()} m"
                    else -> String.format(java.util.Locale.GERMANY, "%.1f km", distance / 1_000)
                },
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = "Standort",
                icon = FieldIcons.MapPin,
                isLoading = locationAccessState.isRequesting,
                variant = FieldButtonVariant.Secondary,
                onClick = locationAccessState.requestPosition,
            )
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = if (liveTracking) "Live stoppen" else "Live aktualisieren",
                icon = FieldIcons.Navigation2,
                variant = if (liveTracking) FieldButtonVariant.Danger else FieldButtonVariant.Secondary,
                onClick = onToggleLive,
            )
            FieldActionButton(
                modifier = Modifier.weight(1f),
                text = "Aktionen",
                icon = FieldIcons.MapPinned,
                onClick = onShowActions,
            )
        }

        if (captureKind != null) {
            FieldActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Kartenauswahl abbrechen",
                variant = FieldButtonVariant.Danger,
                onClick = onCancelCapture,
            )
        }

        val statusMessage = locationError ?: compassError
        when {
            statusMessage != null -> MapNotice(statusMessage, FieldAmber)
            lastLocation != null -> MapNotice(
                "Standort aktiv${lastLocation.accuracyMeters?.let { ", Genauigkeit ca. ${it.toInt()} m" }.orEmpty()}",
                FieldGreen,
            )
            else -> MapNotice("Standort wird erst nach deiner Aktion abgefragt.", FieldMuted)
        }
        detailState.mapDataErrorMessage?.let { MapNotice(it, FieldAmber) }
        if (isOnline && basemapState == BasemapState.UNAVAILABLE) {
            FieldActionButton(
                text = "Neu laden",
                variant = FieldButtonVariant.Secondary,
                onClick = onReload,
            )
        }
    }

    selectedFeature?.takeIf { it.kind == AssignmentMapFeatureKind.BUILDING }?.let { building ->
        BuildingStatusSheet(
            building = building,
            changingBuildingId = detailState.changingBuildingId,
            onDismiss = { selectedFeatureId = null },
            onChangeStatus = { status ->
                onChangeBuildingStatus(building, status)
                selectedFeatureId = null
            },
        )
    }
    selectedFeature?.takeIf { it.kind != AssignmentMapFeatureKind.BUILDING }?.let { feature ->
        MapFeatureEditSheet(
            feature = feature,
            isSaving = detailState.changingMapFeatureId == feature.id,
            canEdit = assignment.status == AssignmentStatus.ACTIVE && feature.canUpdate &&
                !feature.isPendingSync,
            onDismiss = { selectedFeatureId = null },
            onSave = { input ->
                onUpdateMapFeature(feature, input)
                selectedFeatureId = null
            },
            onDelete = {
                onDeleteMapFeature(feature)
                selectedFeatureId = null
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BuildingStatusSheet(
    building: AssignmentMapFeature,
    changingBuildingId: String?,
    onDismiss: () -> Unit,
    onChangeStatus: (BuildingStatus) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FieldPanelColor,
        contentColor = FieldWhite,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FieldEyebrow("Gebäude")
            Text(
                text = building.label ?: "Gebäude #${building.id}",
                color = FieldWhite,
                style = MaterialTheme.typography.titleLarge,
            )
            FieldStatusPill(
                label = (building.status ?: BuildingStatus.OPEN).displayName,
                tone = building.status.buildingTone(),
            )
            when {
                building.isPendingSync ->
                    MapNotice("Für dieses Gebäude ist eine Synchronisierung offen.", FieldAmber)
                !building.canUpdate ->
                    MapNotice("Keine Berechtigung für diese Gebäudeänderung.", FieldAmber)
            }
            BuildingStatus.actionStatuses.forEach { status ->
                FieldActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = status.displayName,
                    enabled = building.canUpdate &&
                        !building.isPendingSync &&
                        building.status != status &&
                        changingBuildingId == null,
                    isLoading = changingBuildingId == building.id,
                    variant = when (status) {
                        BuildingStatus.DONE -> FieldButtonVariant.Primary
                        BuildingStatus.UNREACHABLE,
                        BuildingStatus.PROBLEM,
                        -> FieldButtonVariant.Danger
                        else -> FieldButtonVariant.Secondary
                    },
                    onClick = { onChangeStatus(status) },
                )
            }
        }
    }
}

@Composable
private fun ScannerGate(
    contentPadding: PaddingValues,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding())
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        FieldPanel(Modifier.fillMaxWidth()) {
            FieldEyebrow("Scanner")
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = title,
                color = FieldWhite,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                modifier = Modifier.padding(top = 8.dp),
                text = message,
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            actionLabel?.let {
                FieldActionButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    text = it,
                    icon = FieldIcons.MapPin,
                    variant = FieldButtonVariant.Secondary,
                    onClick = onAction,
                )
            }
        }
    }
}

@Composable
private fun ScannerGrid(theme: ScannerMapTheme) {
    val tint = when (theme) {
        ScannerMapTheme.DEFAULT -> FieldCyan
        ScannerMapTheme.ALERT -> FieldRed
        ScannerMapTheme.BLUE -> Color(0xFF4DA3FF)
    }
    Canvas(Modifier.fillMaxSize()) {
        val step = 42.dp.toPx()
        var x = 0f
        while (x <= size.width) {
            drawLine(tint.copy(alpha = 0.16f), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1f)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            drawLine(tint.copy(alpha = 0.16f), start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1f)
            y += step
        }
    }
}

@Composable
private fun FieldMapControl(
    icon: ImageVector,
    tint: Color,
    selected: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(FieldShape)
            .background(if (selected) tint.copy(alpha = 0.10f) else FieldPanelColor.copy(alpha = 0.92f))
            .border(1.dp, tint.copy(alpha = if (selected) 0.45f else 0.30f), FieldShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(20.dp),
            imageVector = icon,
            contentDescription = description,
            tint = tint,
        )
    }
}

@Composable
private fun MapMetric(modifier: Modifier, label: String, value: String) {
    Column(
        modifier = modifier
            .clip(FieldShape)
            .background(FieldPanelColor)
            .border(1.dp, FieldBorder, FieldShape)
            .padding(12.dp),
    ) {
        Text(
            text = label.uppercase(),
            color = FieldMuted,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            modifier = Modifier.padding(top = 5.dp),
            text = value,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = FieldWhite,
            style = MaterialTheme.typography.titleMedium,
        )
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
            .then(if (includeStatusBarInset) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
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
private fun MapNotice(message: String, tint: Color) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FieldShape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.45f), FieldShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        text = message,
        color = tint,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun AssignmentStatus.mapTone(): FieldStatusTone = when (this) {
    AssignmentStatus.ACTIVE -> FieldStatusTone.Active
    AssignmentStatus.READY, AssignmentStatus.COMPLETED -> FieldStatusTone.Ready
    AssignmentStatus.PAUSED -> FieldStatusTone.Warning
    AssignmentStatus.CANCELLED -> FieldStatusTone.Danger
    else -> FieldStatusTone.Neutral
}

private fun BuildingStatus?.buildingTone(): FieldStatusTone = when (this) {
    BuildingStatus.DONE -> FieldStatusTone.Ready
    BuildingStatus.BLOCKED, BuildingStatus.SKIPPED -> FieldStatusTone.Warning
    BuildingStatus.UNREACHABLE, BuildingStatus.PROBLEM -> FieldStatusTone.Danger
    BuildingStatus.OPEN -> FieldStatusTone.Active
    else -> FieldStatusTone.Neutral
}

private const val MAX_USABLE_ACCURACY_METERS = 500f
private enum class LocationCaptureKind { POSTER, BOOTH }

private data class PendingLocationCapture(
    val kind: LocationCaptureKind,
    val coordinate: MapCoordinate,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationCreateConfirmationSheet(
    capture: PendingLocationCapture,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (AssignmentLocationInput) -> Unit,
) {
    var label by remember(capture) { mutableStateOf("") }
    var note by remember(capture) { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FieldPanelColor,
        contentColor = FieldWhite,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (capture.kind == LocationCaptureKind.POSTER) {
                    "Poster-Standort bestÃ¤tigen"
                } else {
                    "Aktionsstand bestÃ¤tigen"
                },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                String.format(
                    java.util.Locale.GERMANY,
                    "Position: %.6f, %.6f",
                    capture.coordinate.latitude,
                    capture.coordinate.longitude,
                ),
                color = FieldMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = label,
                onValueChange = { label = it.take(120) },
                label = { Text("Bezeichnung (optional)") },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = note,
                onValueChange = { note = it.take(1_000) },
                label = { Text("Notiz (optional)") },
                minLines = 2,
            )
            FieldActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Verbindlich speichern",
                icon = FieldIcons.CheckCircle,
                isLoading = isSaving,
                enabled = !isSaving,
                onClick = {
                    onConfirm(
                        AssignmentLocationInput(
                            latitude = capture.coordinate.latitude,
                            longitude = capture.coordinate.longitude,
                            label = label.trim().takeIf(String::isNotEmpty),
                            note = note.trim().takeIf(String::isNotEmpty),
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapFeatureEditSheet(
    feature: AssignmentMapFeature,
    isSaving: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onSave: (AssignmentLocationInput) -> Unit,
    onDelete: () -> Unit,
) {
    val coordinate = remember(feature.geometryGeoJson) {
        FieldGeoJson.positions(feature.geometryGeoJson).firstOrNull()
    }
    var label by remember(feature.id) { mutableStateOf(feature.label.orEmpty()) }
    var note by remember(feature.id) { mutableStateOf(feature.note.orEmpty()) }
    var status by remember(feature.id) { mutableStateOf(feature.resourceStatus.orEmpty()) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = FieldPanelColor,
        contentColor = FieldWhite,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (feature.kind == AssignmentMapFeatureKind.POSTER) "Poster-Standort" else "Aktionsstand",
                style = MaterialTheme.typography.titleLarge,
            )
            if (!canEdit) {
                Text(
                    if (feature.isPendingSync) {
                        "Eine lokale Ã„nderung wartet auf Synchronisation."
                    } else {
                        "Keine Berechtigung zum Bearbeiten oder Auftrag nicht aktiv."
                    },
                    color = FieldMuted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = label,
                onValueChange = { label = it.take(120) },
                label = { Text("Bezeichnung") },
                enabled = canEdit && !isSaving,
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = status,
                onValueChange = { status = it.take(80) },
                label = { Text("Status") },
                enabled = canEdit && !isSaving,
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = note,
                onValueChange = { note = it.take(1_000) },
                label = { Text("Notiz") },
                enabled = canEdit && !isSaving,
                minLines = 2,
            )
            FieldActionButton(
                modifier = Modifier.fillMaxWidth(),
                text = "Speichern",
                icon = FieldIcons.CheckCircle,
                isLoading = isSaving,
                enabled = canEdit && coordinate != null && !isSaving,
                onClick = {
                    coordinate?.let {
                        onSave(
                            AssignmentLocationInput(
                                latitude = it.latitude,
                                longitude = it.longitude,
                                label = label.trim().takeIf(String::isNotEmpty),
                                note = note.trim().takeIf(String::isNotEmpty),
                                status = status.trim().takeIf(String::isNotEmpty),
                            ),
                        )
                    }
                },
            )
            if (feature.canDelete) {
                FieldActionButton(
                    modifier = Modifier.fillMaxWidth(),
                    text = "LÃ¶schen",
                    variant = FieldButtonVariant.Danger,
                    enabled = canEdit && !isSaving,
                    onClick = onDelete,
                )
            }
        }
    }
}

private const val NETWORK_REFRESH_INTERVAL_MS = 2_000L
