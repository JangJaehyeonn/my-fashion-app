package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesUpdateRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path

interface ClothesApi {
    @GET("clothes")
    suspend fun getClothes(): ApiResponse<List<Clothes>>

    @Multipart
    @POST("clothes")
    suspend fun uploadClothes(@Part image: MultipartBody.Part): ApiResponse<Clothes>

    @GET("clothes/{id}")
    suspend fun getClothesById(@Path("id") id: String): ApiResponse<Clothes>

    @PUT("clothes/{id}")
    suspend fun updateClothes(
        @Path("id") id: String,
        @Body request: ClothesUpdateRequest
    ): ApiResponse<Clothes>

    @DELETE("clothes/{id}")
    suspend fun deleteClothes(@Path("id") id: String): ApiResponse<Unit>
}
