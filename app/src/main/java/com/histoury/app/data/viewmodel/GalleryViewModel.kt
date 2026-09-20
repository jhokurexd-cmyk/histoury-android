package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.Gallery
import com.histoury.app.data.repository.GalleryRepository
import com.histoury.app.data.repository.HistoricalSiteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val isLoading: Boolean = false,
    val photos: List<Gallery> = emptyList(),
    // siteId (business slug) -> siteName, for the caption on each photo tile
    val siteNames: Map<String, String> = emptyMap()
)

class GalleryViewModel : ViewModel() {

    private val galleryRepository = GalleryRepository()

    private val historicalSiteRepository = HistoricalSiteRepository()

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    fun loadGallery() {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true)

            val photos = galleryRepository.getGalleryForCurrentUser()

            val siteNames = historicalSiteRepository.getHistoricalSites()
                .associate { site -> site.siteId to site.siteName }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                photos = photos,
                siteNames = siteNames
            )
        }
    }
}
