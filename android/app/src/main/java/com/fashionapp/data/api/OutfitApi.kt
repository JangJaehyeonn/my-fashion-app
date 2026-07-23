package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.CalendarCreateRequest
import com.fashionapp.data.model.Outfit
import com.fashionapp.data.model.OutfitCalendar
import com.fashionapp.data.model.OutfitCreateRequest
import com.fashionapp.data.model.RecommendRequest
import com.fashionapp.data.model.RecommendResponse
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.SituationRecommendResponse
import com.fashionapp.data.model.Weather
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OutfitApi {
    @GET("outfits")
    suspend fun getOutfits(): ApiResponse<List<Outfit>>

    @POST("outfits")
    suspend fun createOutfit(@Body request: OutfitCreateRequest): ApiResponse<Outfit>

    @POST("outfits/recommend")
    suspend fun recommend(@Body request: RecommendRequest): ApiResponse<RecommendResponse>

    @POST("outfits/recommend/situation")
    suspend fun recommendBySituation(@Body request: SituationRecommendRequest): ApiResponse<SituationRecommendResponse>

    @GET("calendar")
    suspend fun getCalendar(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): ApiResponse<List<OutfitCalendar>>

    @POST("calendar")
    suspend fun addCalendarEntry(@Body request: CalendarCreateRequest): ApiResponse<OutfitCalendar>

    @DELETE("calendar/{id}")
    suspend fun deleteCalendarEntry(@Path("id") id: String): ApiResponse<Unit>

    @GET("weather")
    suspend fun getWeather(): ApiResponse<Weather>
}
