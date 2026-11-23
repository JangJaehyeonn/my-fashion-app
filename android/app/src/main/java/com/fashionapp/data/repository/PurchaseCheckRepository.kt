package com.fashionapp.data.repository

import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.image.UploadImageCompressor
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseRecord
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** A purchase check photo after compression (long edge 1024px JPEG, see ImageCompressor). */
class CompressedPhoto(val jpeg: ByteArray)

@Singleton
class PurchaseCheckRepository @Inject constructor(
    private val purchaseCheckApi: PurchaseCheckApi,
    private val compressor: UploadImageCompressor
) {
    /**
     * FR-17: compresses a picked photo with the existing ImageCompressor. [open] must return a new stream per call.
     * Fails when the image cannot be decoded; the original is never uploaded uncompressed.
     */
    suspend fun compress(open: () -> InputStream?): Result<CompressedPhoto> = runCatching {
        CompressedPhoto(compressor.compress(open))
    }

    /** FR-17: uploads one compressed photo (always image/jpeg) and an optional price in won. */
    suspend fun check(photo: CompressedPhoto, price: Long?): Result<PurchaseCheckResult> = runCatching {
        val image = MultipartBody.Part.createFormData(IMAGE_PART, IMAGE_FILE_NAME, photo.jpeg.toRequestBody(JPEG))
        val pricePart = price?.toString()?.toRequestBody(TEXT)
        purchaseCheckApi.check(image, pricePart).data ?: error("살까 말까 판정 실패")
    }

    suspend fun history(): Result<List<PurchaseRecord>> = runCatching {
        purchaseCheckApi.history().data ?: error("판정 기록 조회 실패")
    }

    companion object {
        const val IMAGE_PART = "image"
        const val IMAGE_FILE_NAME = "purchase.jpg"
        val JPEG = "image/jpeg".toMediaType()
        private val TEXT = "text/plain".toMediaType()
    }
}
