package de.oliveroehme.campaignfield.location

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
