package com.histoury.app.util

import android.location.Location
import com.histoury.app.data.repository.RouteStep
import com.histoury.app.data.repository.TravelMode
import kotlin.math.roundToInt

/**
 * Turns a route's maneuver/step list into turn-by-turn voice cues as the
 * user's live location moves along it.
 *
 * By Google Routes API convention, a step's own maneuver/instructions
 * describe what the traveler does AT THE START of that step (having just
 * finished the previous one), then the step's distance is traveled before
 * the next maneuver. So while the user is walking along step[i-1]'s path,
 * the maneuver they're approaching is step[i], and it happens at
 * step[i].startLocation (== step[i-1].endLocation).
 *
 * steps[0] is the initial "depart" leg — there is no maneuver before it —
 * so tracking always starts at index 1.
 *
 * This class only decides WHAT to say and WHEN; it has no dependency on
 * TextToSpeech, so it stays trivial to reason about (and test) on its own,
 * separate from [VoiceGuidanceManager]'s job of actually speaking.
 */
class RouteStepTracker {

    private var steps: List<RouteStep> = emptyList()

    // Index into [steps] of the maneuver currently being approached.
    private var pendingStepIndex: Int = 1

    private var farAnnounced = false

    private var nearAnnounced = false

    // Closest the traveler has come to the turn currently being approached.
    // A turn only counts as passed once they've actually been near it; this
    // stops a U-turn or sharp turn being "passed" from hundreds of metres
    // away (the point after it can be closer than the turn itself).
    private var closestToManeuver = Double.MAX_VALUE

    // How much warning a turn needs scales with how fast the traveler is
    // covering ground: a pedestrian can react and turn within a step or
    // two, but a driver needs enough distance to notice the cue, check
    // mirrors and signal *before* the turn — signaling only becomes
    // possible once the cue has been heard, so cutting it close means the
    // signal comes after the point where it should have started.
    private var travelMode: TravelMode = TravelMode.WALKING

    /** True once the whole maneuver list has been walked through. */
    fun isRouteComplete(): Boolean = steps.size <= 1 || pendingStepIndex >= steps.size

    /**
     * Replaces the tracked route and restarts progress from the top.
     * Call this once at the start of a navigation session, and again
     * whenever the routing system recomputes a genuinely different route
     * (a reroute) — but not on every routine refresh of an unchanged
     * route, since that would re-announce instructions already given.
     */
    fun setRoute(newSteps: List<RouteStep>, travelMode: TravelMode) {
        steps = newSteps
        this.travelMode = travelMode
        pendingStepIndex = 1
        farAnnounced = false
        nearAnnounced = false
        closestToManeuver = Double.MAX_VALUE
    }

    /**
     * Updates the announce-distance profile without touching tracked
     * progress — for when the traveler switches travel mode mid-route
     * (e.g. walking to driving) without the maneuver sequence itself
     * changing, which would otherwise leave the old mode's distances in
     * effect until the next reroute.
     */
    fun updateTravelMode(travelMode: TravelMode) {
        this.travelMode = travelMode
    }

    /**
     * Call on every live location update while navigation is active.
     * Returns a phrase to speak, or null when there is nothing new to say
     * for this update.
     */
    fun onLocationUpdate(userLatitude: Double, userLongitude: Double): String? {

        if (pendingStepIndex >= steps.size) {
            return null
        }

        val maneuverStep = steps[pendingStepIndex]
        val approachLeg = steps[pendingStepIndex - 1]

        val distanceToManeuver = distanceMeters(
            userLatitude, userLongitude,
            maneuverStep.startLatitude, maneuverStep.startLongitude
        )

        var toSpeak: String? = null

        // Only bother with an early "in X meters" warning when the leg
        // leading up to the maneuver is actually long enough for that to
        // make sense — otherwise it either never triggers (the leg is
        // shorter than the trigger distance) or fires almost on top of the
        // immediate call a few seconds later.
        val approachLegIsLongEnoughToWarnEarly =
            approachLeg.distanceMeters > (farAnnounceMeters + farAnnounceMarginMeters)

        if (!farAnnounced &&
            approachLegIsLongEnoughToWarnEarly &&
            distanceToManeuver <= farAnnounceMeters
        ) {
            farAnnounced = true
            val roundedMeters = roundToNiceMeters(distanceToManeuver)
            toSpeak = "In $roundedMeters meters, ${phraseFor(maneuverStep)}."
        } else if (!nearAnnounced && distanceToManeuver <= nearAnnounceMeters) {
            farAnnounced = true
            nearAnnounced = true
            toSpeak = capitalize(phraseFor(maneuverStep)) + "."
        }

        advanceIfManeuverPassed(userLatitude, userLongitude, maneuverStep, distanceToManeuver)

        return toSpeak
    }

    /** The maneuver the traveler is currently heading toward, or null once all are passed. */
    fun upcomingManeuver(): RouteStep? = steps.getOrNull(pendingStepIndex)

    /** The maneuver right after [upcomingManeuver] — the navigation banner's "Then" hint. */
    fun maneuverAfterUpcoming(): RouteStep? = steps.getOrNull(pendingStepIndex + 1)

    /** Spoken once the destination geofence itself reports arrival. */
    fun announceArrival(): String = "You have arrived at your destination."

    private fun advanceIfManeuverPassed(
        userLatitude: Double,
        userLongitude: Double,
        maneuverStep: RouteStep,
        distanceToManeuver: Double
    ) {

        val distancePastManeuver = distanceMeters(
            userLatitude, userLongitude,
            maneuverStep.endLatitude, maneuverStep.endLongitude
        )

        closestToManeuver = minOf(closestToManeuver, distanceToManeuver)

        // A fix this close to the turn counts as being at it. Wider for
        // vehicles: their fixes are 20-50 m apart, so one rarely lands
        // within a few metres of the corner.
        val hasReachedManeuver = distanceToManeuver <= maneuverReachedMeters

        // The tests below only apply once the traveler has really been near
        // the turn. Without this, the point AFTER a U-turn or sharp turn
        // (which lies back towards the traveler) made the turn count as
        // passed while it was still far ahead.
        val hasBeenNearManeuver = closestToManeuver <= overshootGateMeters

        // Closer to the point AFTER the maneuver than to the maneuver point
        // itself: already passed it, even if no fix landed right on it.
        val hasOvershotManeuver = hasBeenNearManeuver && distancePastManeuver < distanceToManeuver

        // Was near the turn and is now clearly moving away from it: passed.
        // Without this a long road after the turn kept showing the old turn
        // until the traveler was halfway along it.
        val isMovingAwayFromManeuver = hasBeenNearManeuver &&
            distanceToManeuver > closestToManeuver + maneuverReachedMeters

        if (hasReachedManeuver || hasOvershotManeuver || isMovingAwayFromManeuver) {
            pendingStepIndex += 1
            farAnnounced = false
            nearAnnounced = false
            closestToManeuver = Double.MAX_VALUE
        }
    }

    // How close a fix must be to a turn to count as having reached it.
    private val maneuverReachedMeters: Double
        get() = when (travelMode) {
            TravelMode.WALKING, TravelMode.COMMUTE -> 12.0
            TravelMode.MOTORCYCLE, TravelMode.DRIVING -> 30.0
        }

    // How close the traveler must have come to a turn before "passed it"
    // is judged from the points around it: the immediate-cue distance, but
    // never under 40 m, so a walker whose GPS cuts a wide corner by more
    // than 15 m isn't stuck on that turn for the rest of the trip.
    private val overshootGateMeters: Double
        get() = maxOf(nearAnnounceMeters, 40.0)

    // Distance at which the early "in X meters, turn..." cue fires.
    // Scaled to give a driver enough runway to notice the cue and signal
    // before the maneuver, not just react to it as it happens.
    private val farAnnounceMeters: Double
        get() = when (travelMode) {
            TravelMode.WALKING, TravelMode.COMMUTE -> 100.0
            TravelMode.MOTORCYCLE -> 200.0
            TravelMode.DRIVING -> 300.0
        }

    // How much longer than farAnnounceMeters an approach leg must be for
    // the early cue to be worth giving at all, so it doesn't collide with
    // the immediate cue a few seconds later on a short leg.
    private val farAnnounceMarginMeters: Double
        get() = when (travelMode) {
            TravelMode.WALKING, TravelMode.COMMUTE -> 30.0
            TravelMode.MOTORCYCLE -> 60.0
            TravelMode.DRIVING -> 80.0
        }

    // Distance at which the immediate "turn left." cue fires. This is the
    // traveler's actual advance notice on any leg too short for the early
    // cue above, so it has to be far enough out that there's still time to
    // signal and position before reaching the maneuver — 15m gives a
    // pedestrian several seconds, but is under two seconds of warning at
    // driving speed, which is the "voice disappeared" complaint this
    // fixes: the cue was firing at essentially the same moment as the
    // turn itself.
    private val nearAnnounceMeters: Double
        get() = when (travelMode) {
            TravelMode.WALKING, TravelMode.COMMUTE -> 15.0
            TravelMode.MOTORCYCLE -> 40.0
            TravelMode.DRIVING -> 60.0
        }

    private fun roundToNiceMeters(distanceMeters: Double): Int {
        val rounded = (distanceMeters / 50.0).roundToInt() * 50
        return rounded.coerceAtLeast(50)
    }

    private fun capitalize(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    /**
     * A commute ride is announced with Google's own wording ("Light rail
     * towards Baclaran") — "continue straight" would be meaningless there.
     */
    private fun phraseFor(step: RouteStep): String {
        val ride = step.transit
        return when {
            ride != null -> buildString {
                append("take ").append(ride.label)
                ride.headsign?.let { append(" towards ").append(it) }
                ride.departureStop?.let { append(" from ").append(it) }
            }
            step.isTransitRide && step.instructionText.isNotBlank() ->
                step.instructionText.trim().trimEnd('.')
            else -> describeManeuver(step.maneuver)
        }
    }

    companion object {

        /** Plain-language wording for a Routes API maneuver, e.g. "turn left". */
        fun describeManeuver(maneuver: String): String = when (maneuver.uppercase()) {
        "TURN_LEFT" -> "turn left"
        "TURN_RIGHT" -> "turn right"
        "TURN_SHARP_LEFT" -> "turn sharp left"
        "TURN_SHARP_RIGHT" -> "turn sharp right"
        "TURN_SLIGHT_LEFT", "FORK_LEFT" -> "keep left"
        "TURN_SLIGHT_RIGHT", "FORK_RIGHT" -> "keep right"
        "UTURN_LEFT", "UTURN_RIGHT" -> "make a U-turn"
        "ROUNDABOUT_LEFT", "ROUNDABOUT_RIGHT" -> "enter the roundabout"
        "RAMP_LEFT" -> "take the ramp on the left"
        "RAMP_RIGHT" -> "take the ramp on the right"
        "MERGE" -> "merge"
        "FERRY", "FERRY_TRAIN" -> "take the ferry"
        else -> "continue straight"
        }
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, results)
        return results[0].toDouble()
    }

}
