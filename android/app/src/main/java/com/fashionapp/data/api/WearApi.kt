package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.WearRecordRequest
import com.fashionapp.data.model.WearRecordResult
import com.fashionapp.data.model.WearSummary
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface WearApi {
    // FR-10: wear counts this week and this month, and the most worn piece
    @GET("wears/summary")
    suspend fun getSummary(): ApiResponse<WearSummary>

    // FR-10: records the pieces as worn (once per piece per day). Someone else's piece = 400, nothing stored.
    @POST("wears")
    suspend fun record(@Body request: WearRecordRequest): ApiResponse<WearRecordResult>
}
