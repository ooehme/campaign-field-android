package de.oliveroehme.campaignfield.location

import kotlinx.coroutines.flow.Flow

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
)

interface LocationSource {
    val locations: Flow<LocationSample>
}
