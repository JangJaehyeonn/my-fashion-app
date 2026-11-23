package com.fashionapp.data.repository

import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.model.PurchaseRecord
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PurchaseCheckRepository @Inject constructor(
    private val purchaseCheckApi: PurchaseCheckApi
) {
    suspend fun history(): Result<List<PurchaseRecord>> = runCatching {
        purchaseCheckApi.history().data ?: error("판정 기록 조회 실패")
    }
}
