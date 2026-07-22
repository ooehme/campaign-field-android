package de.oliveroehme.campaignfield.ui.status

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.oliveroehme.campaignfield.location.AndroidCurrentLocationRequester
import de.oliveroehme.campaignfield.location.InMemoryLocationSessionState

data class LocationAccessState(
    val hasPosition: Boolean,
    val isRequesting: Boolean,
    val statusLabel: String,
    val requestPosition: () -> Unit,
)

@Composable
fun rememberLocationAccessState(
    sessionState: InMemoryLocationSessionState,
    requester: AndroidCurrentLocationRequester,
): LocationAccessState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lastLocation by sessionState.lastLocation.collectAsStateWithLifecycle()
    var refreshKey by remember { mutableIntStateOf(0) }
    var requestKey by remember { mutableIntStateOf(0) }
    var isRequesting by remember { mutableStateOf(false) }
    var awaitingLocationSettings by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        refreshKey++
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) requestKey++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
                if (awaitingLocationSettings && requester.isLocationEnabled()) {
                    awaitingLocationSettings = false
                    requestKey++
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasFineLocationPermission = remember(refreshKey) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val isLocationEnabled = remember(refreshKey) { requester.isLocationEnabled() }

    LaunchedEffect(requestKey) {
        if (requestKey == 0 || !hasFineLocationPermission || !isLocationEnabled) return@LaunchedEffect
        isRequesting = true
        val location = requester.request()
        if (location?.accuracyMeters?.let { it <= MAX_USABLE_ACCURACY_METERS } == true) {
            sessionState.update(location)
        } else {
            sessionState.clear()
        }
        isRequesting = false
    }

    val statusLabel = when {
        isRequesting -> "Standort wird gesucht"
        lastLocation != null -> "Standort erfasst"
        !hasFineLocationPermission -> "Standortberechtigung fehlt. Tippen zum Erteilen."
        !isLocationEnabled -> "GPS ist ausgeschaltet. Tippen zum Aktivieren."
        else -> "Standort nicht erfasst. Tippen zum Erfassen."
    }

    return LocationAccessState(
        hasPosition = lastLocation != null,
        isRequesting = isRequesting,
        statusLabel = statusLabel,
        requestPosition = {
            when {
                !hasFineLocationPermission -> permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
                !isLocationEnabled -> {
                    awaitingLocationSettings = true
                    context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                else -> requestKey++
            }
        },
    )
}

private const val MAX_USABLE_ACCURACY_METERS = 500f
