package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ExchangeRequest
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.TokenResponse
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    // 인증 불필요(/api/auth/**). 코드는 60초 안에 한 번만 교환 가능 — 만료·재사용이면 401
    @POST("auth/exchange")
    suspend fun exchange(@Body request: ExchangeRequest): ApiResponse<TokenResponse>

    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): ApiResponse<TokenResponse>

    // 서버가 본문의 refreshToken으로 Redis에 저장된 토큰을 폐기함 (본문이 없으면 폐기하지 않음)
    @POST("auth/logout")
    suspend fun logout(@Body request: RefreshRequest): ApiResponse<Unit>

    @GET("users/me")
    suspend fun getMyProfile(): ApiResponse<UserProfile>

    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserProfile>

    // TODO(backend): DELETE /api/users/me 미구현 — 서버에 생길 때까지 호출하면 실패 응답(현재 500)이 온다.
    //  서버 구현 시 옷장·진단 기록·S3 사진·Redis 리프레시 토큰까지 함께 삭제해야 함
    @DELETE("users/me")
    suspend fun deleteAccount(): ApiResponse<Unit>
}
