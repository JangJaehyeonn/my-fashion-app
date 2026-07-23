package com.fashionapp.data.repository

import com.fashionapp.data.api.OutfitApi
import com.fashionapp.data.model.CalendarCreateRequest
import com.fashionapp.data.model.Outfit
import com.fashionapp.data.model.OutfitCalendar
import com.fashionapp.data.model.OutfitCreateRequest
import com.fashionapp.data.model.RecommendRequest
import com.fashionapp.data.model.RecommendResponse
import com.fashionapp.data.model.SituationRecommendRequest
import com.fashionapp.data.model.SituationRecommendResponse
import com.fashionapp.data.model.Weather
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutfitRepository @Inject constructor(
    private val outfitApi: OutfitApi
) {
    suspend fun getOutfits(): Result<List<Outfit>> = runCatching {
        outfitApi.getOutfits().data ?: emptyList()
    }

    suspend fun createOutfit(request: OutfitCreateRequest): Result<Outfit> = runCatching {
        outfitApi.createOutfit(request).data ?: error("코디 저장 실패")
    }

    suspend fun recommend(request: RecommendRequest): Result<RecommendResponse> = runCatching {
        outfitApi.recommend(request).data ?: error("추천 실패")
    }

    suspend fun recommendBySituation(request: SituationRecommendRequest): Result<SituationRecommendResponse> = runCatching {
        outfitApi.recommendBySituation(request).data ?: error("상황 기반 추천 실패")
    }

    suspend fun getCalendar(year: Int? = null, month: Int? = null): Result<List<OutfitCalendar>> = runCatching {
        outfitApi.getCalendar(year, month).data ?: emptyList()
    }

    suspend fun addCalendarEntry(request: CalendarCreateRequest): Result<OutfitCalendar> = runCatching {
        outfitApi.addCalendarEntry(request).data ?: error("기록 저장 실패")
    }

    suspend fun deleteCalendarEntry(id: String): Result<Unit> = runCatching {
        outfitApi.deleteCalendarEntry(id)
        Unit
    }

    suspend fun getWeather(): Result<Weather> = runCatching {
        outfitApi.getWeather().data ?: error("날씨 조회 실패")
    }
}
