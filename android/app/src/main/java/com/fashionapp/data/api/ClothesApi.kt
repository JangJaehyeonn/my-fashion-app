package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesUpdateRequest
import okhttp3.MultipartBody
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface ClothesApi {
    @Multipart
    @POST("clothes")
    suspend fun register(@Part image: MultipartBody.Part): ApiResponse<Clothes>

    @GET("clothes")
    suspend fun getMyClothes(): ApiResponse<List<Clothes>>

    // FR-02: fields left null keep their current value on the server
    @PATCH("clothes/{id}")
    suspend fun update(@Path("id") id: String, @Body request: ClothesUpdateRequest): ApiResponse<Clothes>

    @POST("clothes/{id}/reclassify")
    suspend fun reclassify(@Path("id") id: String): ApiResponse<Clothes>

    @DELETE("clothes/{id}")
    suspend fun delete(@Path("id") id: String): ApiResponse<Unit>
}
