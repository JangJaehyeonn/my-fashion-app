package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.PurchaseRecord
import retrofit2.http.GET

interface PurchaseCheckApi {
    // FR-12: latest 20 purchase checks, newest first
    @GET("purchase-checks")
    suspend fun history(): ApiResponse<List<PurchaseRecord>>
}
