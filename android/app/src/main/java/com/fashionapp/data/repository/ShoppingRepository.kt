package com.fashionapp.data.repository

import com.fashionapp.data.api.ShoppingApi
import com.fashionapp.data.model.ShoppingRecommendRequest
import com.fashionapp.data.model.ShoppingRecommendResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShoppingRepository @Inject constructor(
    private val shoppingApi: ShoppingApi
) {
    suspend fun recommend(request: ShoppingRecommendRequest): Result<ShoppingRecommendResponse> = runCatching {
        shoppingApi.recommend(request).data ?: error("쇼핑 추천 실패")
    }
}
