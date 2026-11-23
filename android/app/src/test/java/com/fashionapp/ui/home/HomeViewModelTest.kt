package com.fashionapp.ui.home

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.OutfitApi
import com.fashionapp.data.location.Coordinates
import com.fashionapp.data.location.DeviceLocation
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetOutfit
import com.fashionapp.data.model.ClosetRecommendResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesStatusRequest
import com.fashionapp.data.model.ClothesStatusResult
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.model.DayWeather
import com.fashionapp.data.model.DayWeatherPoint
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.OutfitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val outfitApi = FakeOutfitApi()
    private val clothesApi = FakeHomeClothesApi()
    private val location = FakeDeviceLocation()

    private val top = piece("top", ClothesCategory.TOP)
    private val pants = piece("pants", ClothesCategory.BOTTOM)
    private val shoes = piece("shoes", ClothesCategory.SHOES)
    private val coat = piece("coat", ClothesCategory.OUTER)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun piece(id: String, category: ClothesCategory, status: String = "AVAILABLE", available: Boolean = true) = Clothes(
        id = id, imageUrl = "/api/images/$id.jpg", category = category, color = "블랙", name = id,
        createdAt = null, status = status, available = available
    )

    private fun loaded(): HomeViewModel = HomeViewModel(
        OutfitRepository(outfitApi), ClothesRepository(clothesApi), location
    )

    private fun point(time: String, temperature: Double, rain: Int = 0) = DayWeatherPoint(time, temperature, rain, 2.0)

    // ---- commute timeline (weather card) ----

    @Test
    fun `a big temperature range day shows three moments and a bold range`() = runTest(dispatcher) {
        outfitApi.day = DayWeather(point("08:00", 3.2), point("13:00", 12.4), point("19:00", 7.0), tempRange = 9.2)

        val vm = loaded()
        advanceUntilIdle()

        assertEquals(CommuteTimeline("8시 3° → 13시 12° → 19시 7°", "일교차 9°", rangeEmphasized = true), vm.timeline.value)
        // The timeline is asked for the same place as the current weather
        assertEquals(listOf(Coordinates(37.55, 127.0)), outfitApi.dayRequests)
    }

    @Test
    fun `a small range is not bold and half hours are spelled out`() {
        val timeline = HomeTexts.timeline(
            DayWeather(point("07:30", 10.0), point("13:00", 15.6), point("18:00", 12.0), tempRange = 5.6)
        )

        assertEquals("7시 30분 10° → 13시 16° → 18시 12°", timeline.pointsText)
        assertEquals("일교차 6°", timeline.rangeText)
        assertFalse(timeline.rangeEmphasized)
        assertTrue(HomeTexts.timeline(DayWeather(point("08:00", 0.0), point("13:00", 8.0), point("19:00", 4.0), 8.0)).rangeEmphasized)
    }

    @Test
    fun `a failed forecast hides the timeline but keeps the current weather`() = runTest(dispatcher) {
        outfitApi.failDay = true

        val vm = loaded()
        advanceUntilIdle()

        assertNull(vm.timeline.value)
        assertEquals(11.0, vm.weather.value?.temperature)
    }

    @Test
    fun `saving commute times then coming back to home moves the timeline moments`() = runTest(dispatcher) {
        outfitApi.day = DayWeather(point("08:00", 3.0), point("13:00", 12.0), point("19:00", 7.0), tempRange = 9.0)
        val vm = loaded()
        advanceUntilIdle()

        // The server now reads the saved commute times (07:00 out, 21:00 back)
        outfitApi.day = DayWeather(point("07:00", 2.0), point("13:00", 12.0), point("21:00", 5.0), tempRange = 10.0)
        vm.refreshTimeline()
        advanceUntilIdle()

        assertEquals("7시 2° → 13시 12° → 21시 5°", vm.timeline.value?.pointsText)
        assertEquals(2, outfitApi.dayRequests.size)
    }

    @Test
    fun `refreshing before the first weather load does nothing`() = runTest(dispatcher) {
        location.permission = false
        location.promptShown = false
        val vm = loaded()
        advanceUntilIdle()

        vm.refreshTimeline()
        advanceUntilIdle()

        assertTrue(outfitApi.dayRequests.isEmpty())
    }

    // ---- recommendation: rule lines, available count, revived ----

    @Test
    fun `the recommendation sends the coordinates and shows the applied rule lines`() = runTest(dispatcher) {
        clothesApi.clothes = listOf(top, pants, shoes, coat)
        outfitApi.response = ClosetRecommendResponse(
            outfits = listOf(ClosetOutfit(listOf(top, pants, shoes), "편한 하루", "캐주얼")),
            appliedRules = listOf("RAIN", "TEMP_RANGE")
        )
        val vm = loaded()
        advanceUntilIdle()

        vm.recommend()
        advanceUntilIdle()

        val sent = outfitApi.recommendRequests.single()
        assertEquals(37.55, sent.lat)
        assertEquals(127.0, sent.lon)
        assertEquals(listOf("외투를 벗어도 괜찮은 코디로 골랐어요", "비 소식이 있어 스웨이드·흰 신발은 뺐어요"), vm.ruleNotes.value)
    }

    @Test
    fun `no applied rules means no rule lines, and only rain shows only the rain line`() {
        assertEquals(emptyList<String>(), HomeTexts.ruleNotes(emptyList()))
        assertEquals(emptyList<String>(), HomeTexts.ruleNotes(null))
        assertEquals(listOf(HomeTexts.NOTE_RAIN), HomeTexts.ruleNotes(listOf("RAIN")))
        assertEquals(listOf(HomeTexts.NOTE_TEMP_RANGE), HomeTexts.ruleNotes(listOf("TEMP_RANGE")))
    }

    @Test
    fun `the available line counts wearable pieces and pieces in the laundry`() = runTest(dispatcher) {
        val washing = piece("shirt", ClothesCategory.TOP, status = "LAUNDRY", available = false)
        val cooling = piece("knit", ClothesCategory.TOP, available = false)
        clothesApi.clothes = listOf(top, pants, shoes, coat, washing, cooling)
        val vm = loaded()
        advanceUntilIdle()

        vm.recommend()
        advanceUntilIdle()

        assertEquals("입을 수 있는 옷 4벌 중에서 · 세탁 중 1벌 제외", vm.availabilityText.value)
        assertEquals("입을 수 있는 옷 4벌 중에서", HomeTexts.availabilityText(listOf(top, pants, shoes, coat)))
    }

    @Test
    fun `a revived look marks its long-unworn piece and other looks mark nothing`() = runTest(dispatcher) {
        clothesApi.clothes = listOf(top, pants, shoes, coat)
        outfitApi.response = ClosetRecommendResponse(
            outfits = listOf(
                ClosetOutfit(listOf(coat, top, pants, shoes), "오랜만에 코트", "미니멀", revived = true, revivedClothesId = "coat"),
                ClosetOutfit(listOf(top, pants, shoes), "편한 하루", "캐주얼")
            ),
            appliedRules = emptyList()
        )
        val vm = loaded()
        advanceUntilIdle()

        vm.recommend()
        advanceUntilIdle()

        assertEquals(listOf("coat", null), vm.looks.value.map { it.revivedClothesId })
        assertEquals("오랜만이에요", HomeTexts.REVIVED_BADGE)
        // A revived flag whose piece is not in the look marks nothing
        val odd = ClosetOutfit(listOf(top), "", "", revived = true, revivedClothesId = "gone").toLookCard()
        assertNull(odd.revivedClothesId)
    }

    @Test
    fun `everything in the laundry shows the server explanation`() = runTest(dispatcher) {
        clothesApi.clothes = listOf(piece("shirt", ClothesCategory.TOP, status = "LAUNDRY", available = false))
        outfitApi.recommendError = HttpException(
            Response.error<Any>(
                400,
                """{"success":false,"message":"지금 입을 수 있는 옷이 없어요.","code":"CLOSET_NONE_AVAILABLE"}"""
                    .toResponseBody("application/json".toMediaType())
            )
        )
        val vm = loaded()
        advanceUntilIdle()

        vm.recommend()
        advanceUntilIdle()

        assertEquals("지금 입을 수 있는 옷이 없어요.", vm.errorMessage.value)
        assertTrue(vm.looks.value.isEmpty())
        assertEquals("입을 수 있는 옷 0벌 중에서 · 세탁 중 1벌 제외", vm.availabilityText.value)
    }

    @Test
    fun `other recommendation failures keep the generic message`() = runTest(dispatcher) {
        clothesApi.clothes = listOf(top, pants, shoes)
        outfitApi.recommendError = RuntimeException("network")
        val vm = loaded()
        advanceUntilIdle()

        vm.recommend()
        advanceUntilIdle()

        assertEquals(HomeViewModel.MSG_RECOMMEND_FAILED, vm.errorMessage.value)
    }
}

private class FakeDeviceLocation : DeviceLocation {
    var permission = true
    var promptShown = true

    override fun hasPermission(): Boolean = permission
    override var isPromptShown: Boolean
        get() = promptShown
        set(value) { promptShown = value }

    override suspend fun currentCoordinates(): Coordinates? = if (permission) Coordinates(37.55, 127.0) else null
}

private class FakeOutfitApi : OutfitApi {
    var day: DayWeather? = null
    var failDay = false
    var response = ClosetRecommendResponse(emptyList(), emptyList())
    var recommendError: Throwable? = null
    val dayRequests = mutableListOf<Coordinates>()
    val recommendRequests = mutableListOf<SituationRecommendRequest>()

    override suspend fun recommendByCloset(request: SituationRecommendRequest): ApiResponse<ClosetRecommendResponse> {
        recommendRequests += request
        recommendError?.let { throw it }
        return ApiResponse(true, response, null)
    }

    override suspend fun getWeather(lat: Double, lon: Double): ApiResponse<Weather> =
        ApiResponse(true, Weather(temperature = 11.0, condition = "맑음", humidity = 40, windSpeed = 2.0), null)

    override suspend fun getDayWeather(lat: Double, lon: Double): ApiResponse<DayWeather> {
        dayRequests += Coordinates(lat, lon)
        if (failDay) throw RuntimeException("forecast down")
        return ApiResponse(true, day, null)
    }
}

private class FakeHomeClothesApi : ClothesApi {
    var clothes: List<Clothes> = emptyList()

    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> = ApiResponse(true, clothes, null)

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> = error("unused")
    override suspend fun getSummary(): ApiResponse<ClosetSummary> = error("unused")
    override suspend fun updateStatus(request: ClothesStatusRequest): ApiResponse<ClothesStatusResult> = error("unused")
    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> = error("unused")
    override suspend fun reclassify(id: String): ApiResponse<Clothes> = error("unused")
    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")
}
