package de.oliveroehme.campaignfield.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.oliveroehme.campaignfield.location.LocationSample
import de.oliveroehme.campaignfield.map.FieldGeoJson
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.map.MapCoordinate
import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.BackgroundLayer
import org.maplibre.android.style.layers.CannotAddLayerException
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillExtrusionLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.backgroundColor
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionBase
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionColor
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionHeight
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillExtrusionVerticalGradient
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.fillOutlineColor
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineDasharray
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textTransform
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

enum class BasemapState {
    LOADING,
    READY,
    UNAVAILABLE,
}

enum class FieldMapMode {
    SCANNER,
    ASSIGNMENT,
}

enum class ScannerMapTheme {
    DEFAULT,
    ALERT,
    BLUE,
}

data class MapZoomRequest(
    val id: Int,
    val delta: Double,
)

@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    mode: FieldMapMode,
    theme: ScannerMapTheme,
    configuration: MapConfiguration,
    areaGeoJson: String?,
    featureGeoJson: String?,
    areaCenter: MapCoordinate?,
    currentLocation: LocationSample?,
    bearing: Double?,
    followLocation: Boolean,
    useBaseBuildingExtrusions: Boolean,
    isOnline: Boolean,
    reloadKey: Int,
    zoomRequest: MapZoomRequest?,
    onBasemapStateChanged: (BasemapState) -> Unit,
) {
    val mapView = rememberManagedMapView(isOnline, reloadKey, mode)
    var map by remember(mapView) { mutableStateOf<MapLibreMap?>(null) }
    var loadedStyle by remember(mapView) { mutableStateOf<Style?>(null) }
    var fallbackInstalled by remember(mapView) { mutableStateOf(false) }
    var cameraInitialized by remember(mapView) { mutableStateOf(false) }

    DisposableEffect(mapView, map) {
        val failureListener = MapView.OnDidFailLoadingMapListener {
            val currentMap = map
            if (currentMap != null && !fallbackInstalled) {
                fallbackInstalled = true
                onBasemapStateChanged(BasemapState.UNAVAILABLE)
                currentMap.setStyle(Style.Builder().fromJson(MapConfiguration.FALLBACK_STYLE_JSON)) {
                    loadedStyle = it
                }
            } else {
                onBasemapStateChanged(BasemapState.UNAVAILABLE)
            }
        }
        mapView.addOnDidFailLoadingMapListener(failureListener)
        onDispose { mapView.removeOnDidFailLoadingMapListener(failureListener) }
    }

    LaunchedEffect(mapView) {
        onBasemapStateChanged(BasemapState.LOADING)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            configureInteractions(readyMap, mode, mapView)
            val styleBuilder = if (isOnline) {
                Style.Builder().fromUri(configuration.styleUrl)
            } else {
                fallbackInstalled = true
                onBasemapStateChanged(BasemapState.UNAVAILABLE)
                Style.Builder().fromJson(MapConfiguration.FALLBACK_STYLE_JSON)
            }
            readyMap.setStyle(styleBuilder) { style ->
                loadedStyle = style
                if (!fallbackInstalled) onBasemapStateChanged(BasemapState.READY)
            }
        }
    }

    LaunchedEffect(
        loadedStyle,
        areaGeoJson,
        featureGeoJson,
        currentLocation,
        bearing,
        theme,
        useBaseBuildingExtrusions,
    ) {
        val style = loadedStyle ?: return@LaunchedEffect
        applyFieldBasemapStyle(style, theme)
        syncBaseBuildingExtrusions(style, theme, useBaseBuildingExtrusions)
        updateSourcesAndLayers(style, areaGeoJson, featureGeoJson, currentLocation, bearing, theme)
    }

    LaunchedEffect(map, loadedStyle, areaGeoJson, areaCenter, currentLocation, mode) {
        val readyMap = map ?: return@LaunchedEffect
        if (loadedStyle == null || cameraInitialized) return@LaunchedEffect
        mapView.post {
            initializeCamera(
                map = readyMap,
                mode = mode,
                configuration = configuration,
                positions = FieldGeoJson.positions(areaGeoJson),
                areaCenter = areaCenter,
                currentLocation = currentLocation,
                bearing = bearing,
            )
            cameraInitialized = true
        }
    }

    LaunchedEffect(map, bearing, followLocation, currentLocation, mode) {
        val readyMap = map ?: return@LaunchedEffect
        if (!cameraInitialized || mode != FieldMapMode.SCANNER || !followLocation) return@LaunchedEffect
        val location = currentLocation ?: return@LaunchedEffect
        val previous = readyMap.cameraPosition
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(previous)
                    .target(LatLng(location.latitude, location.longitude))
                    .bearing(bearing ?: previous.bearing)
                    .tilt(configuration.initialPitch)
                    .build(),
            ),
            CAMERA_ANIMATION_MS,
        )
    }

    LaunchedEffect(map, zoomRequest?.id) {
        val readyMap = map ?: return@LaunchedEffect
        val request = zoomRequest ?: return@LaunchedEffect
        val targetZoom = if (mode == FieldMapMode.SCANNER) {
            (readyMap.cameraPosition.zoom + request.delta).coerceIn(LIVE_MIN_ZOOM, LIVE_MAX_ZOOM)
        } else {
            (readyMap.cameraPosition.zoom + request.delta).coerceAtMost(ASSIGNMENT_MAX_ZOOM)
        }
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(readyMap.cameraPosition).zoom(targetZoom).build(),
            ),
            ZOOM_ANIMATION_MS,
        )
    }

    DisposableEffect(map, mode, areaGeoJson, cameraInitialized) {
        val readyMap = map
        val targetPositions = FieldGeoJson.positions(areaGeoJson)
        if (
            readyMap == null || mode != FieldMapMode.ASSIGNMENT ||
            targetPositions.isEmpty() || !cameraInitialized
        ) {
            onDispose { }
        } else {
            var snapAnimationPending = false
            val listener = MapLibreMap.OnCameraIdleListener {
                if (snapAnimationPending) {
                    snapAnimationPending = false
                    return@OnCameraIdleListener
                }
                val next = constrainedAssignmentCamera(readyMap, targetPositions) ?: return@OnCameraIdleListener
                snapAnimationPending = true
                readyMap.animateCamera(
                    CameraUpdateFactory.newCameraPosition(next),
                    ASSIGNMENT_SNAP_BACK_MS,
                )
            }
            readyMap.addOnCameraIdleListener(listener)
            onDispose { readyMap.removeOnCameraIdleListener(listener) }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
    )
}

@Composable
private fun rememberManagedMapView(
    isOnline: Boolean,
    reloadKey: Int,
    mode: FieldMapMode,
): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context, isOnline, reloadKey, mode) {
        MapView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onCreate(Bundle())
        }
    }
    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        fun start() {
            if (!started) {
                mapView.onStart()
                started = true
            }
        }
        fun resume() {
            start()
            if (!resumed) {
                mapView.onResume()
                resumed = true
            }
        }
        fun pause() {
            if (resumed) {
                mapView.onPause()
                resumed = false
            }
        }
        fun stop() {
            pause()
            if (started) {
                mapView.onStop()
                started = false
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> start()
                Lifecycle.Event.ON_RESUME -> resume()
                Lifecycle.Event.ON_PAUSE -> pause()
                Lifecycle.Event.ON_STOP -> stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        when {
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) -> resume()
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> start()
        }
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
            mapView.onDestroy()
        }
    }
    return mapView
}

private fun configureInteractions(map: MapLibreMap, mode: FieldMapMode, mapView: MapView) {
    val controls = map.uiSettings
    if (mode == FieldMapMode.SCANNER) {
        controls.isAttributionEnabled = false
        controls.isLogoEnabled = false
        controls.isCompassEnabled = false
        controls.setAllGesturesEnabled(false)
        map.setMinZoomPreference(LIVE_MIN_ZOOM)
        map.setMaxZoomPreference(LIVE_MAX_ZOOM)
    } else {
        controls.isAttributionEnabled = true
        controls.isLogoEnabled = true
        controls.setAttributionMargins(dp(mapView, 92), dp(mapView, 4), dp(mapView, 4), dp(mapView, 78))
        controls.setLogoMargins(dp(mapView, 4), dp(mapView, 4), dp(mapView, 4), dp(mapView, 78))
        controls.setAllGesturesEnabled(true)
        controls.isRotateGesturesEnabled = false
        controls.isTiltGesturesEnabled = false
        controls.isCompassEnabled = false
        map.setMaxZoomPreference(ASSIGNMENT_MAX_ZOOM)
    }
}

private fun dp(mapView: MapView, value: Int): Int =
    (value * mapView.resources.displayMetrics.density).toInt()

private fun updateSourcesAndLayers(
    style: Style,
    areaGeoJson: String?,
    featureGeoJson: String?,
    currentLocation: LocationSample?,
    bearing: Double?,
    theme: ScannerMapTheme,
) {
    val accent = theme.colors.accent
    val targetJson = areaGeoJson ?: EMPTY_FEATURE_COLLECTION
    val targetSource = style.getSourceAs<GeoJsonSource>(TARGET_SOURCE_ID)
    if (targetSource == null) {
        style.addSource(GeoJsonSource(TARGET_SOURCE_ID, targetJson))
        style.addLayer(
            LineLayer(TARGET_LINE_LAYER_ID, TARGET_SOURCE_ID).withProperties(
                lineColor(accent),
                lineDasharray(arrayOf(2f, 3f)),
                lineOpacity(1f),
                lineWidth(1.5f),
            ),
        )
    } else {
        targetSource.setGeoJson(targetJson)
        style.getLayerAs<LineLayer>(TARGET_LINE_LAYER_ID)?.setProperties(lineColor(accent))
    }

    ensureFeatureLayers(style)
    val featuresJson = featureGeoJson ?: EMPTY_FEATURE_COLLECTION
    style.getSourceAs<GeoJsonSource>(FEATURE_GEOMETRIES_SOURCE_ID)?.setGeoJson(featuresJson)
    style.getSourceAs<GeoJsonSource>(FEATURE_MARKERS_SOURCE_ID)?.setGeoJson(featuresJson)

    val radiusJson = currentLocation?.let(::actionRadiusGeoJson) ?: EMPTY_FEATURE_COLLECTION
    val radiusSource = style.getSourceAs<GeoJsonSource>(LOCATION_RADIUS_SOURCE_ID)
    if (radiusSource == null) {
        style.addSource(GeoJsonSource(LOCATION_RADIUS_SOURCE_ID, radiusJson))
        style.addLayer(
            FillLayer(LOCATION_RADIUS_FILL_LAYER_ID, LOCATION_RADIUS_SOURCE_ID).withProperties(
                fillColor(accent),
                fillOpacity(0.22f),
            ),
        )
        style.addLayer(
            LineLayer(LOCATION_RADIUS_LINE_LAYER_ID, LOCATION_RADIUS_SOURCE_ID).withProperties(
                lineColor(accent),
                lineOpacity(0.9f),
                lineWidth(2f),
            ),
        )
    } else {
        radiusSource.setGeoJson(radiusJson)
        style.getLayerAs<FillLayer>(LOCATION_RADIUS_FILL_LAYER_ID)?.setProperties(fillColor(accent))
        style.getLayerAs<LineLayer>(LOCATION_RADIUS_LINE_LAYER_ID)?.setProperties(lineColor(accent))
    }

    val locationJson = currentLocation?.let {
        "{\"type\":\"Feature\",\"properties\":{\"bearing\":${normalizeBearing(bearing)}}," +
            "\"geometry\":{\"type\":\"Point\",\"coordinates\":[${it.longitude},${it.latitude}]}}"
    } ?: EMPTY_FEATURE_COLLECTION
    val locationSource = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE_ID)
    if (locationSource == null) {
        style.addSource(GeoJsonSource(LOCATION_SOURCE_ID, locationJson))
        style.addImage(DIRECTION_IMAGE_ID, directionBitmap(accent))
        style.addLayer(
            SymbolLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                iconImage(DIRECTION_IMAGE_ID),
                iconAllowOverlap(true),
                iconIgnorePlacement(true),
                iconRotate(Expression.get("bearing")),
                iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                iconSize(0.625f),
            ),
        )
    } else {
        locationSource.setGeoJson(locationJson)
        style.removeImage(DIRECTION_IMAGE_ID)
        style.addImage(DIRECTION_IMAGE_ID, directionBitmap(accent))
    }
}

private fun ensureFeatureLayers(style: Style) {
    if (style.getSource(FEATURE_GEOMETRIES_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(FEATURE_GEOMETRIES_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
    }
    if (style.getSource(FEATURE_MARKERS_SOURCE_ID) == null) {
        style.addSource(GeoJsonSource(FEATURE_MARKERS_SOURCE_ID, EMPTY_FEATURE_COLLECTION))
    }
    if (style.getImage(TEAM_DIRECTION_IMAGE_ID) == null) {
        style.addImage(TEAM_DIRECTION_IMAGE_ID, directionBitmap(color("#38FF9C")))
    }
    if (style.getLayer(FEATURE_FILL_LAYER_ID) == null) {
        style.addLayer(
            FillLayer(FEATURE_FILL_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(
                    Expression.raw(
                        "[\"all\",[\"==\",[\"geometry-type\"],\"Polygon\"]," +
                            "[\"!=\",[\"get\",\"kind\"],\"building\"]]",
                    ),
                )
                .withProperties(
                    fillColor(Expression.get("color")),
                    fillOpacity(Expression.get("fillOpacity")),
                ),
        )
    }
    if (style.getLayer(FEATURE_BUILDING_LAYER_ID) == null) {
        style.addLayer(
            FillExtrusionLayer(FEATURE_BUILDING_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(
                    Expression.raw(
                        "[\"all\",[\"==\",[\"geometry-type\"],\"Polygon\"]," +
                            "[\"==\",[\"get\",\"kind\"],\"building\"]]",
                    ),
                )
                .withProperties(
                    fillExtrusionBase(0f),
                    fillExtrusionColor(Expression.get("color")),
                    fillExtrusionHeight(Expression.get("extrusionHeight")),
                    fillExtrusionOpacity(1f),
                    fillExtrusionVerticalGradient(true),
                ),
        )
    }
    if (style.getLayer(FEATURE_BUILDING_OUTLINE_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(FEATURE_BUILDING_OUTLINE_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(Expression.raw("[\"==\",[\"get\",\"kind\"],\"building\"]"))
                .withProperties(
                    lineColor(color("#4DA3FF")),
                    lineOpacity(0.88f),
                    lineWidth(1f),
                ),
        )
    }
    if (style.getLayer(FEATURE_BOOTH_AREA_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(FEATURE_BOOTH_AREA_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(Expression.raw("[\"==\",[\"get\",\"kind\"],\"booth-area\"]"))
                .withProperties(
                    lineCap(Property.LINE_CAP_ROUND),
                    lineJoin(Property.LINE_JOIN_ROUND),
                    lineColor(color("#FFB84D")),
                    lineDasharray(arrayOf(0.4f, 2f)),
                    lineOpacity(1f),
                    lineWidth(1.25f),
                ),
        )
    }
    if (style.getLayer(FEATURE_LINE_LAYER_ID) == null) {
        style.addLayer(
            LineLayer(FEATURE_LINE_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(
                    Expression.raw(
                        "[\"all\",[\"!=\",[\"get\",\"kind\"],\"building\"]," +
                            "[\"!=\",[\"get\",\"kind\"],\"booth-area\"]]",
                    ),
                )
                .withProperties(
                    lineColor(Expression.get("color")),
                    lineOpacity(1f),
                    lineWidth(2f),
                ),
        )
    }
    if (style.getLayer(FEATURE_GEOMETRY_POINT_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(FEATURE_GEOMETRY_POINT_LAYER_ID, FEATURE_GEOMETRIES_SOURCE_ID)
                .withFilter(
                    Expression.raw(
                        "[\"all\",[\"==\",[\"geometry-type\"],\"Point\"]," +
                            "[\"!=\",[\"get\",\"kind\"],\"building\"]," +
                            "[\"!=\",[\"get\",\"kind\"],\"team-member\"]]",
                    ),
                )
                .withProperties(
                    circleColor(Expression.get("color")),
                    circleOpacity(1f),
                    circleRadius(Expression.get("radius")),
                    circleStrokeColor(Expression.get("color")),
                    circleStrokeOpacity(1f),
                    circleStrokeWidth(2f),
                ),
        )
    }
    if (style.getLayer(FEATURE_MARKER_LAYER_ID) == null) {
        style.addLayer(
            CircleLayer(FEATURE_MARKER_LAYER_ID, FEATURE_MARKERS_SOURCE_ID)
                .withFilter(Expression.raw("[\"!=\",[\"get\",\"kind\"],\"team-member\"]"))
                .withProperties(
                    circleColor(Expression.get("color")),
                    circleOpacity(1f),
                    circleRadius(Expression.get("radius")),
                    circleStrokeColor(Expression.get("color")),
                    circleStrokeOpacity(1f),
                    circleStrokeWidth(2f),
                ),
        )
    }
    if (style.getLayer(FEATURE_TEAM_DIRECTION_LAYER_ID) == null) {
        style.addLayer(
            SymbolLayer(FEATURE_TEAM_DIRECTION_LAYER_ID, FEATURE_MARKERS_SOURCE_ID)
                .withFilter(
                    Expression.raw(
                        "[\"all\",[\"==\",[\"geometry-type\"],\"Point\"]," +
                            "[\"==\",[\"get\",\"kind\"],\"team-member\"]]",
                    ),
                )
                .withProperties(
                    iconAllowOverlap(true),
                    iconImage(TEAM_DIRECTION_IMAGE_ID),
                    iconIgnorePlacement(true),
                    iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_VIEWPORT),
                    iconRotate(Expression.get("bearing")),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    iconSize(0.46f),
                ),
        )
    }
}

private fun applyFieldBasemapStyle(style: Style, theme: ScannerMapTheme) {
    val colors = theme.colors
    style.layers.filterNot { it.id.startsWith("field-") }.forEach { layer ->
        val key = layer.id.lowercase()
        when (layer) {
            is BackgroundLayer -> layer.setProperties(backgroundColor(colors.background))
            is CircleLayer -> layer.setProperties(
                circleColor(colors.accent),
                circleStrokeColor(colors.background),
            )
            is FillLayer -> when {
                key.includesAny("water", "ocean", "lake", "river") -> layer.setProperties(
                    fillColor(colors.water), fillOutlineColor(colors.waterLine),
                )
                key.includesAny("building") -> layer.setProperties(
                    fillColor(colors.building),
                    fillOpacity(1f),
                    fillOutlineColor(colors.buildingOutline),
                )
                key.includesAny("park", "grass", "wood", "forest", "nature") ->
                    layer.setProperties(fillColor(colors.park))
                key.includesAny("landcover", "landuse", "green") ->
                    layer.setProperties(fillColor(colors.landcover))
                else -> layer.setProperties(fillColor(colors.land))
            }
            is FillExtrusionLayer -> layer.setProperties(
                fillExtrusionColor(colors.buildingExtrusion),
                fillExtrusionOpacity(1f),
                fillExtrusionVerticalGradient(true),
            )
            is LineLayer -> when {
                key.includesAny("water", "waterway", "river") ->
                    layer.setProperties(lineColor(colors.waterLine))
                key.includesAny("boundary", "admin") -> layer.setProperties(
                    lineColor(colors.boundary), lineOpacity(0.78f),
                )
                key.includesAny("motorway", "trunk", "primary", "major") ->
                    layer.setProperties(lineColor(colors.roadMajor))
                key.includesAny("road", "highway", "transportation", "street") ->
                    layer.setProperties(
                        lineColor(if (key.includesAny("casing", "outline")) colors.roadCasing else colors.road),
                    )
                key.includesAny("rail", "transit") -> layer.setProperties(lineColor(colors.rail))
                else -> layer.setProperties(lineColor(colors.mutedLine))
            }
            is SymbolLayer -> {
                val labelColor = when {
                    key.includesAny("country", "state", "city", "town") -> colors.labelStrong
                    key.includesAny("water", "river", "lake") -> colors.waterLabel
                    else -> colors.label
                }
                layer.setProperties(
                    textColor(labelColor),
                    textHaloColor(colors.labelHalo),
                    textHaloWidth(1.2f),
                )
                if (key.includesAny("place", "country", "state", "city", "town")) {
                    layer.setProperties(textTransform("uppercase"))
                }
            }
        }
    }
}

private fun syncBaseBuildingExtrusions(
    style: Style,
    theme: ScannerMapTheme,
    enabled: Boolean,
) {
    val baseExtrusions = style.layers.filterIsInstance<FillExtrusionLayer>()
        .filterNot { it.id == FEATURE_BUILDING_LAYER_ID }
    if (!enabled) {
        baseExtrusions.forEach { style.removeLayer(it.id) }
        return
    }
    style.getLayerAs<FillExtrusionLayer>(BASE_BUILDING_EXTRUSION_LAYER_ID)?.let { layer ->
        layer.applyBuildingExtrusionTheme(theme)
        return
    }
    if (baseExtrusions.isNotEmpty()) {
        baseExtrusions.forEach { it.applyBuildingExtrusionTheme(theme) }
        return
    }
    val buildingLayer = style.layers.filterIsInstance<FillLayer>().firstOrNull { layer ->
        layer.id.contains("building", ignoreCase = true) ||
            layer.sourceLayer.contains("building", ignoreCase = true)
    } ?: return
    val overlayAnchor = baseBuildingOverlayAnchor(style, buildingLayer)
    style.removeLayer(buildingLayer)
    if (overlayAnchor != null) {
        style.addLayerBelow(buildingLayer, overlayAnchor.id)
    } else {
        style.addLayer(buildingLayer)
    }
    val extrusion = FillExtrusionLayer(BASE_BUILDING_EXTRUSION_LAYER_ID, buildingLayer.sourceId)
    if (buildingLayer.sourceLayer.isNotBlank()) {
        extrusion.withSourceLayer(buildingLayer.sourceLayer)
    }
    extrusion.withProperties(
            fillExtrusionColor(theme.colors.buildingExtrusion),
            fillExtrusionBase(
                Expression.raw(
                    "[\"case\",[\"has\",\"render_min_height\"],[\"to-number\",[\"get\",\"render_min_height\"],0]," +
                        "[\"has\",\"min_height\"],[\"to-number\",[\"get\",\"min_height\"],0],0]",
                ),
            ),
            fillExtrusionHeight(
                Expression.raw(
                    "[\"interpolate\",[\"linear\"],[\"zoom\"],15,0,17," +
                        "[\"case\",[\"has\",\"render_height\"],[\"to-number\",[\"get\",\"render_height\"],9]," +
                        "[\"has\",\"height\"],[\"to-number\",[\"get\",\"height\"],9]," +
                        "[\"has\",\"building:levels\"],[\"*\",[\"to-number\",[\"get\",\"building:levels\"],3],3]," +
                        "[\"has\",\"levels\"],[\"*\",[\"to-number\",[\"get\",\"levels\"],3],3],9]]",
                ),
            ),
            fillExtrusionOpacity(0.68f),
            fillExtrusionVerticalGradient(true),
    )
    extrusion.setMinZoom(15f)
    try {
        if (overlayAnchor != null) {
            style.addLayerBelow(extrusion, overlayAnchor.id)
        } else {
            style.addLayer(extrusion)
        }
    } catch (exception: CannotAddLayerException) {
        val duplicateWasAdded = style.getLayer(BASE_BUILDING_EXTRUSION_LAYER_ID) != null ||
            exception.message.orEmpty().contains(
                "Layer $BASE_BUILDING_EXTRUSION_LAYER_ID already exists",
                ignoreCase = true,
            )
        if (!duplicateWasAdded) throw exception
    }
}

private fun baseBuildingOverlayAnchor(style: Style, buildingLayer: FillLayer): SymbolLayer? {
    val layers = style.layers
    val buildingIndex = layers.indexOf(buildingLayer)
    val lastTransportGeometryIndex = layers.indexOfLast { layer ->
        layer !is SymbolLayer && layer.id.lowercase().includesAny(
            "aeroway",
            "bridge",
            "highway",
            "path",
            "railway",
            "road",
            "street",
            "transportation",
            "tunnel",
        )
    }
    val firstOverlayIndex = max(buildingIndex, lastTransportGeometryIndex) + 1
    return layers.drop(firstOverlayIndex).filterIsInstance<SymbolLayer>().firstOrNull()
}

private fun FillExtrusionLayer.applyBuildingExtrusionTheme(theme: ScannerMapTheme) {
    setProperties(
        fillExtrusionColor(theme.colors.buildingExtrusion),
        fillExtrusionOpacity(1f),
        fillExtrusionVerticalGradient(true),
    )
}

private fun initializeCamera(
    map: MapLibreMap,
    mode: FieldMapMode,
    configuration: MapConfiguration,
    positions: List<MapCoordinate>,
    areaCenter: MapCoordinate?,
    currentLocation: LocationSample?,
    bearing: Double?,
) {
    if (mode == FieldMapMode.SCANNER) {
        val target = currentLocation?.let { MapCoordinate(it.latitude, it.longitude) }
            ?: areaCenter
            ?: positions.firstOrNull()
            ?: DEFAULT_CENTER
        map.moveCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder()
                    .target(LatLng(target.latitude, target.longitude))
                    .zoom(configuration.initialZoom)
                    .tilt(configuration.initialPitch)
                    .bearing(bearing ?: configuration.fallbackBearing)
                    .build(),
            ),
        )
        return
    }

    if (positions.size > 1) {
        val bounds = LatLngBounds.Builder()
            .includes(positions.map { LatLng(it.latitude, it.longitude) })
            .build()
        val fitted = map.getCameraForLatLngBounds(
            bounds,
            intArrayOf(FIT_PADDING, FIT_PADDING, FIT_PADDING, FIT_PADDING),
            0.0,
            0.0,
        )
        if (fitted != null) {
            val zoom = min(fitted.zoom, MAX_FIT_ZOOM)
            map.setMinZoomPreference(zoom)
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(fitted)
                        .zoom(zoom)
                        .bearing(0.0)
                        .tilt(0.0)
                        .padding(0.0, 0.0, 0.0, 0.0)
                        .build(),
                ),
            )
            return
        }
    }
    val target = positions.firstOrNull()
        ?: areaCenter
        ?: currentLocation?.let { MapCoordinate(it.latitude, it.longitude) }
        ?: DEFAULT_CENTER
    map.setMinZoomPreference(ASSIGNMENT_DEFAULT_ZOOM)
    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(target.latitude, target.longitude))
                .zoom(ASSIGNMENT_DEFAULT_ZOOM)
                .tilt(0.0)
                .bearing(0.0)
                .build(),
        ),
    )
}

private fun actionRadiusGeoJson(location: LocationSample): String {
    val centerLatitude = Math.toRadians(location.latitude)
    val centerLongitude = Math.toRadians(location.longitude)
    val angularDistance = ACTION_RADIUS_METERS / EARTH_RADIUS_METERS
    val coordinates = (0..ACTION_RADIUS_SEGMENTS).joinToString(",") { index ->
        val angle = 2.0 * Math.PI * index / ACTION_RADIUS_SEGMENTS
        val latitude = asin(
            sin(centerLatitude) * cos(angularDistance) +
                cos(centerLatitude) * sin(angularDistance) * cos(angle),
        )
        val longitude = centerLongitude + kotlin.math.atan2(
            sin(angle) * sin(angularDistance) * cos(centerLatitude),
            cos(angularDistance) - sin(centerLatitude) * sin(latitude),
        )
        "[${Math.toDegrees(longitude)},${Math.toDegrees(latitude)}]"
    }
    return "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{" +
        "\"type\":\"Polygon\",\"coordinates\":[[$coordinates]]}}"
}

private fun directionBitmap(accentColor: Int): Bitmap {
    val bitmap = createBitmap(72, 72)
    val canvas = Canvas(bitmap)
    val path = Path().apply {
        moveTo(36f, 8f)
        lineTo(56f, 58f)
        lineTo(36f, 48f)
        lineTo(16f, 58f)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL_AND_STROKE
        this.color = accentColor
        strokeWidth = 4f
        setShadowLayer(14f, 0f, 0f, accentColor)
    }
    canvas.drawPath(path, paint)
    paint.clearShadowLayer()
    paint.style = Paint.Style.STROKE
    paint.color = color("#051018")
    canvas.drawPath(path, paint)
    canvas.drawCircle(
        36f,
        40f,
        4f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color("#F4FBFF")
        },
    )
    return bitmap
}

private fun constrainedAssignmentCamera(
    map: MapLibreMap,
    positions: List<MapCoordinate>,
): CameraPosition? {
    val current = map.cameraPosition
    val center = current.target ?: return null
    val targetNorth = positions.maxOf { it.latitude }
    val targetSouth = positions.minOf { it.latitude }
    val targetEast = positions.maxOf { it.longitude }
    val targetWest = positions.minOf { it.longitude }
    val minZoom = map.minZoomLevel
    val nextZoom = current.zoom.coerceIn(minZoom, ASSIGNMENT_MAX_ZOOM)
    val nextCenter = if (abs(nextZoom - minZoom) < 0.01) {
        LatLng((targetNorth + targetSouth) / 2.0, (targetEast + targetWest) / 2.0)
    } else {
        val view = map.projection.visibleRegion.latLngBounds
        val halfLongitude = max(
            abs(center.longitude - view.longitudeWest),
            abs(view.longitudeEast - center.longitude),
        )
        val halfLatitude = max(
            abs(view.latitudeNorth - center.latitude),
            abs(center.latitude - view.latitudeSouth),
        )
        LatLng(
            clampViewportCenter(center.latitude, halfLatitude, targetSouth, targetNorth),
            clampViewportCenter(center.longitude, halfLongitude, targetWest, targetEast),
        )
    }
    if (
        abs(center.latitude - nextCenter.latitude) <= 0.000001 &&
        abs(center.longitude - nextCenter.longitude) <= 0.000001 &&
        abs(current.tilt) <= 0.1 && abs(current.bearing) <= 0.1 &&
        abs(current.zoom - nextZoom) <= 0.01
    ) {
        return null
    }
    return CameraPosition.Builder(current)
        .target(nextCenter)
        .zoom(nextZoom)
        .tilt(0.0)
        .bearing(0.0)
        .build()
}

private fun clampViewportCenter(
    center: Double,
    halfSize: Double,
    targetMin: Double,
    targetMax: Double,
): Double {
    val targetSize = targetMax - targetMin
    val viewSize = halfSize * 2.0
    val room = viewSize * 0.1
    return if (viewSize >= targetSize) {
        clamp(center, targetMax - halfSize - room, targetMin + halfSize + room)
    } else {
        clamp(center, targetMin + halfSize - room, targetMax - halfSize + room)
    }
}

private fun clamp(value: Double, minimum: Double, maximum: Double): Double =
    min(maximum, max(minimum, value))

private fun normalizeBearing(value: Double?): Double =
    value?.takeIf(Double::isFinite)?.let { ((it % 360.0) + 360.0) % 360.0 } ?: 0.0

private fun String.includesAny(vararg terms: String): Boolean = terms.any(::contains)

private data class FieldStyleColors(
    val accent: Int,
    val background: Int,
    val boundary: Int,
    val building: Int,
    val buildingOutline: Int,
    val buildingExtrusion: Int,
    val land: Int,
    val landcover: Int,
    val label: Int,
    val labelHalo: Int,
    val labelStrong: Int,
    val mutedLine: Int,
    val park: Int,
    val rail: Int,
    val road: Int,
    val roadCasing: Int,
    val roadMajor: Int,
    val water: Int,
    val waterLabel: Int,
    val waterLine: Int,
)

private val ScannerMapTheme.colors: FieldStyleColors
    get() = when (this) {
        ScannerMapTheme.DEFAULT -> FieldStyleColors(
            accent = color("#36F7FF"), background = color("#05070A"), boundary = color("#5236F7FF"),
            building = color("#0A121C"), buildingOutline = color("#16414D"),
            buildingExtrusion = color("#0F3440"), land = color("#071018"), landcover = color("#1438FF9C"),
            label = color("#7D8EA3"), labelHalo = color("#05070A"), labelStrong = color("#F4FBFF"),
            mutedLine = color("#15303A"), park = color("#1F38FF9C"), rail = color("#18323C"),
            road = color("#16313B"), roadCasing = color("#091620"), roadMajor = color("#1D4852"),
            water = color("#061923"), waterLabel = color("#B836F7FF"), waterLine = color("#0B2B37"),
        )
        ScannerMapTheme.ALERT -> FieldStyleColors(
            accent = color("#FF4D5E"), background = color("#170306"), boundary = color("#6BFF4D5E"),
            building = color("#26070C"), buildingOutline = color("#8E1B2A"),
            buildingExtrusion = color("#4D0B14"), land = color("#1B0509"), landcover = color("#1AFF4D5E"),
            label = color("#FCA5A5"), labelHalo = color("#170306"), labelStrong = color("#FFF1F2"),
            mutedLine = color("#5B111B"), park = color("#24FF4D5E"), rail = color("#6D1723"),
            road = color("#641520"), roadCasing = color("#27070D"), roadMajor = color("#A32031"),
            water = color("#250812"), waterLabel = color("#D1FECACA"), waterLine = color("#7F1D2D"),
        )
        ScannerMapTheme.BLUE -> FieldStyleColors(
            accent = color("#4DA3FF"), background = color("#03101F"), boundary = color("#6B4DA3FF"),
            building = color("#061B33"), buildingOutline = color("#1D5A8F"),
            buildingExtrusion = color("#0B315E"), land = color("#051526"), landcover = color("#1A4DA3FF"),
            label = color("#BFDBFE"), labelHalo = color("#03101F"), labelStrong = color("#EFF6FF"),
            mutedLine = color("#12385F"), park = color("#244DA3FF"), rail = color("#174A76"),
            road = color("#195487"), roadCasing = color("#061728"), roadMajor = color("#2E7BC2"),
            water = color("#061E3A"), waterLabel = color("#DBBFDBFE"), waterLine = color("#1D4F80"),
        )
    }

private fun color(value: String): Int = value.toColorInt()

private val DEFAULT_CENTER = MapCoordinate(latitude = 51.1657, longitude = 10.4515)
private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val TARGET_SOURCE_ID = "field-target-area"
private const val TARGET_LINE_LAYER_ID = "field-target-area-line"
private const val FEATURE_GEOMETRIES_SOURCE_ID = "field-feature-geometries"
private const val FEATURE_MARKERS_SOURCE_ID = "field-feature-markers"
private const val FEATURE_FILL_LAYER_ID = "field-feature-fill"
private const val FEATURE_BUILDING_LAYER_ID = "field-feature-building-extrusion"
private const val FEATURE_BUILDING_OUTLINE_LAYER_ID = "field-feature-building-outline"
private const val FEATURE_BOOTH_AREA_LAYER_ID = "field-feature-booth-area-line"
private const val FEATURE_LINE_LAYER_ID = "field-feature-line"
private const val FEATURE_GEOMETRY_POINT_LAYER_ID = "field-feature-geometry-point"
private const val FEATURE_MARKER_LAYER_ID = "field-feature-marker"
private const val FEATURE_TEAM_DIRECTION_LAYER_ID = "field-feature-team-member-direction"
private const val TEAM_DIRECTION_IMAGE_ID = "field-team-member-direction-image"
private const val LOCATION_RADIUS_SOURCE_ID = "field-current-position-action-radius"
private const val LOCATION_RADIUS_FILL_LAYER_ID = "field-current-position-action-radius-fill"
private const val LOCATION_RADIUS_LINE_LAYER_ID = "field-current-position-action-radius-outline"
private const val LOCATION_SOURCE_ID = "field-current-position"
private const val LOCATION_LAYER_ID = "field-current-position-direction"
private const val DIRECTION_IMAGE_ID = "field-current-position-direction-image"
private const val BASE_BUILDING_EXTRUSION_LAYER_ID = "field-base-buildings-extrusion"
private const val FIT_PADDING = 28
private const val CAMERA_ANIMATION_MS = 180
private const val ZOOM_ANIMATION_MS = 140
private const val ASSIGNMENT_SNAP_BACK_MS = 500
private const val MAX_FIT_ZOOM = 17.0
private const val ASSIGNMENT_MAX_ZOOM = 19.0
private const val ASSIGNMENT_DEFAULT_ZOOM = 14.0
private const val LIVE_MIN_ZOOM = 16.0
private const val LIVE_MAX_ZOOM = 18.0
private const val ACTION_RADIUS_METERS = 10.0
private const val ACTION_RADIUS_SEGMENTS = 64
private const val EARTH_RADIUS_METERS = 6_371_008.8
