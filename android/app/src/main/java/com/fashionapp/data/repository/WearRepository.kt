package com.fashionapp.data.repository

import com.fashionapp.data.api.WearApi
import com.fashionapp.data.model.WearRecordRequest
import com.fashionapp.data.model.WearRecordResult
import com.fashionapp.data.model.WearSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WearRepository @Inject constructor(
    private val wearApi: WearApi
) {
    suspend fun getSummary(): Result<WearSummary> = runCatching {
        wearApi.getSummary().data ?: error("착용 요약 조회 실패")
    }

    /** FR-20: records today's wear of [clothesIds] (the server decides "today"). */
    suspend fun recordToday(clothesIds: List<String>): Result<WearRecordResult> = runCatching {
        wearApi.record(WearRecordRequest(clothesIds)).data ?: error("착용 기록 실패")
    }
}
