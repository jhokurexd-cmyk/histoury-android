package com.histoury.app.data.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.histoury.app.data.model.Weather
import com.histoury.app.data.repository.WeatherRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {

    private val weatherRepository = WeatherRepository()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadWeather()
    }

    fun loadWeather() {

        // Guard against double-firing if the user taps the retry state while
        // a request is already in flight.
        if (_isLoading.value) return

        viewModelScope.launch {

            _isLoading.value = true

            val fetched = weatherRepository.getCurrentWeather()

            // Keep the last good reading on screen if a refresh fails, rather
            // than blanking out a value the user could already see.
            if (fetched != null) {
                _weather.value = fetched
            }

            _isLoading.value = false
        }
    }
}
