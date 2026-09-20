package com.histoury.app.ui.screens.ar

import com.histoury.app.data.model.ArSmokeSettings
import kotlin.math.sin

internal data class SmokePose(
    val x: Float, val y: Float, val z: Float, val size: Float, val opacity: Float
)

/** Six staggered cards per plume; no fluid simulation or growing particle allocations. */
internal object SmokeMotion {
    const val CARDS_PER_PLUME = 6
    const val MAX_CARDS = 18

    fun sample(settings: ArSmokeSettings, index: Int, seconds: Double): SmokePose {
        val plume = index / CARDS_PER_PLUME
        val emitter = settings.emitters[plume]
        val life = 7.0 + plume * 0.83
        val time = seconds.coerceAtLeast(0.0) / life +
            (index % CARDS_PER_PLUME).toDouble() / CARDS_PER_PLUME
        val age = time - kotlin.math.floor(time)
        val fadeIn = (age / 0.12).coerceIn(0.0, 1.0)
        val fadeOut = ((1.0 - age) / 0.38).coerceIn(0.0, 1.0)
        val opacity = fadeIn * fadeOut * settings.density * 0.85
        // Overlap adjacent cards so a tall plume reads as smoke rather than separate beads.
        val size = settings.widthMeters * (0.65 + age * 1.35) +
            settings.heightMeters / CARDS_PER_PLUME * 0.6
        return SmokePose(
            (emitter.rightMeters + settings.windRightMetersPerSecond * age * life +
                sin(age * 5.0 + index * 2.4) * age * settings.widthMeters * 0.2).toFloat(),
            (emitter.upMeters + age * settings.heightMeters + size * 0.2).toFloat(),
            (emitter.forwardMeters + settings.windForwardMetersPerSecond * age * life +
                sin(age * 4.0 + index * 1.7) * age * settings.widthMeters * 0.12).toFloat(),
            size.toFloat(), opacity.toFloat()
        )
    }
}
