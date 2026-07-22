package de.oliveroehme.campaignfield.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

interface LocationSource {
    val locations: Flow<LocationSample>
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
