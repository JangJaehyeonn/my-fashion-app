package com.fashionapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.Situation
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import com.fashionapp.data.repository.OutfitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository
) : ViewModel() {

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather = _weather.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(true)
    val isWeatherLoading = _isWeatherLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedSituation = MutableStateFlow(Situation.DAILY)
    val selectedSituation = _selectedSituation.asStateFlow()

    private val _looks = MutableStateFlow<List<LookCard>>(emptyList())
    val looks = _looks.asStateFlow()

    private val _isRecommending = MutableStateFlow(false)
    val isRecommending = _isRecommending.asStateFlow()

    init {
        loadWeather()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            outfitRepository.getWeather()
                .onSuccess { _weather.value = it }
                .onFailure { _errorMessage.value = "날씨를 불러올 수 없습니다." }
            _isWeatherLoading.value = false
        }
    }

    fun selectSituation(situation: Situation) {
        _selectedSituation.value = situation
    }

    fun recommend() {
        val w = _weather.value ?: return
        viewModelScope.launch {
            _isRecommending.value = true
            outfitRepository.recommendBySituation(
                SituationRecommendRequest(w.temperature, w.condition, _selectedSituation.value.name)
            ).onSuccess { response -> _looks.value = response.outfits.map { it.toLookCard() } }
                .onFailure { _errorMessage.value = "코디 추천에 실패했습니다." }
            _isRecommending.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
