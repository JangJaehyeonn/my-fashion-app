package com.fashionapp.data.repository

import com.fashionapp.data.api.OutfitApi
import com.fashionapp.data.location.Coordinates
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.ClosetRecommendResponse
import com.fashionapp.data.model.DayWeather
import com.fashionapp.data.model.Weather
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutfitRepository @Inject constructor(
    private val outfitApi: OutfitApi
) {
    suspend fun recommendByCloset(request: SituationRecommendRequest): Result<ClosetRecommendResponse> = runCatching {
        outfitApi.recommendByCloset(request).data ?: error("옷장 기반 추천 실패")
    }

    suspend fun getWeather(coordinates: Coordinates): Result<Weather> = runCatching {
        outfitApi.getWeather(coordinates.lat, coordinates.lon).data ?: error("날씨 조회 실패")
    }

    /** FR-28: three moments of today's weather for the commute timeline. */
    suspend fun getDayWeather(coordinates: Coordinates): Result<DayWeather> = runCatching {
        outfitApi.getDayWeather(coordinates.lat, coordinates.lon).data ?: error("동선 날씨 조회 실패")
    }
}
