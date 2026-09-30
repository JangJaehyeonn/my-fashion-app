package com.fashionapp.data.repository

import com.fashionapp.data.api.OutfitApi
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.ClosetRecommendResponse
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

    suspend fun getWeather(): Result<Weather> = runCatching {
        outfitApi.getWeather().data ?: error("날씨 조회 실패")
    }
}
