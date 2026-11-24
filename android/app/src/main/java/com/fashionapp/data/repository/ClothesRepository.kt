package com.fashionapp.data.repository

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesStatusRequest
import com.fashionapp.data.model.ClothesStatusResult
import com.fashionapp.data.model.ClothesUpdateRequest
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClothesRepository @Inject constructor(
    private val clothesApi: ClothesApi
) {
    suspend fun register(image: MultipartBody.Part): Result<Clothes> = runCatching {
        clothesApi.register(image).data ?: error("옷 등록 실패")
    }

    suspend fun getMyClothes(): Result<List<Clothes>> = runCatching {
        clothesApi.getMyClothes().data ?: emptyList()
    }

    suspend fun getSummary(): Result<ClosetSummary> = runCatching {
        clothesApi.getSummary().data ?: error("옷장 요약 조회 실패")
    }

    suspend fun update(id: String, request: ClothesUpdateRequest): Result<Clothes> = runCatching {
        clothesApi.update(id, request).data ?: error("옷 정보 저장 실패")
    }

    /** FR-27: puts the pieces in the laundry basket. */
    suspend fun putInLaundry(ids: List<String>): Result<ClothesStatusResult> = updateStatus(ids, STATUS_LAUNDRY)

    /** FR-27: the laundry is done, the pieces can be worn again. */
    suspend fun finishLaundry(ids: List<String>): Result<ClothesStatusResult> = updateStatus(ids, STATUS_AVAILABLE)

    private suspend fun updateStatus(ids: List<String>, status: String): Result<ClothesStatusResult> = runCatching {
        clothesApi.updateStatus(ClothesStatusRequest(ids, status)).data ?: error("옷 상태 변경 실패")
    }

    suspend fun reclassify(id: String): Result<Clothes> = runCatching {
        clothesApi.reclassify(id).data ?: error("다시 분류 실패")
    }

    suspend fun delete(id: String): Result<Unit> = runCatching {
        clothesApi.delete(id)
        Unit
    }

    companion object {
        const val STATUS_LAUNDRY = "LAUNDRY"
        const val STATUS_AVAILABLE = "AVAILABLE"
    }
}
