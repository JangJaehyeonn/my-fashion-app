package com.fashionapp.ui.fitting

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.VtonResult
import com.fashionapp.data.repository.ProductPage
import com.fashionapp.data.repository.ProductPageRepository
import com.fashionapp.data.repository.VtonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

// 피팅에 쓸 옷 사진의 출처 — 쇼핑몰 URL에서 추출한 이미지 또는 내 갤러리 사진
sealed interface GarmentSource {
    data class Remote(val imageUrl: String) : GarmentSource
    data class Local(val uri: Uri) : GarmentSource
}

@HiltViewModel
class FittingViewModel @Inject constructor(
    private val productPageRepository: ProductPageRepository,
    private val vtonRepository: VtonRepository
) : ViewModel() {

    private val _productUrl = MutableStateFlow("")
    val productUrl = _productUrl.asStateFlow()

    private val _isExtracting = MutableStateFlow(false)
    val isExtracting = _isExtracting.asStateFlow()

    private val _product = MutableStateFlow<ProductPage?>(null)
    val product = _product.asStateFlow()

    private val _garment = MutableStateFlow<GarmentSource?>(null)
    val garment = _garment.asStateFlow()

    private val _garmentDesc = MutableStateFlow("")
    val garmentDesc = _garmentDesc.asStateFlow()

    private val _personImageUri = MutableStateFlow<Uri?>(null)
    val personImageUri = _personImageUri.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _result = MutableStateFlow<VtonResult?>(null)
    val result = _result.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setProductUrl(url: String) { _productUrl.value = url }
    fun setPersonImage(uri: Uri) { _personImageUri.value = uri; _result.value = null }
    fun selectRemoteGarment(imageUrl: String) { _garment.value = GarmentSource.Remote(imageUrl); _result.value = null }
    fun setError(message: String) { _errorMessage.value = message }
    fun clearError() { _errorMessage.value = null }

    fun setLocalGarment(uri: Uri) {
        _garment.value = GarmentSource.Local(uri)
        _result.value = null
    }

    fun extract() {
        val url = _productUrl.value
        if (url.isBlank()) {
            _errorMessage.value = "쇼핑몰 상품 페이지 주소를 입력해주세요."
            return
        }
        viewModelScope.launch {
            _isExtracting.value = true
            productPageRepository.fetchProductPage(url)
                .onSuccess { page ->
                    _product.value = page
                    // 첫 후보(대개 og:image 대표 사진)를 기본 선택
                    _garment.value = GarmentSource.Remote(page.imageUrls.first())
                    _garmentDesc.value = page.title.orEmpty()
                    _result.value = null
                }
                .onFailure { _errorMessage.value = it.message ?: "상품 정보를 불러오지 못했어요." }
            _isExtracting.value = false
        }
    }

    fun tryOn(context: Context) {
        val personUri = _personImageUri.value
        val garment = _garment.value
        if (personUri == null || garment == null) {
            _errorMessage.value = "전신 사진과 옷 사진을 모두 준비해주세요."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            runCatching {
                val personPart = withContext(Dispatchers.IO) { uriToPart(context, personUri, "personImage") }
                val garmentPart = when (garment) {
                    is GarmentSource.Local -> withContext(Dispatchers.IO) { uriToPart(context, garment.uri, "garmentImage") }
                    is GarmentSource.Remote -> {
                        val image = productPageRepository.downloadImage(garment.imageUrl).getOrThrow()
                        MultipartBody.Part.createFormData(
                            "garmentImage",
                            "garmentImage.jpg",
                            image.bytes.toRequestBody(image.mimeType.toMediaType())
                        )
                    }
                }
                val descBody = _garmentDesc.value.toRequestBody("text/plain".toMediaType())
                vtonRepository.tryOn(personPart, garmentPart, descBody).getOrThrow()
            }.onSuccess {
                _result.value = it
            }.onFailure {
                _errorMessage.value = it.message ?: "가상 피팅에 실패했어요."
            }
            _isProcessing.value = false
        }
    }

    private fun uriToPart(context: Context, uri: Uri, fieldName: String): MultipartBody.Part {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("파일을 열 수 없습니다")
        return MultipartBody.Part.createFormData(fieldName, "$fieldName.jpg", bytes.toRequestBody(mimeType.toMediaType()))
    }
}
