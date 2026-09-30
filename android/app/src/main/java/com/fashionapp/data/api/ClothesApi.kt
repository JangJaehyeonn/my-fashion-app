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
    @Multipart
    @POST("clothes")
    suspend fun register(@Part image: MultipartBody.Part): ApiResponse<Clothes>

    @GET("clothes")
    suspend fun getMyClothes(): ApiResponse<List<Clothes>>

    @DELETE("clothes/{id}")
    suspend fun delete(@Path("id") id: String): ApiResponse<Unit>
}
