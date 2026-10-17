package com.histoury.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Histoury's motion tokens. One place for curves and durations so screens
 * don't each invent their own.
 *
 * Compose's built-in FastOutSlowIn is a weak ease-out; these are stronger
 * curves that make short UI motion read as quick and settled.
 */
object HistouryMotion {

    /** Entering / exiting / appearing: fast start, soft landing. */
    val EaseOut: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Something already on screen moving to a new place (e.g. Next → page). */
    val EaseInOut: Easing = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)

    /** Press feedback, small fades (Skip / Back appearing). */
    const val DurationShort = 160

    /** Standard UI: a label swap, an indicator, a small element entering. */
    const val DurationMedium = 250

    /** Larger, first-time moments (onboarding page move, first entrance). */
    const val DurationLong = 400
}

/**
 * True when the user turned animations off (Developer options / the
 * Accessibility "Remove animations" switch both set the animator duration
 * scale to 0).
 *
 * Compose already makes time-based animations finish instantly in that case.
 * This is for the motion Compose can't see: transforms driven directly by a
 * swipe (parallax, depth). Those are dropped; opacity is kept, so content
 * still fades rather than moving.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    }
}
