package com.fashionapp.ui.fitting

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.VtonResult
import com.fashionapp.data.repository.AuthRepository
import com.fashionapp.data.repository.FittingGarmentHandoff
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
    // FR-18 "입어보기": the compressed purchase check photo (JPEG)
    class Photo(val jpeg: ByteArray) : GarmentSource
}

@HiltViewModel
class FittingViewModel @Inject constructor(
    private val productPageRepository: ProductPageRepository,
    private val vtonRepository: VtonRepository,
    private val authRepository: AuthRepository,
    private val garmentHandoff: FittingGarmentHandoff
) : ViewModel() {

    /** FR-18: puts a photo handed over from the purchase check result into the garment slot. */
    fun takeHandedOffGarment() {
        val handed = garmentHandoff.take() ?: return
        _garment.value = GarmentSource.Photo(handed.jpeg)
        _garmentDesc.value = handed.description
        _result.value = null
    }

    // 가상 피팅 사진 전송 동의 여부 (null = 아직 확인 전). 서버가 최종 판단(미동의면 403)하고, 앱은 첫 사용 때 동의 화면을 띄움
    private val _hasConsent = MutableStateFlow<Boolean?>(null)
    val hasConsent = _hasConsent.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog = _showConsentDialog.asStateFlow()

    private val _isSavingConsent = MutableStateFlow(false)
    val isSavingConsent = _isSavingConsent.asStateFlow()

    // 화면에 들어올 때마다 — 마이 탭에서 철회했을 수 있으므로
    fun refreshConsent() {
        viewModelScope.launch {
            authRepository.getMyProfile().onSuccess { _hasConsent.value = it.vtonConsentAt != null }
        }
    }

    /** "가상 피팅 시작": 동의했으면 바로 진행, 아니면(또는 확인 전이면) 동의 화면 */
    fun requestTryOn(context: Context) {
        if (_hasConsent.value == true) tryOn(context) else _showConsentDialog.value = true
    }

    fun dismissConsent() { _showConsentDialog.value = false }

    fun agreeAndTryOn(context: Context) {
        viewModelScope.launch {
            _isSavingConsent.value = true
            authRepository.grantVtonConsent()
                .onSuccess {
                    _hasConsent.value = true
                    _showConsentDialog.value = false
                    tryOn(context)
                }
                .onFailure { _errorMessage.value = "동의를 저장하지 못했어요. 다시 시도해 주세요." }
            _isSavingConsent.value = false
        }
    }

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
                    is GarmentSource.Photo -> MultipartBody.Part.createFormData(
                        "garmentImage",
                        "garmentImage.jpg",
                        garment.jpeg.toRequestBody("image/jpeg".toMediaType())
                    )
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
                if ((it as? retrofit2.HttpException)?.code() == 403) {
                    // 다른 기기에서 철회했거나 동의 정보가 없는 경우 — 동의 화면을 다시 띄움
                    _hasConsent.value = false
                    _showConsentDialog.value = true
                } else {
                    _errorMessage.value = it.message ?: "가상 피팅에 실패했어요."
                }
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
