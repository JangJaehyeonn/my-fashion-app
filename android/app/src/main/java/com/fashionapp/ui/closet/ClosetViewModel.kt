package com.fashionapp.ui.closet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.ProgressRequestBody
import com.fashionapp.data.image.ImageCompressor
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.DownloadedImage
import com.fashionapp.data.repository.ProductPage
import com.fashionapp.data.repository.ProductPageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class ClosetViewModel @Inject constructor(
    private val clothesRepository: ClothesRepository,
    private val productPageRepository: ProductPageRepository
) : ViewModel() {

    private val _clothes = MutableStateFlow<List<Clothes>>(emptyList())

    // null = 전체
    private val _selectedCategory = MutableStateFlow<ClothesCategory?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    // 파생 상태는 plain getter가 아니라 combine().stateIn()으로 노출해야 Compose가 변경을 감지함
    val filteredClothes: StateFlow<List<Clothes>> =
        combine(_clothes, _selectedCategory) { clothes, category ->
            if (category == null) clothes else clothes.filter { it.category == category }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val countByCategory: StateFlow<Map<ClothesCategory?, Int>> =
        _clothes.map { clothes ->
            buildMap {
                put(null, clothes.size)
                ClothesCategory.entries.forEach { c -> put(c, clothes.count { it.category == c }) }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 등록 진행 상황 (null = 등록 중 아님). current는 지금 처리 중인 사진 번호(1부터)
    private val _registerProgress = MutableStateFlow<RegisterProgress?>(null)
    val registerProgress = _registerProgress.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        loadClothes()
    }

    fun loadClothes() {
        viewModelScope.launch {
            _isLoading.value = true
            clothesRepository.getMyClothes()
                .onSuccess { _clothes.value = it }
                .onFailure { _message.value = "옷장을 불러오지 못했어요." }
            _isLoading.value = false
        }
    }

    fun selectCategory(category: ClothesCategory?) {
        _selectedCategory.value = category
    }

    fun register(context: Context, uri: Uri) = registerAll(context, listOf(uri))

    // 여러 장을 한 장씩 순서대로 등록 — 동시에 보내면 AI 서버(OpenAI) 분당 토큰 한도에 걸리기 쉬움
    fun registerAll(context: Context, uris: List<Uri>) =
        runRegistration(uris.map { uri -> suspend { uploadOne(context, uri) } })

    private fun runRegistration(jobs: List<suspend () -> Clothes>) {
        if (jobs.isEmpty() || _registerProgress.value != null) return
        viewModelScope.launch {
            val added = mutableListOf<Clothes>()
            var failed = 0
            jobs.forEachIndexed { index, job ->
                _registerProgress.value = RegisterProgress(current = index + 1, total = jobs.size)
                runCatching { job() }
                    .onSuccess { clothes ->
                        added += clothes
                        // 끝까지 기다리지 않고 등록되는 대로 그리드에 바로 반영
                        _clothes.value = listOf(clothes) + _clothes.value
                    }
                    .onFailure { failed++ }
            }
            _registerProgress.value = null
            _message.value = resultMessage(added, failed)
        }
    }

    // ---- 상품 주소(URL)로 등록: 피팅 탭과 같은 ProductPageRepository로 이미지를 찾아 기존 업로드·AI 분류 흐름에 태움 ----

    private val _urlState = MutableStateFlow<UrlRegisterState?>(null) // null = 다이얼로그 닫힘
    val urlState = _urlState.asStateFlow()

    fun openUrlDialog() { if (_registerProgress.value == null) _urlState.value = UrlRegisterState() }
    fun closeUrlDialog() { _urlState.value = null }
    fun setUrl(url: String) { _urlState.update { it?.copy(url = url, error = null) } }
    fun selectUrlImage(imageUrl: String) { _urlState.update { it?.copy(selectedImageUrl = imageUrl) } }

    fun fetchProduct() {
        val url = _urlState.value?.url?.trim().orEmpty()
        if (url.isEmpty()) { _urlState.update { it?.copy(error = "상품 페이지 주소를 입력해주세요.") }; return }
        if (_urlState.value?.isFetching == true) return
        _urlState.update { it?.copy(isFetching = true, error = null, product = null, selectedImageUrl = null) }
        viewModelScope.launch {
            productPageRepository.fetchProductPage(url)
                .onSuccess { page ->
                    _urlState.update { it?.copy(isFetching = false, product = page, selectedImageUrl = page.imageUrls.firstOrNull()) }
                }
                .onFailure { e ->
                    _urlState.update { it?.copy(isFetching = false, error = e.message ?: "상품 정보를 불러오지 못했어요.") }
                }
        }
    }

    fun registerFromUrl() {
        val imageUrl = _urlState.value?.selectedImageUrl ?: return
        if (_registerProgress.value != null) return
        _urlState.value = null
        runRegistration(listOf(suspend { uploadFromUrl(imageUrl) }))
    }

    private suspend fun uploadFromUrl(imageUrl: String): Clothes {
        setStage(RegisterStage.DOWNLOADING)
        val downloaded = productPageRepository.downloadImage(imageUrl).getOrThrow()
        setStage(RegisterStage.COMPRESSING)
        val (bytes, mimeType) = withContext(Dispatchers.Default) { prepareDownloaded(downloaded) }
        return uploadBytes(bytes, mimeType)
    }

    private fun prepareDownloaded(image: DownloadedImage): Pair<ByteArray, String> =
        runCatching { ImageCompressor.compress(image.bytes) to "image/jpeg" }
            .getOrElse { image.bytes to image.mimeType }

    private suspend fun uploadOne(context: Context, uri: Uri): Clothes {
        setStage(RegisterStage.COMPRESSING)
        // 카메라 원본(수 MB)을 그대로 올리면 모바일 회선에서 업로드가 가장 오래 걸리므로 긴 변 1024px JPEG로 줄여서 올림
        val (bytes, mimeType) = withContext(Dispatchers.Default) { prepareImage(context, uri) }
        return uploadBytes(bytes, mimeType)
    }

    private suspend fun uploadBytes(bytes: ByteArray, mimeType: String): Clothes {
        setStage(RegisterStage.UPLOADING, percent = 0)
        val body = ProgressRequestBody(bytes.toRequestBody(mimeType.toMediaType())) { written, total ->
            // 본문을 다 보냈으면 이후는 서버의 AI 분류 대기
            if (total > 0 && written >= total) setStage(RegisterStage.ANALYZING)
            else if (total > 0) setStage(RegisterStage.UPLOADING, percent = (written * 100 / total).toInt())
        }
        val part = MultipartBody.Part.createFormData("image", "clothes.jpg", body)
        return clothesRepository.register(part).getOrThrow()
    }

    // 압축에 실패하면(예: 구형 기기의 HEIC) 예전처럼 원본을 그대로 보냄. 실제 MIME 타입을 보내야 서버 허용 목록을 통과함 — "image/*" 금지
    private fun prepareImage(context: Context, uri: Uri): Pair<ByteArray, String> =
        runCatching { ImageCompressor.compress { context.contentResolver.openInputStream(uri) } to "image/jpeg" }
            .getOrElse {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("파일을 열 수 없습니다")
                raw to mime
            }

    // 진행률 콜백은 OkHttp 스레드에서 오므로 StateFlow.update로 안전하게 갱신하고, 값이 같으면 갱신하지 않음
    private fun setStage(stage: RegisterStage, percent: Int = 0) {
        _registerProgress.update { it?.copy(stage = stage, uploadPercent = percent)?.takeIf { next -> next != it } ?: it }
    }

    private fun resultMessage(added: List<Clothes>, failed: Int): String = when {
        added.isEmpty() -> "옷 등록에 실패했어요. 다시 시도해주세요."
        added.size == 1 && failed == 0 -> {
            val one = added.first()
            val color = one.color?.let { " · $it" } ?: ""
            "${one.category.label}$color(으)로 등록했어요"
        }
        failed == 0 -> "${added.size}벌을 등록했어요"
        else -> "${added.size}벌 등록, ${failed}벌은 실패했어요"
    }

    fun delete(clothes: Clothes) {
        viewModelScope.launch {
            clothesRepository.delete(clothes.id)
                .onSuccess { _clothes.value = _clothes.value.filterNot { it.id == clothes.id } }
                .onFailure { _message.value = "삭제에 실패했어요." }
        }
    }

    fun setMessage(message: String) { _message.value = message }
    fun clearMessage() { _message.value = null }
}

// 한 장을 등록하는 동안의 단계: (URL 등록이면 이미지 내려받기) → 사진 줄이기 → 업로드(%) → 서버 AI 분류 대기
enum class RegisterStage { DOWNLOADING, COMPRESSING, UPLOADING, ANALYZING }

data class RegisterProgress(
    val current: Int,
    val total: Int,
    val stage: RegisterStage = RegisterStage.COMPRESSING,
    val uploadPercent: Int = 0
)

// 상품 주소로 등록 다이얼로그 상태 — product가 있으면 후보 이미지 중 selectedImageUrl을 등록
data class UrlRegisterState(
    val url: String = "",
    val isFetching: Boolean = false,
    val product: ProductPage? = null,
    val selectedImageUrl: String? = null,
    val error: String? = null
)
