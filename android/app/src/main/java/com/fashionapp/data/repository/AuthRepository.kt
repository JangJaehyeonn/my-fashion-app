package com.fashionapp.data.repository

import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.datastore.TokenDataStore
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import kotlinx.coroutines.flow.firstOrNull
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
        val refreshToken = tokenDataStore.refreshToken.firstOrNull()
        if (refreshToken != null) {
            runCatching { authApi.logout(RefreshRequest(refreshToken)) }
        }
        tokenDataStore.clearTokens()
    }

    // 서버 삭제가 성공했을 때만 로컬 토큰을 지움 — 실패 시 로그인 상태를 유지해 다시 시도할 수 있게 함
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val response = authApi.deleteAccount()
        check(response.success) { response.message ?: "회원 탈퇴 실패" }
        tokenDataStore.clearTokens()
    }

    suspend fun getMyProfile(): Result<UserProfile> = runCatching {
        authApi.getMyProfile().data ?: error("프로필 없음")
    }

    suspend fun updateProfile(request: UpdateProfileRequest): Result<UserProfile> = runCatching {
        authApi.updateProfile(request).data ?: error("프로필 수정 실패")
    }
}
