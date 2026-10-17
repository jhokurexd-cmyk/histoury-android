package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.histoury.app.data.model.Itinerary
import com.histoury.app.data.model.StopStatus
import com.histoury.app.data.repository.ItineraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

data class TripCompleteUiState(
    val isLoading: Boolean = true,
    val itinerary: Itinerary? = null,
    val errorMessage: String? = null
) {

    val completedCount: Int
        get() = itinerary?.stops?.count { it.status == StopStatus.COMPLETED } ?: 0

    val skippedCount: Int
        get() = itinerary?.stops?.count { it.status == StopStatus.SKIPPED } ?: 0

    /** Null when either endpoint is missing — an itinerary finished before these fields existed. */
    val elapsedMinutes: Int?
        get() {
            val trip = itinerary ?: return null

            fun millis(value: Any?): Long? = when (value) {
                is Timestamp -> value.toDate().time
                is Date -> value.time
                else -> null
            }

            val start = millis(trip.startedAt) ?: return null
            val finish = millis(trip.completedAt) ?: return null
            if (finish < start) return null

            return ((finish - start) / 60_000L).coerceAtLeast(1L).toInt()
        }
}

/**
 * Backs the trip-completion summary shown once an itinerary's final stop
 * resolves. Reads the itinerary fresh from Firestore by id rather than
 * accepting it (or its progress) as a navigation argument, matching the
 * itinerary rework's rule that stop/trip progress lives in exactly one
 * place — the saved [Itinerary] document — never duplicated into a
 * screen's own state.
 */
class TripCompleteViewModel : ViewModel() {

    private val itineraryRepository = ItineraryRepository()

    private val _uiState = MutableStateFlow(TripCompleteUiState())
    val uiState: StateFlow<TripCompleteUiState> = _uiState.asStateFlow()

    fun load(itineraryId: String) {

        viewModelScope.launch {

            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val itinerary = itineraryRepository.getItineraryById(itineraryId)

            if (itinerary != null) {
                _uiState.value = _uiState.value.copy(isLoading = false, itinerary = itinerary)
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Couldn't load this trip's summary."
                )
            }
        }
    }
}
