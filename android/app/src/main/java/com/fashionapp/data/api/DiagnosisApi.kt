package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.StyleDiagnosis
import okhttp3.MultipartBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface DiagnosisApi {
    @Multipart
    @POST("diagnosis")
    suspend fun diagnose(@Part image: MultipartBody.Part): ApiResponse<StyleDiagnosis>

    @GET("diagnosis")
    suspend fun getDiagnoses(): ApiResponse<List<StyleDiagnosis>>

    @GET("diagnosis/{id}")
    suspend fun getDiagnosis(@Path("id") id: String): ApiResponse<StyleDiagnosis>
}
