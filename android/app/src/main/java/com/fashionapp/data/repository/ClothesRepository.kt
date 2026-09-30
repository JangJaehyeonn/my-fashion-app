package com.fashionapp.data.repository

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.model.Clothes
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

    suspend fun delete(id: String): Result<Unit> = runCatching {
        clothesApi.delete(id)
        Unit
    }
}
