package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetRecommendResponse
import com.fashionapp.data.model.DayWeather
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OutfitApi {
    @POST("outfits/recommend/closet")
    suspend fun recommendByCloset(@Body request: SituationRecommendRequest): ApiResponse<ClosetRecommendResponse>

    // 서버가 좌표(소수 둘째 자리로 반올림)별로 OpenWeatherMap을 직접 조회·캐시한다. 좌표를 못 얻으면 앱이 서울 좌표를 보냄
    @GET("weather")
    suspend fun getWeather(@Query("lat") lat: Double, @Query("lon") lon: Double): ApiResponse<Weather>

    // FR-28: today's going out / midday / coming back weather, using the commute times saved in the profile
    @GET("weather/day")
    suspend fun getDayWeather(@Query("lat") lat: Double, @Query("lon") lon: Double): ApiResponse<DayWeather>
}
