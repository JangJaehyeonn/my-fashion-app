package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ShoppingRecommendRequest
import com.fashionapp.data.model.ShoppingRecommendResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface ShoppingApi {
    @POST("shopping/recommend")
    suspend fun recommend(@Body request: ShoppingRecommendRequest): ApiResponse<ShoppingRecommendResponse>
}
