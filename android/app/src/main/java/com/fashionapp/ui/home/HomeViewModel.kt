package com.fashionapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.serverError
import com.fashionapp.data.location.Coordinates
import com.fashionapp.data.location.DeviceLocation
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
    private val locationProvider: DeviceLocation
) : ViewModel() {

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather = _weather.asStateFlow()

    // FR-30 commute timeline (null = not loaded or the forecast failed; the card shows the current weather only)
    private val _timeline = MutableStateFlow<CommuteTimeline?>(null)
    val timeline = _timeline.asStateFlow()

    // FR-30 weather rule lines of the last recommendation ("외투를 벗어도…", "비 소식이 있어…")
    private val _ruleNotes = MutableStateFlow<List<String>>(emptyList())
    val ruleNotes = _ruleNotes.asStateFlow()

    // FR-30 "입을 수 있는 옷 N벌 중에서 · 세탁 중 N벌 제외" above the look cards
    private val _availabilityText = MutableStateFlow<String?>(null)
    val availabilityText = _availabilityText.asStateFlow()

    // Coordinates the weather was loaded for; the recommendation and the timeline refresh use the same place
    private var coordinates: Coordinates? = null

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
            val at = deviceCoordinates ?: LocationProvider.SEOUL
            coordinates = at
            outfitRepository.getWeather(at)
                .onSuccess { _weather.value = it }
                .onFailure { _errorMessage.value = "날씨를 불러올 수 없습니다." }
            _isWeatherLoading.value = false
            loadTimeline(at)
        }
    }

    /**
     * Called each time the home tab shows: commute times saved on the my tab change the timeline moments.
     * Does nothing before the first weather load (that load fetches the timeline itself).
     */
    fun refreshTimeline() {
        val at = coordinates ?: return
        viewModelScope.launch { loadTimeline(at) }
    }

    // A failed forecast hides the timeline; the current weather card still works
    private suspend fun loadTimeline(at: Coordinates) {
        outfitRepository.getDayWeather(at)
            .onSuccess { _timeline.value = HomeTexts.timeline(it) }
            .onFailure { _timeline.value = null }
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
                _availabilityText.value = HomeTexts.availabilityText(closet)
                // 2) 서버가 DB의 내 옷 목록 + 날씨 + 상황으로 AI 추천을 받아옴 (좌표가 있으면 동선 날씨 규칙 적용, FR-28)
                val at = coordinates
                val response = outfitRepository.recommendByCloset(
                    SituationRecommendRequest(w.temperature, w.condition, _selectedSituation.value.name, at?.lat, at?.lon)
                ).getOrThrow()
                _looks.value = response.outfits.map { it.toLookCard() }
                _ruleNotes.value = HomeTexts.ruleNotes(response.appliedRules)
                if (response.outfits.isEmpty()) {
                    _errorMessage.value = "지금 옷장으로는 코디를 만들기 어려워요. 상의·하의를 더 등록해보세요."
                }
            }.onFailure { e ->
                _errorMessage.value = recommendFailure(e)
            }
            _isRecommending.value = false
        }
    }

    // FR-27: everything is in the laundry or worn recently -> the server explains it (CLOSET_NONE_AVAILABLE)
    private fun recommendFailure(e: Throwable): String {
        val error = e.serverError()
        if (error?.code == CODE_NONE_AVAILABLE) {
            _looks.value = emptyList()
            _ruleNotes.value = emptyList()
            return error.message ?: MSG_NONE_AVAILABLE
        }
        return MSG_RECOMMEND_FAILED
    }

    fun clearError() { _errorMessage.value = null }

    companion object {
        const val CODE_NONE_AVAILABLE = "CLOSET_NONE_AVAILABLE"
        const val MSG_NONE_AVAILABLE = "지금 입을 수 있는 옷이 없어요. 세탁이 끝난 옷은 옷장에서 세탁 끝을 눌러 주세요."
        const val MSG_RECOMMEND_FAILED = "코디 추천에 실패했습니다."
    }
}
