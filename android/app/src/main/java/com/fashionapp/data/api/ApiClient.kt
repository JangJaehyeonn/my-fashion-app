package com.fashionapp.data.api

import com.fashionapp.BuildConfig
import com.fashionapp.data.datastore.TokenDataStore
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.TokenResponse
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

class AuthInterceptor @Inject constructor(
    private val tokenDataStore: TokenDataStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenDataStore.accessToken.firstOrNull() }
        val request = chain.request().newBuilder().apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
        }.build()
        return chain.proceed(request)
    }
}

class TokenAuthenticator @Inject constructor(
    private val tokenDataStore: TokenDataStore
) : Authenticator {
    private val gson = Gson()
    private val refreshClient = OkHttpClient()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseCount(response) >= 2) return null

        val refreshToken = runBlocking { tokenDataStore.refreshToken.firstOrNull() } ?: return null

        val body = """{"refreshToken":"$refreshToken"}""".toRequestBody("application/json".toMediaType())
        val refreshRequest = Request.Builder()
            .url("${BuildConfig.BASE_URL}auth/refresh")
            .post(body)
            .build()

        val refreshResponse = try {
            refreshClient.newCall(refreshRequest).execute()
        } catch (e: Exception) {
            return null
        }

        if (!refreshResponse.isSuccessful) {
            runBlocking { tokenDataStore.clearTokens() }
            return null
        }

        val json = refreshResponse.body?.string() ?: return null
        val type = object : TypeToken<ApiResponse<TokenResponse>>() {}.type
        val apiResponse: ApiResponse<TokenResponse> = gson.fromJson(json, type)
        val newTokens = apiResponse.data ?: run {
            runBlocking { tokenDataStore.clearTokens() }
            return null
        }

        runBlocking { tokenDataStore.saveTokens(newTokens.accessToken, newTokens.refreshToken) }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newTokens.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var r = response.priorResponse
        while (r != null) { count++; r = r.priorResponse }
        return count
    }
}

@Singleton
class ApiClient @Inject constructor(
    authInterceptor: AuthInterceptor,
    tokenAuthenticator: TokenAuthenticator
) {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}
