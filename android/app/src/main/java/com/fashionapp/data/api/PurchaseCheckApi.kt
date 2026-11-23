package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.PurchaseAttributesRequest
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseDecisionRequest
import com.fashionapp.data.model.PurchaseDecisionResult
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.model.PurchaseSummary
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

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

    // FR-20: checks this month and the combos added by pieces bought from them
    @GET("purchase-checks/summary")
    suspend fun summary(): ApiResponse<PurchaseSummary>

    // FR-12: BUY registers the photo in the closet, SKIP discards it. Expired = 410, decided = 409.
    @POST("purchase-checks/{id}/decision")
    suspend fun decide(
        @Path("id") id: String,
        @Body request: PurchaseDecisionRequest
    ): ApiResponse<PurchaseDecisionResult>

    // FR-13: re-judge with corrected attributes. Same long read timeout as the check.
    @PATCH("purchase-checks/{id}/attributes")
    suspend fun updateAttributes(
        @Path("id") id: String,
        @Body request: PurchaseAttributesRequest
    ): ApiResponse<PurchaseCheckResult>
}
