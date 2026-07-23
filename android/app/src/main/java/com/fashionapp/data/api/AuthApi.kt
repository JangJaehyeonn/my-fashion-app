package com.fashionapp.data.api

import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.TokenResponse
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/refresh")
    suspend fun refreshToken(@Body request: RefreshRequest): ApiResponse<TokenResponse>

    @POST("auth/logout")
    suspend fun logout(): ApiResponse<Unit>

    @GET("users/me")
    suspend fun getMyProfile(): ApiResponse<UserProfile>

    @PUT("users/me")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): ApiResponse<UserProfile>
}
