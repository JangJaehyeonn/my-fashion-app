package com.fashionapp.ui.signup

import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.datastore.TokenStore
import com.fashionapp.data.model.AgeConfirmationRequest
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
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response

internal val testProfile = UserProfile(
    id = "u1", email = null, nickname = "tester", profileImageUrl = null, provider = "google",
    height = null, weight = null, bodyType = null, preferredStyle = null, ageConfirmedAt = "2026-11-21T00:00:00"
)

/** HttpException carrying the server's ApiResponse error body. */
internal fun httpError(status: Int, message: String): HttpException {
    val body = """{"success":false,"data":null,"message":"$message"}""".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(status, body))
}

internal class FakeTokenStore : TokenStore {
    override val accessToken = MutableStateFlow<String?>(null)
    override val refreshToken = MutableStateFlow<String?>(null)

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        this.accessToken.value = accessToken
        this.refreshToken.value = refreshToken
    }

    override suspend fun clearTokens() {
        accessToken.value = null
        refreshToken.value = null
    }
}

internal class FakeAuthApi : AuthApi {
    var checkIdAvailable = true
    var signupError: Throwable? = null
    var confirmAgeError: Throwable? = null
    val signupRequests = mutableListOf<SignupRequest>()
    val ageConfirmations = mutableListOf<AgeConfirmationRequest>()

    private val tokens = TokenResponse("access", "refresh")

    override suspend fun signup(request: SignupRequest): ApiResponse<TokenResponse> {
        signupRequests += request
        signupError?.let { throw it }
        return ApiResponse(true, tokens, null)
    }

    override suspend fun checkLoginId(loginId: String): ApiResponse<CheckIdResponse> =
        ApiResponse(true, CheckIdResponse(checkIdAvailable), null)

    override suspend fun confirmAge(request: AgeConfirmationRequest): ApiResponse<UserProfile> {
        ageConfirmations += request
        confirmAgeError?.let { throw it }
        return ApiResponse(true, testProfile, null)
    }

    override suspend fun getMyProfile(): ApiResponse<UserProfile> = ApiResponse(true, testProfile.copy(ageConfirmedAt = null), null)
    override suspend fun deleteAccount(): ApiResponse<Unit> = ApiResponse(true, Unit, null)

    override suspend fun exchange(request: ExchangeRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun login(request: LoginRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun refreshToken(request: RefreshRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun logout(request: RefreshRequest): ApiResponse<Unit> = error("unused")
    override suspend fun changePassword(request: ChangePasswordRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun grantVtonConsent(): ApiResponse<UserProfile> = error("unused")
    override suspend fun revokeVtonConsent(): ApiResponse<UserProfile> = error("unused")
    override suspend fun updateProfile(request: UpdateProfileRequest): ApiResponse<UserProfile> = error("unused")
}
