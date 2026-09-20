package com.histoury.app.data.model

/** Metres in the placed model's axes, before its visual mesh normalization. */
data class ArSmokeEmitter(
    val rightMeters: Double = 0.0,
    val upMeters: Double = 0.0,
    val forwardMeters: Double = 0.0
)

/** Shared contract: ar_scenes.smoke and historical_sites.arSmoke. Old sites default off. */
data class ArSmokeSettings(
    val enabled: Boolean = false,
    val density: Double = 0.65,
    val heightMeters: Double = 4.0,
    val widthMeters: Double = 1.6,
    val windRightMetersPerSecond: Double = 0.25,
    val windForwardMetersPerSecond: Double = 0.0,
    val emitters: List<ArSmokeEmitter> = listOf(ArSmokeEmitter())
) {
    fun sanitized(): ArSmokeSettings = copy(
        density = density.bounded(0.65, 0.0, 1.0),
        heightMeters = heightMeters.bounded(4.0, 0.5, 15.0),
        widthMeters = widthMeters.bounded(1.6, 0.25, 6.0),
        windRightMetersPerSecond = windRightMetersPerSecond.bounded(0.25, -2.0, 2.0),
        windForwardMetersPerSecond = windForwardMetersPerSecond.bounded(0.0, -2.0, 2.0),
        emitters = emitters.take(3).map {
            ArSmokeEmitter(
                it.rightMeters.bounded(0.0, -100.0, 100.0),
                it.upMeters.bounded(0.0, -100.0, 100.0),
                it.forwardMeters.bounded(0.0, -100.0, 100.0)
            )
        }
    )
}

private fun Double.bounded(fallback: Double, min: Double, max: Double): Double =
    if (isFinite()) coerceIn(min, max) else fallback
