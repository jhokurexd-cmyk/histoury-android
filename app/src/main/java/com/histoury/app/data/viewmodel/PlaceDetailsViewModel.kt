package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.Place
import com.histoury.app.data.repository.PlaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaceDetailsUiState(
    val isLoading: Boolean = false,
    val place: Place? = null,
    val errorMessage: String? = null
)

/**
 * Backs PlaceDetailsScreen — a plain business-listing showcase for
 * non-historical places (cafés, restaurants, hotels, souvenir shops,
 * schools, banks). Unlike SiteDetailsViewModel, there's no arrival gate or
 * AR concept here: this is just "load the document and show it".
 */
class PlaceDetailsViewModel : ViewModel() {

    private val placeRepository = PlaceRepository()

    private val _uiState = MutableStateFlow(PlaceDetailsUiState())
    val uiState: StateFlow<PlaceDetailsUiState> = _uiState.asStateFlow()

    fun loadPlace(documentId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )

            val place = placeRepository.getPlaceById(documentId)

            if (place == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "This place could not be found."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                place = place
            )
        }
    }
}
