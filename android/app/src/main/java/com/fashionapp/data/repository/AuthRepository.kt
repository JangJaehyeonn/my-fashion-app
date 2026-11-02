package com.fashionapp.data.repository

import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.datastore.TokenDataStore
import com.fashionapp.data.model.ChangePasswordRequest
import com.fashionapp.data.model.ExchangeRequest
import com.fashionapp.data.model.LoginRequest
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.SignupRequest
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

    // OAuth 딥링크의 1회용 코드를 토큰으로 바꿔 저장
    suspend fun exchangeCode(code: String): Result<Unit> = runCatching {
        val tokens = authApi.exchange(ExchangeRequest(code)).data ?: error("토큰 교환 실패")
        tokenDataStore.saveTokens(tokens.accessToken, tokens.refreshToken)
    }

    // 아이디·비밀번호 로그인 → 토큰 저장
    suspend fun login(loginId: String, password: String): Result<Unit> = runCatching {
        val tokens = authApi.login(LoginRequest(loginId, password)).data ?: error("로그인 실패")
        tokenDataStore.saveTokens(tokens.accessToken, tokens.refreshToken)
    }

    // 가입 → 토큰 저장 (바로 로그인 상태)
    suspend fun signup(loginId: String, password: String, nickname: String): Result<Unit> = runCatching {
        val tokens = authApi.signup(SignupRequest(loginId, password, nickname)).data ?: error("가입 실패")
        tokenDataStore.saveTokens(tokens.accessToken, tokens.refreshToken)
    }

    suspend fun isLoginIdAvailable(loginId: String): Result<Boolean> = runCatching {
        authApi.checkLoginId(loginId).data?.available ?: error("중복 확인 실패")
    }

    // 비밀번호 변경 → 서버가 준 새 토큰으로 교체 (이 기기는 로그인 유지, 다른 기기의 리프레시 토큰은 무효)
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = runCatching {
        val tokens = authApi.changePassword(ChangePasswordRequest(currentPassword, newPassword)).data
            ?: error("비밀번호 변경 실패")
        tokenDataStore.saveTokens(tokens.accessToken, tokens.refreshToken)
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
