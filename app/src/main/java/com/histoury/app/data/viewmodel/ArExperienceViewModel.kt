package com.histoury.app.data.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Session
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.data.model.Geofence
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.repository.ArCaptureRepository
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the AR session is in its journey from "camera just opened" to
 * "the historical structure is standing in front of the visitor".
 *
 * Positioning is MANUAL ONLY. The visitor aims at a detected floor and taps
 * to place; nothing in the app decides where in the camera view the model
 * belongs. ARCore Geospatial, VPS and Augmented Image recognition were all
 * removed deliberately — they decided placement from coordinates or from a
 * reference photo, and both proved unreliable in the field.
 *
 * The site's geofence is a separate concern and is still very much alive.
 * It decides WHETHER this site's AR may run at all; manual placement decides
 * WHERE the model goes. Those two responsibilities never mix: no latitude or
 * longitude reaches the model's transform.
 */
enum class ArStage {

    /** Fetching the site record, its written content and the model. */
    LOADING_SITE,

    /** Site has no usable AR content, or the device can't run AR at all. */
    UNAVAILABLE,

    /**
     * The visitor is not standing inside THIS site's geofence, so its AR is
     * not allowed to arm. Kept separate from [UNAVAILABLE] because it is not
     * a fault — the experience is fine, the visitor is simply somewhere else
     * — and the screen offers "View on Map" rather than an apology.
     */
    OUTSIDE_GEOFENCE,

    /**
     * Camera is up and the visitor is placing the structure by hand: find a
     * floor, aim, tap to place, then drag, pinch and rotate until it sits
     * right.
     */
    MANUAL_ALIGNING,

    /** "Lock in Place" was pressed; position, rotation and scale are frozen. */
    PLACED
}

data class ArUiState(

    val stage: ArStage = ArStage.LOADING_SITE,

    val site: HistoricalSite? = null,

    /**
     * The same written content the Site Details page shows.
     *
     * Loaded alongside the site so tapping Info inside the camera is
     * instant. A visitor standing in front of the structure should not see
     * less than one reading about it at home.
     */
    val siteContent: SiteContent? = null,

    /** Which tab that panel is showing. */
    val infoTab: SiteDetailsTab = SiteDetailsTab.OVERVIEW,

    /** Human-readable reason shown when [stage] is UNAVAILABLE. */
    val unavailableReason: String = "",

    /**
     * True when UNAVAILABLE came from a failed load (usually no signal)
     * rather than the site genuinely having no AR — the screen then offers
     * Try again instead of only Go back.
     */
    val unavailableRetryable: Boolean = false,

    /**
     * Why placement is blocked when [stage] is OUTSIDE_GEOFENCE, phrased for
     * a visitor rather than an engineer.
     */
    val outsideGeofenceReason: String = "",

    /**
     * True once ARCore has found a horizontal surface worth aiming at, so
     * the overlay can stop saying "move your phone slowly" and start showing
     * the placement grid.
     */
    val groundFound: Boolean = false,

    /** True while the visitor can still drag, pinch and rotate the model. */
    val manualPlacementUnlocked: Boolean = true,

    /**
     * Set when the visitor has walked out of the site's geofence and stayed
     * out long enough that it isn't GPS jitter.
     *
     * This never tears down a model that is already standing — doing that on
     * a wobbly fix would be far worse than letting someone finish looking at
     * a structure from a few metres outside the line. It only stops a NEW
     * placement from being made, and tells the visitor why.
     */
    val leftSiteArea: Boolean = false,

    // ----- capture -----

    val isSavingCapture: Boolean = false,

    val captureMessage: String? = null
)

/**
 * The "How was your experience?" prompt shown when leaving AR after the
 * model was actually placed. What it writes is an ordinary site review.
 */
data class ArRatingPromptState(
    /** A model was placed during this AR session. Opening AR alone doesn't count. */
    val modelPlaced: Boolean = false,
    val isVisible: Boolean = false,
    /** The visitor's existing review of this site, to prefill and update rather than duplicate. */
    val existingReview: com.histoury.app.data.model.Review? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    /** Answered or dismissed this session; Back now simply leaves. */
    val handled: Boolean = false
)

class ArExperienceViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()
    private val arCaptureRepository = ArCaptureRepository()
    private val siteContentRepository = SiteContentRepository()
    private val geofenceRepository = GeofenceRepository()

    private val _uiState = MutableStateFlow(ArUiState())
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    /**
     * This site's own geofence, resolved once by [verifyGeofence] and then
     * reused for the cheap re-checks that run while AR is open.
     *
     * Cached deliberately: the boundary check runs every few seconds for as
     * long as the camera is up, and a Firestore round trip per check would
     * be both slow and pointless — a geofence does not move while somebody
     * is standing in it.
     */
    private var siteGeofence: Geofence? = null

    /**
     * True once [verifyGeofence] has actually looked, so a re-check can tell
     * "this site has no fence" apart from "nobody has asked yet".
     */
    private var geofenceResolved = false

    fun loadSite(siteId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                stage = ArStage.LOADING_SITE,
                unavailableRetryable = false
            )

            // Arrival/navigation may carry either the Firestore document id
            // or the site's stable business id. Accept both, just like the
            // arrival flow, so a valid scene never becomes an empty screen.
            val site = historicalSiteRepository.getHistoricalSiteBySiteId(siteId)

            val content = site?.siteId?.let {
                siteContentRepository.getSiteContent(it)
            }

            if (site == null) {
                _uiState.value = _uiState.value.copy(
                    stage = ArStage.UNAVAILABLE,
                    unavailableReason = "We couldn't load this site. Check your connection and try again.",
                    unavailableRetryable = true
                )
                return@launch
            }

            // The only two things manual placement needs: the feature turned
            // on, and a model to place. Reference photos and AR coordinates
            // are no longer consulted at all — a site that has neither is now
            // perfectly playable, which is the point of the change.
            if (!site.arEnabled || site.arModelUrl.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    site = site,
                    siteContent = content,
                    stage = ArStage.UNAVAILABLE,
                    unavailableReason = "The AR experience for this site isn't ready yet."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                site = site,
                siteContent = content
            )
        }
    }

    /**
     * The authoritative check that this site's AR is allowed to arm at all:
     * is the visitor physically within ITS OWN geofence right now?
     *
     * The geofence is looked up by the site's own id, never by proximity to
     * "any" fence. That distinction is the whole point in a place like
     * Intramuros, where several sites sit within a short walk of each other:
     * standing in Fort Santiago must never unlock Manila Cathedral's model,
     * and it cannot, because the only fence consulted here is the one whose
     * siteId matches the site this screen resolved.
     *
     * A site with no geofence document yet fails OPEN (returns true).
     * Geofencing is a gate in front of AR, not a prerequisite for it, so a
     * site nobody has drawn a fence for keeps working rather than silently
     * dying. That is a deliberate trade — if every site must be fenced
     * before its AR can run, this is the single line to change.
     */
    suspend fun verifyGeofence(location: Location?): Boolean {

        val site = _uiState.value.site ?: return true

        if (!ensureGeofenceResolved()) return true

        if (location == null) {
            _uiState.value = _uiState.value.copy(
                stage = ArStage.OUTSIDE_GEOFENCE,
                outsideGeofenceReason = "Histoury needs your location to confirm you're at " +
                    "${site.siteName}. Turn on location access and open AR again."
            )
            return false
        }

        if (isWithinGeofence(location)) return true

        onOutsideGeofence()

        return false
    }

    /**
     * Resolves and caches this site's fence, returning whether it has one.
     *
     * Split out of [verifyGeofence] so the screen's gate can settle the
     * fence first and only then start spending fixes on it — there is no
     * point asking the GPS anything for a site nobody has fenced.
     *
     * Idempotent: the lookup runs once per screen and the answer is reused,
     * for the same reason the cache existed before. A failed lookup is not
     * cached as "no fence" — [geofenceResolved] is only set once a real
     * answer came back, so a transient Firestore error is retried rather
     * than silently disabling the gate for the rest of the session.
     */
    suspend fun ensureGeofenceResolved(): Boolean {

        if (geofenceResolved) return siteGeofence != null

        val site = _uiState.value.site ?: return false

        val geofence = try {
            geofenceRepository.getGeofenceForSite(site.siteId)
        } catch (e: Exception) {
            return false
        }

        // Checked against the site's own coordinates when it has them —
        // the same point arrival detection and the Map route use — so AR
        // can't refuse someone the arrival screen has just welcomed.
        val lat = site.latitude
        val lng = site.longitude
        siteGeofence = if (geofence != null && lat != null && lng != null) {
            geofence.copy(latitude = lat, longitude = lng)
        } else {
            geofence
        }
        geofenceResolved = true

        return geofence != null
    }

    /**
     * The gate has spent every fix it is going to and none of them put the
     * visitor inside the fence.
     */
    fun onOutsideGeofence() {

        val siteName = _uiState.value.site?.siteName.orEmpty()

        _uiState.value = _uiState.value.copy(
            stage = ArStage.OUTSIDE_GEOFENCE,
            outsideGeofenceReason = "Travel Back in Time is only available when you are at " +
                "$siteName. Move closer to the historical site and try again."
        )
    }

    /**
     * Location is permitted but the device produced no usable fix at all.
     *
     * Deliberately worded as a GPS problem rather than a "you are in the
     * wrong place" one, because it is: under an arcade or against a wall
     * the radio can simply fail to deliver anything, and telling somebody
     * standing in front of the structure to move closer to it is both wrong
     * and infuriating. The screen pairs this with a Try again button.
     */
    fun onLocationUnavailable() {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.OUTSIDE_GEOFENCE,
            outsideGeofenceReason = "Histoury couldn't get a reliable GPS reading here. " +
                "Step into the open for a moment, then try again."
        )
    }

    /**
     * A Try again press: drop back to checking so the screen shows progress
     * rather than leaving the refusal on display while the recheck runs.
     */
    fun onGeofenceRecheckStarted() {
        if (_uiState.value.stage != ArStage.OUTSIDE_GEOFENCE) return
        _uiState.value = _uiState.value.copy(
            stage = ArStage.LOADING_SITE,
            outsideGeofenceReason = ""
        )
    }

    /**
     * Cheap, allocation-light re-check against the already-resolved fence,
     * for the boundary watch that runs while the camera is open.
     *
     * Returns true when there is nothing to be outside of — no fence, or a
     * fence that was never resolved — so an unfenced site is never nagged.
     */
    fun isWithinGeofence(location: Location): Boolean {

        val geofence = siteGeofence ?: return true

        val results = FloatArray(1)

        Location.distanceBetween(
            location.latitude, location.longitude,
            geofence.latitude, geofence.longitude,
            results
        )

        // The floor matters: an admin can save a radius of 0 or a couple of
        // metres, which no consumer GPS fix could ever satisfy, and that
        // would lock the visitor out of a site they are standing inside.
        val effectiveRadius = geofence.radius.toFloat()
            .coerceAtLeast(MINIMUM_GEOFENCE_RADIUS_METERS)

        // A fix is not a point, it is a point plus a radius of uncertainty,
        // and comparing only the point against the fence was what locked
        // visitors out of sites they were standing inside.
        //
        // Intramuros is close to the worst case for this: narrow streets
        // between high stone walls, so satellites are occluded on most
        // bearings and the ones still visible arrive via reflections.
        // Reported accuracy of twenty to fifty metres is routine there, and
        // against a thirty-metre fence that alone scatters readings well
        // past the boundary while the phone has not moved at all.
        //
        // So the fix counts as inside when its uncertainty circle reaches
        // the fence, not only when its centre does. That is the same
        // reading of a fix that Android's own geofencing uses, and it
        // resolves ambiguity towards admitting the visitor — which is the
        // right way round here, since the fence guards a history lesson
        // rather than anything that matters if it is wrong.
        return results[0] - accuracyAllowance(location) <= effectiveRadius
    }

    /**
     * How much of a fix's reported uncertainty to forgive, in metres.
     *
     * Capped, because an uncapped allowance would let a genuinely distant
     * visitor in on the strength of a bad enough fix — a reading 300 m out
     * with 300 m of claimed accuracy is not evidence of standing at the
     * site. Past the cap the fix is better treated as evidence of nothing,
     * which is what [isFixTrustworthy] is for.
     *
     * A fix carrying no accuracy at all is rare and gets a modest fixed
     * allowance rather than none: zero would resolve the unknown towards
     * locking somebody out, which is the failure this whole change exists
     * to stop.
     */
    private fun accuracyAllowance(location: Location): Float =
        if (location.hasAccuracy()) {
            location.accuracy.coerceIn(0f, MAX_ACCURACY_ALLOWANCE_METERS)
        } else {
            UNKNOWN_ACCURACY_ALLOWANCE_METERS
        }

    /**
     * Whether a fix is precise enough to be worth drawing a conclusion from.
     *
     * Only ever used to DISCARD evidence, never to admit anyone: a fix this
     * vague cannot show somebody is outside the fence, so the boundary watch
     * skips it instead of counting it towards an exit. A vague fix that
     * nonetheless lands inside is still accepted, because it agrees with the
     * benefit of the doubt the gate already gives.
     */
    fun isFixTrustworthy(location: Location): Boolean =
        !location.hasAccuracy() || location.accuracy <= MAX_TRUSTED_ACCURACY_METERS

    /** Whether this site is fenced at all, once [verifyGeofence] has looked. */
    fun hasResolvedGeofence(): Boolean = geofenceResolved && siteGeofence != null

    /**
     * The visitor has been outside the fence for long enough that it is a
     * walk rather than a wobble. Blocks new placements; never disturbs a
     * model that is already standing.
     */
    fun onSustainedGeofenceExit() {
        if (_uiState.value.leftSiteArea) return
        _uiState.value = _uiState.value.copy(leftSiteArea = true)
    }

    /** They came back inside. Placement is allowed again. */
    fun onGeofenceReentered() {
        if (!_uiState.value.leftSiteArea) return
        _uiState.value = _uiState.value.copy(leftSiteArea = false)
    }

    /**
     * The model is in memory and the geofence check has passed; hand the
     * visitor straight to manual placement.
     *
     * There is no search phase any more. Nothing is being recognised and
     * nothing is being localised, so there is nothing to wait for beyond
     * ARCore finding a floor — which the overlay coaches separately.
     */
    fun onAssetsReady() {
        if (_uiState.value.stage == ArStage.LOADING_SITE) {
            _uiState.value = _uiState.value.copy(
                stage = ArStage.MANUAL_ALIGNING,
                manualPlacementUnlocked = true
            )
        }
    }

    // ------------------------------------------------ post-AR rating prompt

    private val reviewRepository = com.histoury.app.data.repository.ReviewRepository()

    private val _ratingPrompt = MutableStateFlow(ArRatingPromptState())
    val ratingPrompt: StateFlow<ArRatingPromptState> = _ratingPrompt.asStateFlow()

    /** The model is standing in the scene for the first time this session. */
    fun onModelPlaced() {
        if (_ratingPrompt.value.modelPlaced) return
        _ratingPrompt.value = _ratingPrompt.value.copy(modelPlaced = true)
        // Experiencing a site in AR counts as visiting it: record the visit
        // (de-duplicated with the arrival's own record within 30 minutes),
        // which also refreshes Visit History wherever it's open.
        _uiState.value.site?.let { site ->
            viewModelScope.launch {
                com.histoury.app.data.repository.VisitHistoryRepository()
                    .recordVisitIfNotRecent(site.siteId.ifBlank { site.documentId })
            }
        }
        // Look up their review now, while they're looking at the model, so
        // the prompt opens prefilled with no wait.
        val siteId = _uiState.value.site?.siteId ?: return
        viewModelScope.launch {
            val existing = reviewRepository.getMyReviewForSite(siteId)
            _ratingPrompt.value = _ratingPrompt.value.copy(existingReview = existing)
        }
    }

    /**
     * Called on Back. Returns true when the prompt was shown instead of
     * leaving, false when the screen should just close.
     *
     * The prompt appears only if a model was actually placed during this AR
     * session (opening the camera and backing out doesn't count), and only
     * once per session. A visitor who already reviewed the site is offered
     * to update that review rather than write a second one.
     */
    fun onExitRequested(): Boolean {
        val prompt = _ratingPrompt.value
        if (!prompt.modelPlaced || prompt.handled || prompt.isVisible) return false
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return false
        if (_uiState.value.site?.siteId.isNullOrBlank()) return false

        _ratingPrompt.value = prompt.copy(isVisible = true, errorMessage = null)
        return true
    }

    /** "Not now" / close: the visitor leaves AR; nothing is written. */
    fun declineRating() {
        _ratingPrompt.value = _ratingPrompt.value.copy(isVisible = false, handled = true)
    }
    fun submitRating(rating: Int, text: String, onSaved: (String) -> Unit) {

        val prompt = _ratingPrompt.value
        val siteId = _uiState.value.site?.siteId ?: return
        if (prompt.isSubmitting) return

        _ratingPrompt.value = prompt.copy(isSubmitting = true, errorMessage = null)

        viewModelScope.launch {
            val result = reviewRepository.saveSiteReview(
                siteId = siteId,
                rating = rating,
                reviewText = text,
                source = com.histoury.app.data.repository.ReviewSource.AR_PROMPT
            )
            when (result) {
                is com.histoury.app.data.repository.ReviewActionResult.Success -> {
                    _ratingPrompt.value = _ratingPrompt.value.copy(
                        isSubmitting = false,
                        isVisible = false,
                        handled = true
                    )
                    onSaved(if (result.value.updated) "Your review was updated." else "Thanks for your review!")
                }
                is com.histoury.app.data.repository.ReviewActionResult.Failed -> {
                    // Stays open with the message, so nothing typed is lost.
                    _ratingPrompt.value = _ratingPrompt.value.copy(
                        isSubmitting = false,
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    /** ARCore has (or has lost) a horizontal surface worth aiming at. */
    fun onGroundFound(found: Boolean) {
        if (_uiState.value.groundFound != found) {
            _uiState.value = _uiState.value.copy(groundFound = found)
        }
    }

    fun lockManualPlacement() {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.PLACED,
            manualPlacementUnlocked = false
        )
    }

    fun unlockManualPlacement() {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.MANUAL_ALIGNING,
            manualPlacementUnlocked = true
        )
    }

    /** Start over: back to aiming, with nothing placed. */
    fun resetManualPlacement() {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.MANUAL_ALIGNING,
            manualPlacementUnlocked = true
        )
    }

    fun onSessionFailed(message: String) {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.UNAVAILABLE,
            unavailableReason = message
        )
    }

    fun saveCapture(bitmap: Bitmap) {

        // The business slug, not the Firestore document id.
        //
        // Everything else that references a site — visit_history, the
        // gallery's own caption lookup, geofences — keys on the slug, so
        // storing the document id here meant the Gallery screen's
        // siteNames[photo.siteId] lookup always missed and fell back to
        // printing the raw id under each photo.
        val siteId = _uiState.value.site?.siteId ?: return

        // A second Save tap while the first upload runs used to store the
        // same photo twice.
        if (_uiState.value.isSavingCapture) return

        _uiState.value = _uiState.value.copy(isSavingCapture = true, captureMessage = null)

        viewModelScope.launch {

            // Saved on the phone, not uploaded (see ArCaptureRepository).
            val result = arCaptureRepository.saveCapture(
                context = getApplication(),
                bitmap = bitmap,
                siteId = siteId
            )

            _uiState.value = _uiState.value.copy(
                isSavingCapture = false,
                captureMessage = when (result) {
                    is ArCaptureRepository.Result.Success -> "Saved to your Gallery"
                    is ArCaptureRepository.Result.NotSignedIn -> "Sign in to save photos."
                    is ArCaptureRepository.Result.Failed -> result.reason
                }
            )
        }
    }

    fun onInfoTabSelected(tab: SiteDetailsTab) {
        _uiState.value = _uiState.value.copy(infoTab = tab)
    }

    /**
     * The screen copy itself failed, before anything was uploaded.
     *
     * Rare — a zero-size window, or PixelCopy refusing a secure surface —
     * but silently doing nothing is the worst possible response to a
     * deliberate button press.
     */
    fun onCaptureFailed() {
        _uiState.value = _uiState.value.copy(
            isSavingCapture = false,
            captureMessage = "Couldn't take that photo. Try again."
        )
    }

    fun clearCaptureMessage() {
        _uiState.value = _uiState.value.copy(captureMessage = null)
    }

    /**
     * ARCore hands back a Session only after its own install and permission
     * checks pass, so a null session here means the device is out.
     */
    fun onSessionCreated(session: Session?) {
        if (session == null) {
            onSessionFailed("This device doesn't support AR.")
        }
    }

    private companion object {

        /**
         * No consumer GPS fix is trustworthy to within a couple of metres,
         * so a fence smaller than this would lock out the very visitors it
         * is meant to admit.
         */
        const val MINIMUM_GEOFENCE_RADIUS_METERS = 15f

        /**
         * Ceiling on the uncertainty forgiven by [accuracyAllowance].
         *
         * Set at the point where a fix stops being a slightly blurred
         * position and starts being a guess: fifty metres is a bad urban
         * fix, several hundred is a cell-tower estimate that says little
         * more than which district the phone is in.
         */
        const val MAX_ACCURACY_ALLOWANCE_METERS = 50f

        /** Allowance for a fix that reports no accuracy at all. */
        const val UNKNOWN_ACCURACY_ALLOWANCE_METERS = 20f

        /**
         * Past this, a fix is not evidence of anything and the boundary
         * watch ignores it rather than counting it towards an exit.
         */
        const val MAX_TRUSTED_ACCURACY_METERS = 75f
    }
}
