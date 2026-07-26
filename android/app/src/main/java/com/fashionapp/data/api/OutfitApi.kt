package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.SituationRecommendResponse
import com.fashionapp.data.model.Weather
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface OutfitApi {
    @POST("outfits/recommend/situation")
    suspend fun recommendBySituation(@Body request: SituationRecommendRequest): ApiResponse<SituationRecommendResponse>

    @GET("weather")
    suspend fun getWeather(): ApiResponse<Weather>
}
