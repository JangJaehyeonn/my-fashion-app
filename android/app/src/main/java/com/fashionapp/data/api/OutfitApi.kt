package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetRecommendResponse
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.Weather
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface OutfitApi {
    @POST("outfits/recommend/closet")
    suspend fun recommendByCloset(@Body request: SituationRecommendRequest): ApiResponse<ClosetRecommendResponse>

    // TODO(backend): 서버 GET /api/weather는 아직 lat/lon을 받지 않아 항상 서울 날씨를 반환함.
    //  서버가 좌표를 AI 서버(/ai/weather?lat=&lon=)로 넘기고 좌표별로 캐시하도록 바뀌어야 실제 위치가 반영됨
    @GET("weather")
    suspend fun getWeather(@Query("lat") lat: Double, @Query("lon") lon: Double): ApiResponse<Weather>
}
