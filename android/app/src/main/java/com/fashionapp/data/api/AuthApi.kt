package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ChangePasswordRequest
import com.fashionapp.data.model.CheckIdResponse
import com.fashionapp.data.model.ExchangeRequest
import com.fashionapp.data.model.LoginRequest
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.SignupRequest
import com.fashionapp.data.model.TokenResponse
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query

interface AuthApi {
    // 인증 불필요(/api/auth/**). 코드는 60초 안에 한 번만 교환 가능 — 만료·재사용이면 401
    @POST("auth/exchange")
    suspend fun exchange(@Body request: ExchangeRequest): ApiResponse<TokenResponse>

    // 아이디·비밀번호 로그인. 아이디 없음·비밀번호 틀림 모두 401 "아이디 또는 비밀번호가 틀렸어요", 5회 실패 시 429(5분 잠금)
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<TokenResponse>

    // 가입 성공 시 바로 로그인 상태가 되도록 토큰을 함께 받음. 아이디 중복 409, 규칙 위반 400
    @POST("auth/signup")
    suspend fun signup(@Body request: SignupRequest): ApiResponse<TokenResponse>

    @GET("auth/check-id")
    suspend fun checkLoginId(@Query("loginId") loginId: String): ApiResponse<CheckIdResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): ApiResponse<TokenResponse>

    // 서버가 본문의 refreshToken으로 Redis에 저장된 토큰을 폐기함 (본문이 없으면 폐기하지 않음)
    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): ApiResponse<Unit>

    @GET("users/me")
    suspend fun getMyProfile(): ApiResponse<UserProfile>

    // 아이디 가입 사용자만. 성공하면 새 토큰을 받고 기존 리프레시 토큰은 모두 무효. 현재 비밀번호 오류는 400
    @PATCH("users/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): ApiResponse<TokenResponse>

    // 가상 피팅 사진 전송 동의/철회 — 서버가 동의 시각을 저장하고, 미동의면 /api/vton이 403
    @POST("users/me/consents/vton")
    suspend fun grantVtonConsent(): ApiResponse<UserProfile>

    @DELETE("users/me/consents/vton")
    suspend fun revokeVtonConsent(): ApiResponse<UserProfile>

    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserProfile>

    // TODO(backend): DELETE /api/users/me 미구현 — 서버에 생길 때까지 호출하면 실패 응답(현재 500)이 온다.
    //  서버 구현 시 옷장·진단 기록·S3 사진·Redis 리프레시 토큰까지 함께 삭제해야 함
    @DELETE("users/me")
    suspend fun deleteAccount(): ApiResponse<Unit>
}
