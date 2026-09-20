package com.histoury.app.data.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.location.Location
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ar.core.Session
import com.histoury.app.data.repository.SiteContentRepository
import com.histoury.app.data.model.SiteContent
import com.histoury.app.data.model.HistoricalSite
import com.histoury.app.data.model.anchorMode
import com.histoury.app.data.model.geospatialAnchoringAvailable
import com.histoury.app.data.model.hasGeospatialPlacement
import com.histoury.app.data.model.hasImagePlacement
import com.histoury.app.data.model.imageAnchoringAvailable
import com.histoury.app.data.repository.ArCaptureRepository
import com.histoury.app.data.repository.GeofenceRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Where the AR session is in its journey from "camera just opened" to
 * "the historical structure is standing over the real one".
 *
 * Positioning comes from recognising one of the structure's reference
 * photos, from ARCore's Geospatial API, or from whichever of the two locks
 * on first — as set per site in the admin panel ([HistoricalSite.arAnchorMode]).
 */
enum class ArStage {

    /** Fetching the site record, the reference photo and the model. */
    LOADING_SITE,

    /** Site has no usable AR content, or the device can't run AR at all. */
    UNAVAILABLE,

    /** Camera is up; no anchoring method has locked on yet. */
    SEARCHING,

    /** A method locked on; the model is being attached. */
    ANCHORING,

    /** Model is placed and visible. */
    PLACED,

    /**
     * The structure was recognized once but has since left the frame or
     * stopped being tracked. The model stays where it was last seen rather
     * than vanishing, but the overlay says so.
     */
    TRACKING_LOST,

    /**
     * Recognition never happened within the time budget. The visitor is
     * offered manual placement rather than an apology.
     */
    NOT_RECOGNIZED,

    /** Visitor is dragging the model into place by hand. */
    MANUAL_ALIGNING
}

/** What put the model where it is. */
enum class PlacementSource {
    /** A reference photo was recognised. */
    IMAGE,

    /** ARCore Geospatial resolved the stored latitude/longitude. */
    GEOSPATIAL,

    /** The visitor placed it by hand. */
    MANUAL
}

/** Live Geospatial localisation quality, for the searching card. */
data class GeoStatus(

    /** False until ARCore Earth is tracking at all. */
    val tracking: Boolean = false,

    /** Estimated horizontal error in metres, or null before tracking. */
    val horizontalAccuracyMeters: Double? = null,

    /** Estimated heading error in degrees, or null before tracking. */
    val headingAccuracyDegrees: Double? = null,

    /** True once accuracy is good enough to place the model. */
    val goodEnough: Boolean = false,

    /** Set when Earth itself can't run (bad API key, no permission, …). */
    val problem: String? = null
)

data class ArUiState(

    val stage: ArStage = ArStage.LOADING_SITE,

    val site: HistoricalSite? = null,

    /**
     * The same written content the Site Details page shows.
     *
     * Loaded alongside the site so the panel inside the camera can offer
     * the full overview, timeline, stories and sources rather than a
     * shortened summary. A visitor standing in front of the structure
     * should not see less than one reading about it at home.
     */
    val siteContent: SiteContent? = null,

    /** Which tab that panel is showing. */
    val infoTab: SiteDetailsTab = SiteDetailsTab.OVERVIEW,

    /** Human-readable reason shown when [stage] is UNAVAILABLE. */
    val unavailableReason: String = "",

    // ----- anchoring -----

    /** Recognise the structure from its reference photos. */
    val useImage: Boolean = false,

    /** Place the model from its stored latitude/longitude. */
    val useGeospatial: Boolean = false,

    /** Set once the model is placed. */
    val placedBy: PlacementSource? = null,

    /** Label of the reference photo that placed the model, when [placedBy] is IMAGE. */
    val matchedPhotoLabel: String = "",

    val geoStatus: GeoStatus = GeoStatus(),

    /** Seconds spent looking for the structure. */
    val searchingSeconds: Int = 0,

    /**
     * True once the model has been placed by hand rather than by
     * recognition, so the overlay can be honest about it.
     */
    val placementIsManual: Boolean = false,

    /** True while the visitor can still drag/rotate a manually placed model. */
    val manualPlacementUnlocked: Boolean = false,

    // ----- capture -----

    val isSavingCapture: Boolean = false,

    val captureMessage: String? = null,

    /**
     * Incremented by [ArExperienceViewModel.retrySearch]. The camera layer
     * keys its timeout clock on this so a retry restarts the search without
     * tearing down the ARCore session.
     */
    val retryToken: Int = 0
) {

    /**
     * How long to look before offering manual placement.
     *
     * Image recognition either fires within a few seconds of the structure
     * filling a reasonable part of the frame, or it isn't going to. Geospatial
     * localisation is slower — ARCore has to match the surrounding facades
     * against Street View imagery — so it gets a longer window.
     */
    val timeoutSeconds: Int
        get() = if (useGeospatial) GEOSPATIAL_TIMEOUT_SECONDS else RECOGNITION_TIMEOUT_SECONDS

    /**
     * Progress through the time budget, for the coaching bar. A patience
     * indicator rather than a measure of convergence.
     */
    val searchProgress: Float
        get() = (searchingSeconds.toFloat() / timeoutSeconds).coerceIn(0f, 1f)

    companion object {

        // 30s rather than 20s: a busy or poorly-lit site needs more wall-clock
        // time to accumulate enough clean, unobstructed frames for the
        // stabilizer to lock, even though the stabilizer itself already
        // tolerates individual bad frames just fine.
        const val RECOGNITION_TIMEOUT_SECONDS = 30

        const val GEOSPATIAL_TIMEOUT_SECONDS = 60
    }
}

class ArExperienceViewModel(application: Application) : AndroidViewModel(application) {

    private val historicalSiteRepository = HistoricalSiteRepository()
    private val arCaptureRepository = ArCaptureRepository()
    private val siteContentRepository = SiteContentRepository()
    private val geofenceRepository = GeofenceRepository()

    private val _uiState = MutableStateFlow(ArUiState())
    val uiState: StateFlow<ArUiState> = _uiState.asStateFlow()

    fun loadSite(siteId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(stage = ArStage.LOADING_SITE)

            // Arrival/navigation may carry either the Firestore document id
            // or the site's stable business id. Accept both, just like the
            // arrival flow, so a valid scene never becomes an empty screen.
            val site = historicalSiteRepository.getHistoricalSiteBySiteId(siteId)
            
            // Fetched here rather than when the panel opens, so tapping Info
            // is instant. The AR session takes seconds to recognise the
            // structure anyway — there is time to spare, and no reason to
            // make the visitor wait twice.
            val content = site?.siteId?.let {
                siteContentRepository.getSiteContent(it)
            }

            if (site == null) {
                _uiState.value = _uiState.value.copy(
                    stage = ArStage.UNAVAILABLE,
                    unavailableReason = "We couldn't load this site."
                )
                return@launch
            }

            if (!site.arEnabled || site.arModelUrl.isBlank()) {
                _uiState.value = _uiState.value.copy(
                    site = site,
                    siteContent = content,
                    stage = ArStage.UNAVAILABLE,
                    unavailableReason = "The AR experience for this site isn't ready yet."
                )
                return@launch
            }

            // Which methods can actually run. In "both" mode a half-set-up
            // method is simply skipped and the other one carries the scene,
            // matching the admin panel's "live if either is complete" rule.
            val useImage = site.imageAnchoringAvailable
            val useGeospatial = site.geospatialAnchoringAvailable

            if (!useImage && !useGeospatial) {
                val mode = site.anchorMode
                val reason = when {
                    mode.usesImage && !mode.usesGeospatial && !site.hasImagePlacement ->
                        "This site has no reference photo set, so the structure " +
                            "can't be recognized. Add one in the admin panel."
                    mode.usesGeospatial && !mode.usesImage && !site.hasGeospatialPlacement ->
                        "This site's AR location hasn't been set. Add its " +
                            "coordinates and model size in the admin panel."
                    else ->
                        "Neither AR method is set up for this site yet."
                }
                _uiState.value = _uiState.value.copy(
                    site = site,
                    siteContent = content,
                    stage = ArStage.UNAVAILABLE,
                    unavailableReason = reason
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                site = site,
                siteContent = content,
                useImage = useImage,
                useGeospatial = useGeospatial
            )
        }
    }

    /**
     * The authoritative check that this site's AR is allowed to arm at all:
     * is the visitor physically within ITS OWN geofence right now?
     *
     * Recognition is already scoped to one site's own reference photos (the
     * AugmentedImageDatabase for this screen is built only from
     * [HistoricalSite.referenceImages] for whichever site [loadSite]
     * resolved), so another site's photo can never be matched here by
     * itself. What this closes is the other half: whichever site got
     * resolved — by a tapped notification, a live arrival event, a deep
     * link, or content that was filed under the wrong site record —  its AR
     * still shouldn't be able to go live for someone who isn't standing
     * where that site actually is. Two nearby sites' geofences can be
     * dwelt in almost together, or a site's own data can simply be wrong;
     * either way, location is the one signal that isn't routed through any
     * of that and can be checked directly against the site's own
     * coordinates.
     *
     * A site with no geofence document yet fails OPEN (returns true) —
     * geofencing is a safety layer on top of AR, not a prerequisite for it,
     * so a site nobody has drawn a fence for yet should keep working the
     * way it always has.
     */
    suspend fun verifyGeofence(location: Location?): Boolean {

        val site = _uiState.value.site ?: return true

        val geofence = try {
            geofenceRepository.getGeofenceForSite(site.siteId)
        } catch (e: Exception) {
            null
        } ?: return true

        if (location == null) {
            _uiState.value = _uiState.value.copy(
                stage = ArStage.UNAVAILABLE,
                unavailableReason = "Turn on location access to use AR at ${site.siteName}."
            )
            return false
        }

        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            geofence.latitude, geofence.longitude,
            results
        )

        val withinRange = results[0] <= geofence.radius.toFloat().coerceAtLeast(10f)

        if (!withinRange) {
            _uiState.value = _uiState.value.copy(
                stage = ArStage.UNAVAILABLE,
                unavailableReason = "This AR experience is only available at " +
                    "${site.siteName}. Move closer to the site and open AR again."
            )
        }

        return withinRange
    }

    /**
     * Image recognition can't run after all (no photo could be loaded, or
     * ARCore rejected every one). Carries on with geospatial if that is also
     * set up; otherwise the experience is unavailable.
     */
    fun onImageAnchoringUnavailable(reason: String) {
        val state = _uiState.value
        if (!state.useImage) return
        _uiState.value = if (state.useGeospatial) {
            state.copy(useImage = false)
        } else {
            state.copy(stage = ArStage.UNAVAILABLE, unavailableReason = reason)
        }
    }

    /** Same as [onImageAnchoringUnavailable], for geospatial. */
    fun onGeospatialUnavailable(reason: String) {
        val state = _uiState.value
        if (!state.useGeospatial) return
        _uiState.value = when {
            state.useImage -> state.copy(
                useGeospatial = false,
                geoStatus = state.geoStatus.copy(problem = reason)
            )
            state.stage == ArStage.SEARCHING -> state.copy(
                stage = ArStage.NOT_RECOGNIZED,
                unavailableReason = reason,
                geoStatus = state.geoStatus.copy(problem = reason)
            )
            else -> state.copy(stage = ArStage.UNAVAILABLE, unavailableReason = reason)
        }
    }

    /** The reference photo and the model are both in memory; start looking. */
    fun onAssetsReady() {
        if (_uiState.value.stage == ArStage.LOADING_SITE) {
            _uiState.value = _uiState.value.copy(stage = ArStage.SEARCHING)
        }
    }

    /** Once per frame while searching: advances the clock and times out. */
    fun onSearchTick(elapsedSeconds: Int) {

        val state = _uiState.value

        if (state.stage != ArStage.SEARCHING || state.placementIsManual) return

        if (elapsedSeconds >= state.timeoutSeconds) {
            _uiState.value = state.copy(
                searchingSeconds = elapsedSeconds,
                stage = ArStage.NOT_RECOGNIZED
            )
        } else if (elapsedSeconds != state.searchingSeconds) {
            _uiState.value = state.copy(searchingSeconds = elapsedSeconds)
        }
    }

    fun onGeoStatus(status: GeoStatus) {
        if (_uiState.value.geoStatus != status) {
            _uiState.value = _uiState.value.copy(geoStatus = status)
        }
    }

    /**
     * A method has locked on. Returns true when the caller should create the
     * anchor — the anchor itself is made in the composable, where the
     * session and scene graph live.
     *
     * Only ever true while SEARCHING: the model is placed once and is then
     * world-locked, so nothing may re-place it (see ArCameraLayer).
     */
    fun beginAnchoring(@Suppress("UNUSED_PARAMETER") source: PlacementSource): Boolean {

        val state = _uiState.value

        if (state.placementIsManual || state.stage != ArStage.SEARCHING) return false

        _uiState.value = state.copy(stage = ArStage.ANCHORING)
        return true
    }

    /** An anchor request failed (e.g. a terrain anchor didn't resolve); keep looking. */
    fun cancelAnchoring() {
        val state = _uiState.value
        if (state.stage == ArStage.ANCHORING) {
            _uiState.value = state.copy(stage = ArStage.SEARCHING)
        }
    }

    fun onAnchorPlaced(source: PlacementSource, photoLabel: String = "") {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.PLACED,
            placedBy = source,
            matchedPhotoLabel = if (source == PlacementSource.IMAGE) photoLabel else ""
        )
    }

    /**
     * Whatever placed the model is still (or again) being tracked. The model
     * stays up either way; the overlay just says when it's coasting.
     */
    fun onPlacementTracking(tracking: Boolean) {

        val state = _uiState.value

        if (state.placementIsManual) return

        if (state.stage == ArStage.PLACED && !tracking) {
            _uiState.value = state.copy(stage = ArStage.TRACKING_LOST)
        } else if (state.stage == ArStage.TRACKING_LOST && tracking) {
            _uiState.value = state.copy(stage = ArStage.PLACED)
        }
    }

    fun onAnchorFailed(reason: String) {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.NOT_RECOGNIZED,
            unavailableReason = reason
        )
    }

    /**
     * Look again from scratch. Cheaper than leaving and reopening the screen,
     * which would rebuild the whole ARCore session and throw away the feature
     * map it has already built of the surroundings.
     */
    fun retrySearch() {

        val state = _uiState.value

        if (state.stage != ArStage.NOT_RECOGNIZED) return

        _uiState.value = state.copy(
            stage = ArStage.SEARCHING,
            searchingSeconds = 0,
            placementIsManual = false,
            manualPlacementUnlocked = false,
            placedBy = null,
            matchedPhotoLabel = "",
            retryToken = state.retryToken + 1
        )
    }

    /**
     * The escape hatch. Recognition depends on light, angle and an
     * unobstructed view, none of which are guaranteed on the day of a panel
     * defense. Manual placement is visibly cruder, but it always works.
     */
    fun startManualPlacement() {
        _uiState.value = _uiState.value.copy(
            stage = ArStage.MANUAL_ALIGNING,
            placementIsManual = true,
            manualPlacementUnlocked = true,
            placedBy = PlacementSource.MANUAL,
            matchedPhotoLabel = ""
        )
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

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isSavingCapture = true, captureMessage = null)

            val result = arCaptureRepository.saveCapture(bitmap = bitmap, siteId = siteId)

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

    /**
     * The screen copy itself failed, before anything was uploaded.
     *
     * Rare — a zero-size window, or PixelCopy refusing a secure surface —
     * but silently doing nothing is the worst possible response to a
     * deliberate button press.
     */
    fun onInfoTabSelected(tab: SiteDetailsTab) {
        _uiState.value = _uiState.value.copy(infoTab = tab)
    }

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
}
