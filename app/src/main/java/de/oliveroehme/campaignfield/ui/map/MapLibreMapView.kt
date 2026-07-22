package de.oliveroehme.campaignfield.ui.map

import android.graphics.Color
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.oliveroehme.campaignfield.location.LocationSample
import de.oliveroehme.campaignfield.map.FieldGeoJson
import de.oliveroehme.campaignfield.map.MapConfiguration
import de.oliveroehme.campaignfield.map.MapCoordinate
import kotlin.math.min
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.fillColor
import org.maplibre.android.style.layers.PropertyFactory.fillOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonSource

enum class BasemapState {
    LOADING,
    READY,
    UNAVAILABLE,
}

@Composable
fun MapLibreMapView(
    modifier: Modifier = Modifier,
    configuration: MapConfiguration,
    areaGeoJson: String?,
    areaCenter: MapCoordinate?,
    currentLocation: LocationSample?,
    bearing: Double?,
    followLocation: Boolean,
    isOnline: Boolean,
    reloadKey: Int,
    onBasemapStateChanged: (BasemapState) -> Unit,
) {
    val mapView = rememberManagedMapView(isOnline, reloadKey)
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
            readyMap.uiSettings.isAttributionEnabled = true
            readyMap.uiSettings.isLogoEnabled = true
            readyMap.uiSettings.isCompassEnabled = true
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

    LaunchedEffect(loadedStyle, areaGeoJson, currentLocation) {
        val style = loadedStyle ?: return@LaunchedEffect
        updateSourcesAndLayers(style, areaGeoJson, currentLocation)
    }

    LaunchedEffect(map, loadedStyle, areaGeoJson, areaCenter) {
        val readyMap = map ?: return@LaunchedEffect
        if (loadedStyle == null || cameraInitialized) return@LaunchedEffect
        mapView.post {
            initializeCamera(
                map = readyMap,
                configuration = configuration,
                positions = FieldGeoJson.positions(areaGeoJson),
                areaCenter = areaCenter,
                currentLocation = currentLocation,
                bearing = bearing,
            )
            cameraInitialized = true
        }
    }

    LaunchedEffect(map, bearing, followLocation, currentLocation) {
        val readyMap = map ?: return@LaunchedEffect
        if (!cameraInitialized) return@LaunchedEffect
        val previous = readyMap.cameraPosition
        val target = if (followLocation) {
            currentLocation?.let { LatLng(it.latitude, it.longitude) } ?: previous.target
        } else {
            previous.target
        }
        readyMap.animateCamera(
            CameraUpdateFactory.newCameraPosition(
                CameraPosition.Builder(previous)
                    .target(target)
                    .bearing(bearing ?: previous.bearing)
                    .tilt(configuration.initialPitch)
                    .build(),
            ),
            CAMERA_ANIMATION_MS,
        )
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
    )
}

@Composable
private fun rememberManagedMapView(isOnline: Boolean, reloadKey: Int): MapView {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember(context, isOnline, reloadKey) {
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

private fun updateSourcesAndLayers(
    style: Style,
    areaGeoJson: String?,
    currentLocation: LocationSample?,
) {
    val targetJson = areaGeoJson ?: EMPTY_FEATURE_COLLECTION
    val targetSource = style.getSourceAs<GeoJsonSource>(TARGET_SOURCE_ID)
    if (targetSource == null) {
        style.addSource(GeoJsonSource(TARGET_SOURCE_ID, targetJson))
        style.addLayer(
            FillLayer(TARGET_FILL_LAYER_ID, TARGET_SOURCE_ID).withProperties(
                fillColor(Color.parseColor("#36F7FF")),
                fillOpacity(0.16f),
            ),
        )
        style.addLayer(
            LineLayer(TARGET_LINE_LAYER_ID, TARGET_SOURCE_ID).withProperties(
                lineColor(Color.parseColor("#36F7FF")),
                lineWidth(3f),
            ),
        )
    } else {
        targetSource.setGeoJson(targetJson)
    }

    val locationJson = currentLocation?.let {
        "{\"type\":\"Feature\",\"properties\":{},\"geometry\":{" +
            "\"type\":\"Point\",\"coordinates\":[${it.longitude},${it.latitude}]}}"
    } ?: EMPTY_FEATURE_COLLECTION
    val locationSource = style.getSourceAs<GeoJsonSource>(LOCATION_SOURCE_ID)
    if (locationSource == null) {
        style.addSource(GeoJsonSource(LOCATION_SOURCE_ID, locationJson))
        style.addLayer(
            CircleLayer(LOCATION_LAYER_ID, LOCATION_SOURCE_ID).withProperties(
                circleRadius(8f),
                circleColor(Color.parseColor("#38FF9C")),
                circleStrokeWidth(3f),
                circleStrokeColor(Color.parseColor("#071014")),
            ),
        )
    } else {
        locationSource.setGeoJson(locationJson)
    }
}

private fun initializeCamera(
    map: MapLibreMap,
    configuration: MapConfiguration,
    positions: List<MapCoordinate>,
    areaCenter: MapCoordinate?,
    currentLocation: LocationSample?,
    bearing: Double?,
) {
    val cameraBearing = bearing ?: configuration.fallbackBearing
    if (positions.size > 1) {
        val bounds = LatLngBounds.Builder()
            .includes(positions.map { LatLng(it.latitude, it.longitude) })
            .build()
        val fitted = map.getCameraForLatLngBounds(
            bounds,
            intArrayOf(CAMERA_PADDING, CAMERA_PADDING, CAMERA_PADDING, CAMERA_PADDING),
            cameraBearing,
            configuration.initialPitch,
        )
        if (fitted != null) {
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(fitted)
                        .zoom(min(fitted.zoom, MAX_FIT_ZOOM))
                        .bearing(cameraBearing)
                        .tilt(configuration.initialPitch)
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
    map.moveCamera(
        CameraUpdateFactory.newCameraPosition(
            CameraPosition.Builder()
                .target(LatLng(target.latitude, target.longitude))
                .zoom(configuration.initialZoom)
                .tilt(configuration.initialPitch)
                .bearing(cameraBearing)
                .build(),
        ),
    )
}

private val DEFAULT_CENTER = MapCoordinate(latitude = 51.1657, longitude = 10.4515)
private const val EMPTY_FEATURE_COLLECTION = "{\"type\":\"FeatureCollection\",\"features\":[]}"
private const val TARGET_SOURCE_ID = "field-target-area"
private const val TARGET_FILL_LAYER_ID = "field-target-area-fill"
private const val TARGET_LINE_LAYER_ID = "field-target-area-line"
private const val LOCATION_SOURCE_ID = "field-current-location"
private const val LOCATION_LAYER_ID = "field-current-location-circle"
private const val CAMERA_PADDING = 64
private const val CAMERA_ANIMATION_MS = 180
private const val MAX_FIT_ZOOM = 17.0
