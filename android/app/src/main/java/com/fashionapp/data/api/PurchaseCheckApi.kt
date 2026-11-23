package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseRecord
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface PurchaseCheckApi {
    // FR-11: compare one photo with the closet. price (won) is optional.
    // Read timeout is raised to 30s for this path only (LongRunningTimeoutInterceptor).
    @Multipart
    @POST("purchase-checks")
    suspend fun check(
        @Part image: MultipartBody.Part,
        @Part("price") price: RequestBody?
    ): ApiResponse<PurchaseCheckResult>

    // FR-12: latest 20 purchase checks, newest first
    @GET("purchase-checks")
    suspend fun history(): ApiResponse<List<PurchaseRecord>>
}
