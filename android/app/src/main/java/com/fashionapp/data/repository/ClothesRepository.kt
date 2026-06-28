package com.fashionapp.data.repository

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesUpdateRequest
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClothesRepository @Inject constructor(
    private val clothesApi: ClothesApi
) {
    suspend fun getClothes(): Result<List<Clothes>> = runCatching {
        clothesApi.getClothes().data ?: emptyList()
    }

    suspend fun uploadClothes(image: MultipartBody.Part): Result<Clothes> = runCatching {
        clothesApi.uploadClothes(image).data ?: error("업로드 실패")
    }

    suspend fun updateClothes(id: String, request: ClothesUpdateRequest): Result<Clothes> = runCatching {
        clothesApi.updateClothes(id, request).data ?: error("수정 실패")
    }

    suspend fun deleteClothes(id: String): Result<Unit> = runCatching {
        clothesApi.deleteClothes(id)
        Unit
    }
}
