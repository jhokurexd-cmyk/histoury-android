package com.histoury.app.util

/**
 * Smooths a stream of raw bearing readings (0°..360°) into a value that
 * eases toward each new reading instead of snapping straight to it, always
 * taking the shortest way around the compass — a reading like 359° -> 2°
 * eases forward through 0°, never spins the long way back through 180°.
 *
 * This mirrors the shortest-path smoothing already used for the AR
 * compass needle (`smoothAngle` / `rememberCompassHeading` in
 * ARExperienceScreen.kt), applied here to the live navigation map's camera
 * bearing instead of to a screen compass widget. Reusing the same formula
 * keeps the codebase's one already-proven approach to "smooth angle
 * following" instead of introducing a second, different one.
 *
 * An instance is meant to live for the duration of a navigation session
 * (held as a field on the ViewModel, not created fresh per update or per
 * recomposition) so its eased state carries over continuously between
 * location fixes instead of being reset each time.
 */
class BearingSmoother(
    private val smoothingFactor: Float = 0.22f,
    private val deadbandDegrees: Float = 4f
) {

    private var smoothedBearing: Float? = null

    /**
     * Feeds a new raw bearing reading and returns the updated smoothed
     * value. A reading that differs from the current smoothed value by
     * less than [deadbandDegrees] is treated as ordinary sensor/GPS noise
     * and ignored, so tiny fluctuations don't keep nudging the map.
     */
    fun update(rawBearingDegrees: Float): Float {

        val target = normalize(rawBearingDegrees)

        val current = smoothedBearing

        if (current == null) {
            // Nothing to ease from yet — start exactly at the first reading
            // rather than easing in from an arbitrary default.
            smoothedBearing = target
            return target
        }

        val delta = shortestDelta(current, target)

        if (kotlin.math.abs(delta) < deadbandDegrees) {
            return current
        }

        val next = normalize(current + delta * smoothingFactor)

        smoothedBearing = next

        return next
    }

    /** The current smoothed value without feeding in a new reading, or null if none yet. */
    fun current(): Float? = smoothedBearing

    /** Clears state, e.g. when a fresh navigation session starts. */
    fun reset() {
        smoothedBearing = null
    }

    companion object {

        private fun normalize(degrees: Float): Float = (degrees % 360f + 360f) % 360f

        /** Shortest signed angular distance from [from] to [to], in (-180, 180]. */
        private fun shortestDelta(from: Float, to: Float): Float {
            return (to - from + 540f) % 360f - 180f
        }
    }
}
