package com.histoury.app.data.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.histoury.app.data.model.Gallery
import com.histoury.app.data.repository.DeviceGalleryRepository
import com.histoury.app.data.repository.GalleryRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

data class GalleryUiState(
    val isLoading: Boolean = false,
    val photos: List<Gallery> = emptyList(),
    // siteId (business slug) -> siteName, for the caption on each photo tile
    val siteNames: Map<String, String> = emptyMap(),

    /**
     * Which photos the visitor has picked out, and whether picking is
     * happening at all.
     *
     * The flag is separate rather than inferred from the set being
     * non-empty, so deselecting the last photo leaves the visitor in
     * selection mode with nothing selected instead of dropping them out of
     * it mid-task — which would feel like the screen had cancelled on them.
     */
    val selectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),

    /** True while a save or delete runs, so the action bar can't be retapped. */
    val isWorking: Boolean = false,

    /** Photos being saved to the device right now; their Download button shows a spinner and ignores taps. */
    val downloadingIds: Set<String> = emptySet(),

    /**
     * Photos with a copy on this device. Their Download button is hidden.
     * Remembered across launches, and re-checked against the device on
     * every load, so deleting the copy from the Photos app brings the
     * button back.
     */
    val downloadedIds: Set<String> = emptySet(),

    /** Saved moments ago: the tile shows a tick briefly before the button goes. */
    val justDownloadedIds: Set<String> = emptySet(),

    /** One-shot confirmation or error, shown briefly and then cleared. */
    val message: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val galleryRepository = GalleryRepository()

    private val deviceGalleryRepository = DeviceGalleryRepository()

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadGallery() {

        // Re-opening the tab refreshes quietly: the full-screen spinner is
        // only for the very first load. It used to blank the grid on every
        // tab switch.
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {

            if (_uiState.value.photos.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoading = true)
            }

            // Independent reads, run together.
            val photosDeferred = async { galleryRepository.getGalleryForCurrentUser(getApplication()) }
            val sitesDeferred = async { historicalSiteRepository.getHistoricalSites() }

            val photos = photosDeferred.await()

            val siteNames = sitesDeferred.await()
                .associate { site -> site.siteId to site.siteName }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                photos = photos,
                siteNames = siteNames
            )

            refreshDownloadedState(photos)
        }
    }

    // ------------------------------------------------- remembered downloads

    // photoId -> media store Uri of the copy on this device, per account.
    private val downloadPrefs = application.getSharedPreferences("gallery_downloads", Context.MODE_PRIVATE)

    private fun downloadsKey() = "saved_${FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"}"

    private fun storedDownloads(): Map<String, String> =
        downloadPrefs.getStringSet(downloadsKey(), emptySet()).orEmpty()
            .mapNotNull { entry ->
                val id = entry.substringBefore('|')
                val uri = entry.substringAfter('|', "")
                if (id.isBlank() || uri.isBlank()) null else id to uri
            }
            .toMap()

    private fun storeDownloads(map: Map<String, String>) {
        downloadPrefs.edit()
            .putStringSet(downloadsKey(), map.map { (id, uri) -> "$id|$uri" }.toSet())
            .apply()
    }

    private fun rememberDownload(photoId: String, uri: Uri) {
        storeDownloads(storedDownloads() + (photoId to uri.toString()))
    }

    /**
     * Keeps only the remembered copies that still exist on the device and
     * belong to photos still in the gallery.
     */
    private suspend fun refreshDownloadedState(photos: List<Gallery>) {
        val context = getApplication<Application>()
        val liveIds = photos.map { it.id }.toSet()
        val stillSaved = storedDownloads()
            .filterKeys { it in liveIds }
            .filter { (_, uri) -> deviceGalleryRepository.isStillSaved(context, Uri.parse(uri)) }
        storeDownloads(stillSaved)
        // Anything saved while this check ran is kept too.
        _uiState.value = _uiState.value.copy(
            downloadedIds = stillSaved.keys + _uiState.value.justDownloadedIds
        )
    }

    fun clearJustDownloaded(photoId: String) {
        _uiState.value = _uiState.value.copy(justDownloadedIds = _uiState.value.justDownloadedIds - photoId)
    }

    fun onStoragePermissionDenied() {
        _uiState.value = _uiState.value.copy(
            message = "Storage permission is needed to save photos to your device."
        )
    }

    // ------------------------------------------------------------ selection

    /** A long press on a tile: enter selection mode with that photo picked. */
    fun startSelection(photoId: String) {
        if (photoId.isBlank()) return
        _uiState.value = _uiState.value.copy(
            selectionMode = true,
            selectedIds = setOf(photoId)
        )
    }

    fun toggleSelection(photoId: String) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (photoId in current) current - photoId else current + photoId
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedIds = _uiState.value.photos.map { it.id }.toSet()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectionMode = false,
            selectedIds = emptySet()
        )
    }

    /** The picked photos, in the grid's own order. */
    fun selectedPhotos(): List<Gallery> {
        val ids = _uiState.value.selectedIds
        return _uiState.value.photos.filter { it.id in ids }
    }

    // ------------------------------------------------------ save to device

    /**
     * Copies [photos] into the phone's own Photos app.
     *
     * Sequential rather than concurrent. Each one is a download plus a media
     * store write, and firing twenty of those at once on café wifi is how
     * you get twenty timeouts instead of twenty photos — the radio is the
     * bottleneck, so queueing them is both kinder to it and usually faster.
     *
     * Reports what actually happened rather than a blanket success. A
     * partial result is the one a visitor most needs telling about, because
     * it is the one where checking their camera roll would otherwise just
     * leave them puzzled.
     */
    fun saveToDevice(context: Context, photos: List<Gallery>) {

        val state = _uiState.value

        // A photo already saving is skipped rather than queued again, so
        // repeated taps on the same Download button do nothing.
        val toSave = photos.filter { it.id !in state.downloadingIds }

        if (toSave.isEmpty()) return

        // Several photos at once is the selection bar's save: that one also
        // locks the bar. A single tile's Download only locks its own tile.
        val isBatch = photos.size > 1 || state.selectionMode
        if (isBatch && state.isWorking) return

        // Set before launching so a double tap can't queue a second copy.
        _uiState.value = state.copy(
            isWorking = if (isBatch) true else state.isWorking,
            downloadingIds = state.downloadingIds + toSave.map { it.id },
            message = null
        )

        viewModelScope.launch {

            var saved = 0
            var alreadyOnDevice = 0
            var lastFailure: String? = null

            for (photo in toSave) {

                val siteName = _uiState.value.siteNames[photo.siteId] ?: "Histoury"

                val capturedAt = (photo.capturedAt as? Timestamp)?.toDate()?.time ?: 0L

                val result = deviceGalleryRepository.saveToDevice(
                    context = context,
                    imageUrl = photo.imageUrl,
                    siteName = siteName,
                    capturedAtMillis = capturedAt
                )

                val current = _uiState.value

                when (result) {
                    is DeviceGalleryRepository.Result.Success -> {
                        if (result.alreadySaved) alreadyOnDevice++ else saved++
                        rememberDownload(photo.id, result.uri)
                        _uiState.value = current.copy(
                            downloadingIds = current.downloadingIds - photo.id,
                            downloadedIds = current.downloadedIds + photo.id,
                            justDownloadedIds = current.justDownloadedIds + photo.id
                        )
                    }
                    is DeviceGalleryRepository.Result.Failed -> {
                        lastFailure = result.reason
                        _uiState.value = current.copy(downloadingIds = current.downloadingIds - photo.id)
                    }
                }
            }

            val done = saved + alreadyOnDevice
            val total = toSave.size

            _uiState.value = _uiState.value.copy(
                isWorking = if (isBatch) false else _uiState.value.isWorking,
                selectionMode = if (isBatch) false else _uiState.value.selectionMode,
                selectedIds = if (isBatch) emptySet() else _uiState.value.selectedIds,
                message = when {
                    done == 0 -> lastFailure ?: "Couldn't save to your device."
                    total == 1 && alreadyOnDevice == 1 -> "This photo is already on your device."
                    total == 1 -> "Photo saved to your device."
                    done == total -> "$done photos saved to your device."
                    else -> "Saved $done of $total photos to your device."
                }
            )
        }
    }

    fun requiresLegacyStoragePermission(): Boolean =
        deviceGalleryRepository.requiresLegacyStoragePermission()

    // ------------------------------------------------------------- deleting

    /**
     * Removes [photos] from Histoury for good.
     *
     * The grid is filtered by what the repository confirms it deleted rather
     * than by what was asked for, so a photo whose delete failed stays on
     * screen instead of disappearing and then reappearing on the next
     * refresh.
     */
    fun deleteSelected(photos: List<Gallery>) {

        if (photos.isEmpty() || _uiState.value.isWorking) return

        _uiState.value = _uiState.value.copy(isWorking = true, message = null)

        viewModelScope.launch {

            val deleted = galleryRepository.deletePhotos(getApplication(), photos).toSet()

            val remaining = _uiState.value.photos.filterNot { it.id in deleted }

            // The device copy is the visitor's to keep; only the record of it goes.
            storeDownloads(storedDownloads() - deleted)

            _uiState.value = _uiState.value.copy(
                isWorking = false,
                photos = remaining,
                selectionMode = false,
                selectedIds = emptySet(),
                message = when {
                    deleted.isEmpty() -> "Couldn't delete those photos."
                    // Checked before the singular case: deleting one of
                    // three successfully is a partial result, and reporting
                    // it as a flat "Photo deleted." would hide the two that
                    // are still there.
                    deleted.size < photos.size -> "Deleted ${deleted.size} of ${photos.size} photos."
                    deleted.size == 1 -> "Photo deleted."
                    else -> "${deleted.size} photos deleted."
                }
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
