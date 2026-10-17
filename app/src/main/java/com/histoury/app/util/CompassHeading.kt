package com.histoury.app.util

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Which way the phone is facing, in degrees from TRUE north (0 = north,
 * 90 = east), from the phone's compass (the rotation-vector sensor).
 *
 * Navigation used to point the arrow using only the GPS "course" — the
 * direction between the last couple of fixes. That's fine in a moving car,
 * but at walking pace a fix that jumps a few metres backwards produces a
 * course pointing the opposite way, and standing still there's no course
 * at all. The compass answers immediately and doesn't depend on moving.
 *
 * Works whether the phone is held flat (top edge) or upright like a camera
 * (back of the phone), and follows the screen's rotation.
 */
class CompassHeading(context: Context) {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(SensorManager::class.java)
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

    private var onHeading: ((Float) -> Unit)? = null
    private val rotation = FloatArray(9)

    /** Magnetic north -> true north, from the latest GPS position. */
    @Volatile
    private var declination = 0f

    /** False while Android reports the compass needs calibrating. */
    @Volatile
    var isReliable = true
        private set

    val isAvailable: Boolean get() = sensor != null

    private val listener = object : SensorEventListener {

        override fun onSensorChanged(event: SensorEvent) {
            SensorManager.getRotationMatrixFromVector(rotation, event.values)
            val heading = headingFrom(rotation) ?: return
            onHeading?.invoke(((heading + declination) % 360f + 360f) % 360f)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            isReliable = accuracy != SensorManager.SENSOR_STATUS_UNRELIABLE
        }
    }

    fun start(onHeading: (Float) -> Unit) {
        val s = sensor ?: return
        stop()
        this.onHeading = onHeading
        runCatching {
            sensorManager?.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        onHeading = null
        runCatching { sensorManager?.unregisterListener(listener) }
    }

    /** Call with each GPS fix so the heading is relative to true north. */
    fun updateLocation(latitude: Double, longitude: Double, altitudeMeters: Double) {
        declination = runCatching {
            GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                altitudeMeters.toFloat(),
                System.currentTimeMillis()
            ).declination
        }.getOrDefault(0f)
    }

    /**
     * [r] maps phone axes to world axes (x = east, y = north, z = up);
     * column i is phone axis i expressed in world coordinates.
     */
    private fun headingFrom(r: FloatArray): Float? {

        // How flat the phone lies: z-axis (out of the screen) vs straight up.
        val screenFacingUp = abs(r[8])

        val east: Float
        val north: Float
        if (screenFacingUp >= 0.55f) {
            // Held flat-ish: the direction the top of the SCREEN points.
            val (x, y) = screenUpAxis()
            east = r[0] * x + r[1] * y
            north = r[3] * x + r[4] * y
        } else {
            // Held upright: the direction the back of the phone looks
            // (as when pointing the camera), i.e. minus the z-axis.
            east = -r[2]
            north = -r[5]
        }
        if (abs(east) < 1e-4f && abs(north) < 1e-4f) return null
        val degrees = Math.toDegrees(atan2(east.toDouble(), north.toDouble())).toFloat()
        return (degrees + 360f) % 360f
    }

    /** The phone axis (x, y) that's "up" on screen for the current display rotation. */
    private fun screenUpAxis(): Pair<Float, Float> {
        val displayRotation = runCatching {
            appContext.getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
        }.getOrNull() ?: Surface.ROTATION_0
        return when (displayRotation) {
            Surface.ROTATION_90 -> 1f to 0f     // turned left: the right edge is up
            Surface.ROTATION_180 -> 0f to -1f
            Surface.ROTATION_270 -> -1f to 0f
            else -> 0f to 1f
        }
    }
}
