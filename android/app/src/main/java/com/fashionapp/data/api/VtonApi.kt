package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.VtonResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface VtonApi {
    @Multipart
    @POST("vton")
    suspend fun tryOn(
        @Part personImage: MultipartBody.Part,
        @Part garmentImage: MultipartBody.Part,
        @Part("garmentDesc") garmentDesc: RequestBody
    ): ApiResponse<VtonResult>
}
