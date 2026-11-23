package com.fashionapp.ui.purchase

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.image.UploadImageCompressor
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.model.PurchaseAttributesRequest
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseDecisionRequest
import com.fashionapp.data.model.PurchaseDecisionResult
import com.fashionapp.data.model.PurchaseItem
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.model.PurchaseVerdict
import com.fashionapp.data.repository.DownloadedImage
import com.fashionapp.data.repository.ProductImageSource
import com.fashionapp.data.repository.ProductPage
import kotlinx.coroutines.delay
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import retrofit2.HttpException
import retrofit2.Response
import java.io.InputStream

/** Marks compressed output so tests can tell it apart from the original bytes. */
class FakeCompressor : UploadImageCompressor {
    var calls = 0
    var fail = false

    override suspend fun compress(open: () -> InputStream?): ByteArray {
        calls++
        if (fail) error("decode failed")
        val original = open()?.use { it.readBytes() } ?: error("no stream")
        return COMPRESSED_PREFIX + original
    }

    companion object {
        val COMPRESSED_PREFIX = "jpeg:".toByteArray()
        fun compressed(original: ByteArray) = COMPRESSED_PREFIX + original
    }
}

/** Captures the uploaded multipart parts. [responseDelayMs] simulates a slow server. */
class FakePurchaseCheckApi : PurchaseCheckApi {
    var responseDelayMs = 0L
    var fail: Throwable? = null
    var result: PurchaseCheckResult = sampleResult()
    var uploadedBytes: ByteArray? = null
    var uploadedContentType: String? = null
    var uploadedDisposition: String? = null
    var uploadedPrice: String? = null
    var calls = 0

    override suspend fun check(image: MultipartBody.Part, price: RequestBody?): ApiResponse<PurchaseCheckResult> {
        calls++
        uploadedBytes = Buffer().also { image.body.writeTo(it) }.readByteArray()
        uploadedContentType = image.body.contentType()?.toString()
        uploadedDisposition = image.headers?.get("Content-Disposition")
        uploadedPrice = price?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }
        if (responseDelayMs > 0) delay(responseDelayMs)
        fail?.let { throw it }
        return ApiResponse(true, result, null)
    }

    override suspend fun history(): ApiResponse<List<PurchaseRecord>> = ApiResponse(true, emptyList(), null)

    // ---- FR-18 decision and re-judge ----
    var decideFail: Throwable? = null
    var decisions = mutableListOf<Pair<String, String>>()
    var registered: Clothes = Clothes("new-1", "/api/images/new-1.jpg", ClothesCategory.TOP, "블랙", "울 니트", null)
    var recheckFail: Throwable? = null
    var recheckRequests = mutableListOf<PurchaseAttributesRequest>()
    var recheckResult: PurchaseCheckResult = sampleResult()

    override suspend fun decide(id: String, request: PurchaseDecisionRequest): ApiResponse<PurchaseDecisionResult> {
        decisions += id to request.decision
        decideFail?.let { throw it }
        val clothes = if (request.decision == "BUY") registered else null
        return ApiResponse(true, PurchaseDecisionResult(null, clothes), null)
    }

    override suspend fun updateAttributes(id: String, request: PurchaseAttributesRequest): ApiResponse<PurchaseCheckResult> {
        recheckRequests += request
        recheckFail?.let { throw it }
        return ApiResponse(true, recheckResult, null)
    }
}

/** Closet list used for similar/combo photos. */
class FakeClothesApi : ClothesApi {
    var clothes: List<Clothes> = listOf(
        Clothes("c1", "/api/images/c1.jpg", ClothesCategory.TOP, "회색", "회색 니트", null),
        Clothes("c2", "/api/images/c2.jpg", ClothesCategory.BOTTOM, "검정", "슬랙스", null)
    )
    var listCalls = 0

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> = error("unused")
    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> {
        listCalls++
        return ApiResponse(true, clothes, null)
    }
    override suspend fun getSummary(): ApiResponse<ClosetSummary> = error("unused")
    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> = error("unused")
    override suspend fun reclassify(id: String): ApiResponse<Clothes> = error("unused")
    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")
}

/** A Retrofit HTTP error with the server's error body ({"success":false,"message":..,"code":..}). */
fun httpError(status: Int, code: String? = null, message: String = "서버 문구"): HttpException {
    val codeJson = code?.let { ",\"code\":\"$it\"" }.orEmpty()
    val body = "{\"success\":false,\"message\":\"$message\"$codeJson}".toResponseBody("application/json".toMediaType())
    return HttpException(Response.error<Any>(status, body))
}

class FakeProductImageSource : ProductImageSource {
    var page = ProductPage(title = "울 니트", imageUrls = listOf("https://shop.example/a.jpg", "https://shop.example/b.jpg"))
    var imageBytes = "product-image".toByteArray()
    var failFetch = false
    var downloadedUrl: String? = null

    override suspend fun fetchProductPage(rawUrl: String): Result<ProductPage> =
        if (failFetch) Result.failure(IllegalStateException("이 페이지에서 옷 이미지를 찾지 못했어요."))
        else Result.success(page)

    override suspend fun downloadImage(imageUrl: String): Result<DownloadedImage> {
        downloadedUrl = imageUrl
        return Result.success(DownloadedImage(imageBytes, "image/png"))
    }
}

fun sampleResult(plus: Int = 6, verdict: PurchaseVerdict = PurchaseVerdict.BUY) = PurchaseCheckResult(
    id = "check-1",
    item = PurchaseItem(ClothesCategory.TOP, "블랙", "울 니트", null, null, null, null),
    verdict = verdict,
    plus = plus,
    comboCountBefore = 10,
    comboCountAfter = 10 + plus,
    similar = emptyList(),
    outfits = emptyList(),
    headline = "코디가 많이 늘어나요",
    reasons = listOf("a", "b", "c"),
    gap = null,
    expectedWears = null,
    price = null,
    costPerWear = null
)
