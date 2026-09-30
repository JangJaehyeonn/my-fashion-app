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
import java.util.concurrent.TimeUnit
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

/**
 * 가상 피팅(/api/vton)은 HF Space 대기열 + 합성으로 20초~2분이 걸려 OkHttp 기본 10초에 끊긴다.
 * 전체 타임아웃을 늘리면 다른 API가 멈췄을 때 사용자가 한참 기다리게 되므로 이 요청에만 늘린다.
 */
class LongRunningTimeoutInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val isVton = chain.request().url.encodedPath.trimEnd('/').endsWith("/vton")
        if (!isVton) return chain.proceed(chain.request())
        return chain
            .withWriteTimeout(60, TimeUnit.SECONDS)
            .withReadTimeout(180, TimeUnit.SECONDS)
            .proceed(chain.request())
    }
}

@Singleton
class ApiClient @Inject constructor(
    authInterceptor: AuthInterceptor,
    tokenAuthenticator: TokenAuthenticator
) {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(LongRunningTimeoutInterceptor())
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
