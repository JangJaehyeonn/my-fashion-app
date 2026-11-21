package com.fashionapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.location.LocationProvider
import com.fashionapp.data.model.Situation
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.OutfitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository,
    private val clothesRepository: ClothesRepository,
    private val locationProvider: LocationProvider
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

    // 추천 버튼을 눌렀는데 옷장이 비어 있던 경우 → 옷장 등록 안내 표시
    private val _isClosetEmpty = MutableStateFlow(false)
    val isClosetEmpty = _isClosetEmpty.asStateFlow()

    // 날씨 카드에 표시할 위치 이름 — 기기 위치를 쓰면 "내 위치", 권한 거부·위치 실패 시 "서울"
    private val _locationLabel = MutableStateFlow("서울")
    val locationLabel = _locationLabel.asStateFlow()

    // 위치 권한이 없고 아직 한 번도 안내하지 않았으면 화면이 안내 다이얼로그 → 권한 요청을 띄움
    private val _needsLocationPrompt = MutableStateFlow(
        !locationProvider.hasPermission() && !locationProvider.isPromptShown
    )
    val needsLocationPrompt = _needsLocationPrompt.asStateFlow()

    init {
        // 권한 안내를 띄워야 하면 응답을 받은 뒤(onLocationPermissionResult) 불러옴 — 서울 날씨를 먼저 받았다가 바뀌는 것 방지
        if (!_needsLocationPrompt.value) loadWeather()
    }

    fun onLocationPermissionResult() {
        locationProvider.isPromptShown = true
        _needsLocationPrompt.value = false
        loadWeather()
    }

    fun loadWeather() {
        viewModelScope.launch {
            _isWeatherLoading.value = true
            val deviceCoordinates = locationProvider.currentCoordinates()
            _locationLabel.value = if (deviceCoordinates != null) "내 위치" else "서울"
            outfitRepository.getWeather(deviceCoordinates ?: LocationProvider.SEOUL)
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
            runCatching {
                // 1) 내 옷장 확인 — 비어 있으면 AI 호출 없이 등록 안내로 전환
                val closet = clothesRepository.getMyClothes().getOrThrow()
                _isClosetEmpty.value = closet.isEmpty()
                if (closet.isEmpty()) {
                    _looks.value = emptyList()
                    return@runCatching
                }
                // 2) 서버가 DB의 내 옷 목록 + 날씨 + 상황으로 AI 추천을 받아옴
                val response = outfitRepository.recommendByCloset(
                    SituationRecommendRequest(w.temperature, w.condition, _selectedSituation.value.name)
                ).getOrThrow()
                _looks.value = response.outfits.map { it.toLookCard() }
                if (response.outfits.isEmpty()) {
                    _errorMessage.value = "지금 옷장으로는 코디를 만들기 어려워요. 상의·하의를 더 등록해보세요."
                }
            }.onFailure {
                _errorMessage.value = "코디 추천에 실패했습니다."
            }
            _isRecommending.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
