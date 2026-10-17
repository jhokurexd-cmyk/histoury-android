package com.histoury.app.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Shared between MainActivity (which owns the picture-in-picture window) and
 * MapScreen (which knows whether turn-by-turn navigation is running).
 *
 * The floating mini map is Android's picture-in-picture mode: while
 * navigating, leaving the app (Home, recents, or the mini-map button)
 * shrinks the whole activity into a small window that floats over other
 * apps and keeps following the route, with voice guidance still speaking.
 */
object NavigationPipState {

    /** True while MapScreen is actively navigating — the only time PiP is offered. */
    val isNavigating = MutableStateFlow(false)

    /** True while the activity is shown as the floating mini window. */
    val isInPip = MutableStateFlow(false)

    private val _pipDismissed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * Fires when the visitor closes the floating window (the X), as opposed
     * to expanding it back to full screen. Navigation carries on (the
     * foreground service keeps location live, as Google Maps does); this is
     * kept for anything that wants to react to the window closing.
     */
    val pipDismissed: SharedFlow<Unit> = _pipDismissed

    fun notifyPipDismissed() {
        _pipDismissed.tryEmit(Unit)
    }

    /**
     * The line shown under "Navigating to …" in the ongoing notification,
     * e.g. "4 min · 300 m". Written by MapViewModel, read by
     * NavigationForegroundService.
     */
    val navigationStatus = MutableStateFlow<String?>(null)

    private val _endNavigationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Fires when the visitor taps "End" on the navigation notification. */
    val endNavigationRequests: SharedFlow<Unit> = _endNavigationRequests

    fun requestEndNavigation() {
        _endNavigationRequests.tryEmit(Unit)
    }
}
