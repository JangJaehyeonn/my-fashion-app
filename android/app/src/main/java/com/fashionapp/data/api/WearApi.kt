package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.WearSummary
import retrofit2.http.GET

interface WearApi {
    // FR-10: wear counts this week and this month
    @GET("wears/summary")
    suspend fun getSummary(): ApiResponse<WearSummary>
}
