package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import retrofit2.http.POST

interface EventApi {
    // FR-14 APP_OPENED: no body, the app sends it once a day
    @POST("events/app-open")
    suspend fun appOpened(): ApiResponse<Unit>
}
