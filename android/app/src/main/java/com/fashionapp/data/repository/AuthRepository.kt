package com.fashionapp.data.repository

import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.datastore.TokenDataStore
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) {
    val accessToken = tokenDataStore.accessToken
    val refreshToken = tokenDataStore.refreshToken

    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        tokenDataStore.saveTokens(accessToken, refreshToken)
    }

    suspend fun logout() {
        runCatching { authApi.logout() }
        tokenDataStore.clearTokens()
    }

    suspend fun getMyProfile(): Result<UserProfile> = runCatching {
        authApi.getMyProfile().data ?: error("프로필 없음")
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile> = runCatching {
        authApi.updateProfile(request).data ?: error("프로필 수정 실패")
    }
}
