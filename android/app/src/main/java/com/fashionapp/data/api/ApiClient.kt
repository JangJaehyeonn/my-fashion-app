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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
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
        // 로그인·재발급 같은 /auth/ 요청의 401은 토큰 만료가 아니라 요청 자체의 실패(코드 만료 등) — 재발급 시도 안 함
        if (response.request.url.encodedPath.contains("/auth/")) return null

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
        val path = chain.request().url.encodedPath.trimEnd('/')
        return when {
            path.endsWith("/vton") -> chain
                .withWriteTimeout(60, TimeUnit.SECONDS)
                .withReadTimeout(180, TimeUnit.SECONDS)
                .proceed(chain.request())
            // FR-02 reclassify waits for AI classification (backend AI read timeout is 30s)
            path.endsWith("/reclassify") -> chain
                .withReadTimeout(40, TimeUnit.SECONDS)
                .proceed(chain.request())
            // FR-17 purchase check (classify + embed + explain). Matches nginx proxy_read_timeout 30s
            isPurchaseCheck(chain.request().method, path) -> chain
                .withReadTimeout(PURCHASE_CHECK_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .proceed(chain.request())
            else -> chain.proceed(chain.request())
        }
    }

    companion object {
        const val PURCHASE_CHECK_READ_TIMEOUT_SECONDS = 30

        /** Only the new check (POST /api/purchase-checks) waits for AI; history and decisions keep 10s. */
        fun isPurchaseCheck(method: String, path: String): Boolean =
            method == "POST" && path.trimEnd('/').endsWith("/purchase-checks")
    }
}

/**
 * 이미지 로딩(Coil)용 OkHttpClient 구성 요소. 로컬 저장소의 이미지는 `/api/images/...`로 내려오며 JWT가 필요하다.
 * 반면 피팅·옷장의 "상품 주소로 등록"은 쇼핑몰 같은 외부 이미지도 Coil로 불러오므로,
 * 토큰은 **백엔드 호스트로 가는 요청에만** 붙이고 외부 호스트에는 절대 보내지 않는다.
 */
object BackendHost {
    private val backend: HttpUrl = BuildConfig.BASE_URL.toHttpUrl()
    fun matches(url: HttpUrl) = url.host == backend.host && url.port == backend.port && url.scheme == backend.scheme
}

class BackendOnlyAuthInterceptor(private val delegate: Interceptor) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        if (BackendHost.matches(chain.request().url)) delegate.intercept(chain) else chain.proceed(chain.request())
}

class BackendOnlyAuthenticator(private val delegate: Authenticator) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? =
        if (BackendHost.matches(response.request.url)) delegate.authenticate(route, response) else null
}

fun imageOkHttpClient(authInterceptor: Interceptor, authenticator: Authenticator): OkHttpClient =
    OkHttpClient.Builder()
        .addInterceptor(BackendOnlyAuthInterceptor(authInterceptor))
        .authenticator(BackendOnlyAuthenticator(authenticator))
        .build()

@Singleton
class ApiClient @Inject constructor(
    authInterceptor: AuthInterceptor,
    tokenAuthenticator: TokenAuthenticator
) {

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(LongRunningTimeoutInterceptor())
        .authenticator(tokenAuthenticator)
        // 릴리즈에선 본문(토큰·presigned URL·이미지)이 logcat에 남지 않도록 로깅을 끄고, 디버그에서도 인증 헤더는 가린다
        .addInterceptor(DebugHttpLogging.create())
        .build()

    val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}

/**
 * 디버그 빌드 HTTP 로그. 릴리즈는 아무것도 남기지 않는다.
 * 디버그에서도 비밀번호·토큰이 오가는 요청(/auth/ 아래 경로, 비밀번호 변경)은 본문을 빼고 요청 줄과 상태만 남긴다 —
 * 디버그 로그가 버그 리포트 등으로 공유될 때 비밀번호가 함께 나가지 않도록. Authorization 헤더는 항상 가린다.
 * 위치(좌표)가 URL 쿼리로 가는 날씨 요청은 아예 로깅하지 않는다.
 */
object DebugHttpLogging {
    private fun isLocation(path: String) = path.endsWith("/weather")
    private fun isSensitive(path: String) = path.contains("/auth/") || path.endsWith("/users/me/password")

    fun create(): Interceptor {
        if (!BuildConfig.DEBUG) return Interceptor { it.proceed(it.request()) }
        val body = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
        }
        val basic = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return Interceptor { chain ->
            val path = chain.request().url.encodedPath
            if (isLocation(path)) chain.proceed(chain.request())
            else (if (isSensitive(path)) basic else body).intercept(chain)
        }
    }
}
