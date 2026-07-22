package de.oliveroehme.campaignfield.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

interface CompassSource {
    val isAvailable: Boolean
    val bearings: Flow<Double>
}

class AndroidCompassSource(context: Context) : CompassSource {
    private val sensorManager = context.applicationContext.getSystemService(SensorManager::class.java)
    private val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    override val isAvailable: Boolean = rotationSensor != null

    override val bearings: Flow<Double> = callbackFlow {
        val manager = sensorManager
        val sensor = rotationSensor
        if (manager == null || sensor == null) {
            close(IllegalStateException("Kompass ist auf diesem Gerät nicht verfügbar."))
            return@callbackFlow
        }
        val smoother = BearingSmoother()
        val rotationMatrix = FloatArray(9)
        val orientation = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val degrees = normalizeBearing(orientation[0] * 180.0 / PI)
                trySend(smoother.update(degrees))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (!manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)) {
            close(IllegalStateException("Kompass konnte nicht gestartet werden."))
            return@callbackFlow
        }
        awaitClose { manager.unregisterListener(listener) }
    }
}

internal class BearingSmoother(private val alpha: Double = 0.18) {
    private var x: Double? = null
    private var y: Double? = null

    fun update(bearing: Double): Double {
        val radians = normalizeBearing(bearing) * PI / 180.0
        val nextX = cos(radians)
        val nextY = sin(radians)
        x = x?.let { previous -> previous * (1.0 - alpha) + nextX * alpha } ?: nextX
        y = y?.let { previous -> previous * (1.0 - alpha) + nextY * alpha } ?: nextY
        return normalizeBearing(atan2(requireNotNull(y), requireNotNull(x)) * 180.0 / PI)
    }
}

internal fun normalizeBearing(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
