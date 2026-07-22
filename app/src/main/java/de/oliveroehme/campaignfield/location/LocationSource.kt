package de.oliveroehme.campaignfield.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.location.LocationListener
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

interface LocationSource {
    val locations: Flow<LocationSample>
}

class AndroidLocationSource(context: Context) : LocationSource {
    private val locationManager = context.applicationContext
        .getSystemService(LocationManager::class.java)

    @SuppressLint("MissingPermission")
    override val locations: Flow<LocationSample> = callbackFlow {
        val manager = locationManager
        val provider = manager?.let(::enabledProvider)
        if (manager == null || provider == null) {
            close(IllegalStateException("Standort ist nicht verfügbar."))
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                trySend(
                    LocationSample(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        accuracyMeters = location.accuracy,
                    ),
                )
            }

            override fun onProviderDisabled(provider: String) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Deprecated by Android")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        try {
            manager.requestLocationUpdates(
                provider,
                LOCATION_UPDATE_INTERVAL_MS,
                LOCATION_UPDATE_DISTANCE_METERS,
                listener,
                Looper.getMainLooper(),
            )
        } catch (error: SecurityException) {
            close(error)
            return@callbackFlow
        }
        awaitClose { manager.removeUpdates(listener) }
    }

    private fun enabledProvider(manager: LocationManager): String? = when {
        runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
            LocationManager.GPS_PROVIDER
        runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
            LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private companion object {
        const val LOCATION_UPDATE_INTERVAL_MS = 2_000L
        const val LOCATION_UPDATE_DISTANCE_METERS = 1f
    }
}

class AndroidCurrentLocationRequester(context: Context) {
    private val locationManager = context.applicationContext
        .getSystemService(LocationManager::class.java)
    private val directExecutor = Executor(Runnable::run)

    fun isLocationEnabled(): Boolean = runCatching {
        locationManager?.let(LocationManagerCompat::isLocationEnabled) == true &&
            enabledProvider() != null
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    suspend fun request(): LocationSample? = withTimeoutOrNull(REQUEST_TIMEOUT_MS) {
        val manager = locationManager ?: return@withTimeoutOrNull null
        val provider = enabledProvider() ?: return@withTimeoutOrNull null
        suspendCancellableCoroutine { continuation ->
            val cancellationSignal = CancellationSignal()
            continuation.invokeOnCancellation { cancellationSignal.cancel() }
            try {
                LocationManagerCompat.getCurrentLocation(
                    manager,
                    provider,
                    cancellationSignal,
                    directExecutor,
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(
                            location?.let {
                                LocationSample(
                                    latitude = it.latitude,
                                    longitude = it.longitude,
                                    accuracyMeters = it.accuracy,
                                )
                            },
                        )
                    }
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun enabledProvider(): String? = when {
        providerEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
        providerEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
        else -> null
    }

    private fun providerEnabled(provider: String): Boolean = runCatching {
        locationManager?.isProviderEnabled(provider) == true
    }.getOrDefault(false)

    private companion object {
        const val REQUEST_TIMEOUT_MS = 12_000L
    }
}

class InMemoryLocationSessionState {
    private val mutableLastLocation = MutableStateFlow<LocationSample?>(null)
    val lastLocation: StateFlow<LocationSample?> = mutableLastLocation.asStateFlow()

    fun update(sample: LocationSample) {
        mutableLastLocation.value = sample
    }

    fun clear() {
        mutableLastLocation.value = null
    }
}
