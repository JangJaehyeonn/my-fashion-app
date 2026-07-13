package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.Clothes
import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ClothesApi {
    @GET("clothes")
    suspend fun getClothes(): ApiResponse<List<Clothes>>

    @Multipart
    @POST("clothes")
    suspend fun uploadClothes(@Part image: MultipartBody.Part): ApiResponse<Clothes>

    @DELETE("clothes/{id}")
    suspend fun deleteClothes(@Path("id") id: String): ApiResponse<Unit>
}
