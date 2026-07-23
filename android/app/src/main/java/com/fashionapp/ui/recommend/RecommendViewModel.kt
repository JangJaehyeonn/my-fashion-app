package com.fashionapp.ui.recommend

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.OutfitCreateRequest
import com.fashionapp.data.model.RecommendRequest
import com.fashionapp.data.model.RecommendedOutfit
import com.fashionapp.data.model.Situation
import com.fashionapp.data.model.SituationOutfitSuggestion
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.OutfitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class RecommendViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val clothesRepository: ClothesRepository
) : ViewModel() {

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather = _weather.asStateFlow()

    private val _recommendations = MutableStateFlow<List<RecommendedOutfit>>(emptyList())
    val recommendations = _recommendations.asStateFlow()

    private val _allClothes = MutableStateFlow<List<Clothes>>(emptyList())
    val allClothes = _allClothes.asStateFlow()

    private val _isWeatherLoading = MutableStateFlow(true)
    val isWeatherLoading = _isWeatherLoading.asStateFlow()

    private val _isRecommendLoading = MutableStateFlow(false)
    val isRecommendLoading = _isRecommendLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _savedIds = MutableStateFlow<Set<Int>>(emptySet())
    val savedIds = _savedIds.asStateFlow()

    private val _selectedSituation = MutableStateFlow(Situation.WORK)
    val selectedSituation = _selectedSituation.asStateFlow()

    private val _situationRecommendations = MutableStateFlow<List<SituationOutfitSuggestion>>(emptyList())
    val situationRecommendations = _situationRecommendations.asStateFlow()

    private val _isSituationLoading = MutableStateFlow(false)
    val isSituationLoading = _isSituationLoading.asStateFlow()

    init {
        loadWeather()
        viewModelScope.launch {
            clothesRepository.getClothes()
                .onSuccess { _allClothes.value = it }
        }
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

    fun recommend() {
        val w = _weather.value ?: return
        viewModelScope.launch {
            _isRecommendLoading.value = true
            _savedIds.value = emptySet()
            outfitRepository.recommend(RecommendRequest(w.temperature, w.condition))
                .onSuccess { _recommendations.value = it.outfits }
                .onFailure { _errorMessage.value = "추천 요청에 실패했습니다." }
            _isRecommendLoading.value = false
        }
    }

    fun saveOutfit(outfit: RecommendedOutfit, index: Int) {
        val w = _weather.value ?: return
        viewModelScope.launch {
            outfitRepository.createOutfit(
                OutfitCreateRequest(
                    name = "AI 추천 코디 ${LocalDate.now()}",
                    styleTag = outfit.styleTag,
                    weatherCondition = w.condition,
                    clothesIds = outfit.clothesIds
                )
            ).onSuccess {
                _savedIds.value = _savedIds.value + index
            }.onFailure {
                _errorMessage.value = "저장에 실패했습니다."
            }
        }
    }

    fun selectSituation(situation: Situation) {
        _selectedSituation.value = situation
    }

    fun recommendBySituation() {
        val w = _weather.value ?: return
        viewModelScope.launch {
            _isSituationLoading.value = true
            outfitRepository.recommendBySituation(
                SituationRecommendRequest(w.temperature, w.condition, _selectedSituation.value.name)
            ).onSuccess { _situationRecommendations.value = it.outfits }
                .onFailure { _errorMessage.value = "상황 기반 추천에 실패했습니다." }
            _isSituationLoading.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
