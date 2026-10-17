package com.histoury.app.navigation

import androidx.navigation.NavController

/**
 * Switches to one of the five bottom-bar tabs the same way the bar itself
 * does: Home stays the root, each tab's own back stack/scroll position is
 * saved and restored, and re-selecting never stacks duplicates.
 *
 * Other screens that send the visitor "to a tab" (Home's Resume Trip,
 * Arrival's Back to Itinerary) used plain navigate() calls instead, which
 * pushed a second, fresh copy of the tab on top — so the bottom bar and the
 * Back button then disagreed about where the visitor was.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.Home.route) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

// ----------------------------------------------------------------------------
// Double-tap protection
//
// A quick double tap on Back, or on a button that opens a screen, ran the
// action twice: Back popped two screens (and popping the last one left a
// blank screen), and "open X" put two copies of X on the stack. These two
// wrappers make the second tap a no-op; every screen uses them instead of
// calling popBackStack() / navigate() directly.
// ----------------------------------------------------------------------------

private const val DUPLICATE_NAVIGATION_WINDOW_MS = 700L

private var lastNavigationRoute: String? = null
private var lastNavigationAtMs: Long = 0L

/**
 * popBackStack() that ignores a second press while the screen being left is
 * already on its way out (no longer RESUMED), and never pops the last screen
 * in the app off the stack.
 */
fun NavController.safePopBackStack(): Boolean {
    val current = currentBackStackEntry ?: return false
    if (current.lifecycle.currentState != androidx.lifecycle.Lifecycle.State.RESUMED) return false
    if (previousBackStackEntry == null) return false
    return popBackStack()
}

/**
 * navigate() that drops a repeat of the SAME route within a moment of the
 * first — a double tap — while leaving every other navigation untouched.
 */
fun NavController.navigateSafely(
    route: String,
    builder: androidx.navigation.NavOptionsBuilder.() -> Unit = {}
) {
    val now = android.os.SystemClock.elapsedRealtime()
    if (route == lastNavigationRoute && now - lastNavigationAtMs < DUPLICATE_NAVIGATION_WINDOW_MS) return
    lastNavigationRoute = route
    lastNavigationAtMs = now
    navigate(route, builder)
}
