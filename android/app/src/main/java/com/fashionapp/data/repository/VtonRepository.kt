package com.fashionapp.data.repository

import com.fashionapp.data.api.VtonApi
import com.fashionapp.data.model.VtonResult
import okhttp3.MultipartBody
import okhttp3.RequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VtonRepository @Inject constructor(
    private val vtonApi: VtonApi
) {
    suspend fun tryOn(
        personImage: MultipartBody.Part,
        garmentImage: MultipartBody.Part,
        garmentDesc: RequestBody
    ): Result<VtonResult> = runCatching {
        vtonApi.tryOn(personImage, garmentImage, garmentDesc).data ?: error("가상 피팅 실패")
    }
}
