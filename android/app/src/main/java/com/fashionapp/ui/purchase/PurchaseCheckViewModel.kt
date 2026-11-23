package com.fashionapp.ui.purchase

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.serverMessage
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.repository.CompressedPhoto
import com.fashionapp.data.repository.ProductImageSource
import com.fashionapp.data.repository.ProductPage
import com.fashionapp.data.repository.PurchaseCheckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InterruptedIOException
import javax.inject.Inject

/** Where the photo comes from. All three lead to the same confirm step. */
enum class PurchaseSource { CAMERA, GALLERY, URL }

enum class PurchaseStage { PICKING, URL_INPUT, CONFIRM, ANALYZING, RESULT }

/** Product URL step: fetch the page, pick one of the candidate images. */
data class ProductUrlState(
    val url: String = "",
    val isFetching: Boolean = false,
    val product: ProductPage? = null,
    val selectedImageUrl: String? = null,
    val error: String? = null
)

data class PurchaseCheckState(
    val source: PurchaseSource,
    val stage: PurchaseStage,
    /** Compressed photo shown in confirm/analyzing and uploaded as is. */
    val photo: CompressedPhoto? = null,
    /** True while compressing a picked photo or downloading the product image. */
    val isPreparing: Boolean = false,
    val priceText: String = "",
    val productUrl: ProductUrlState = ProductUrlState(),
    /** Index of the analysis step in progress (0..2). Earlier steps are done. */
    val analysisStep: Int = 0,
    val result: PurchaseCheckResult? = null,
    val errorMessage: String? = null,
    /** The picker was cancelled before any photo was chosen: the screen should close. */
    val closed: Boolean = false
) {
    val price: Long? get() = priceText.toLongOrNull()?.takeIf { it > 0 }
    val canCompare: Boolean get() = stage == PurchaseStage.CONFIRM && photo != null && !isPreparing
}

/**
 * FR-17: picks one photo (camera, gallery or product URL), compresses it with ImageCompressor,
 * takes an optional price and runs the purchase check. Analysis steps advance on a timer,
 * but the result is shown as soon as the response arrives.
 */
@HiltViewModel
class PurchaseCheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseCheckRepository: PurchaseCheckRepository,
    private val productImageSource: ProductImageSource
) : ViewModel() {

    private val source: PurchaseSource = savedStateHandle.get<String>(ARG_SOURCE)
        ?.let { raw -> PurchaseSource.entries.find { it.name == raw } }
        ?: PurchaseSource.GALLERY

    private val _state = MutableStateFlow(
        PurchaseCheckState(
            source = source,
            stage = if (source == PurchaseSource.URL) PurchaseStage.URL_INPUT else PurchaseStage.PICKING
        )
    )
    val state = _state.asStateFlow()

    /** The camera/gallery picker opens automatically once; later opens are user initiated. */
    var pickerAutoLaunched: Boolean = false
        private set

    private var analysisTicker: Job? = null
    private var checkJob: Job? = null

    fun markPickerLaunched() {
        pickerAutoLaunched = true
    }

    // ---- camera / gallery ----

    /** [open] must return a new stream per call (ImageCompressor reads the image several times). */
    fun onPhotoPicked(open: () -> InputStream?) = prepare { purchaseCheckRepository.compress(open).getOrThrow() }

    fun onPickCancelled() {
        if (_state.value.photo == null) _state.update { it.copy(closed = true) }
    }

    // ---- product URL ----

    fun setUrl(url: String) = _state.update {
        it.copy(productUrl = it.productUrl.copy(url = url, error = null))
    }

    fun selectProductImage(imageUrl: String) = _state.update {
        it.copy(productUrl = it.productUrl.copy(selectedImageUrl = imageUrl))
    }

    fun fetchProduct() {
        val current = _state.value.productUrl
        val url = current.url.trim()
        if (current.isFetching) return
        if (url.isEmpty()) {
            _state.update { it.copy(productUrl = it.productUrl.copy(error = MSG_URL_EMPTY)) }
            return
        }
        _state.update {
            it.copy(productUrl = it.productUrl.copy(isFetching = true, error = null, product = null, selectedImageUrl = null))
        }
        viewModelScope.launch {
            val page = productImageSource.fetchProductPage(url)
            _state.update { s ->
                val next = page.fold(
                    onSuccess = { s.productUrl.copy(isFetching = false, product = it, selectedImageUrl = it.imageUrls.firstOrNull()) },
                    onFailure = { s.productUrl.copy(isFetching = false, error = it.message ?: MSG_URL_FAILED) }
                )
                s.copy(productUrl = next)
            }
        }
    }

    /** Downloads the chosen product image and compresses it like a gallery photo. */
    fun useSelectedProductImage() {
        val imageUrl = _state.value.productUrl.selectedImageUrl ?: return
        prepare {
            val downloaded = productImageSource.downloadImage(imageUrl).getOrThrow()
            purchaseCheckRepository.compress { ByteArrayInputStream(downloaded.bytes) }.getOrThrow()
        }
    }

    private fun prepare(load: suspend () -> CompressedPhoto) {
        if (_state.value.isPreparing) return
        _state.update { it.copy(isPreparing = true, errorMessage = null) }
        viewModelScope.launch {
            runCatching { load() }
                .onSuccess { photo ->
                    _state.update { it.copy(isPreparing = false, photo = photo, stage = PurchaseStage.CONFIRM) }
                }
                .onFailure { e ->
                    val message = if (_state.value.source == PurchaseSource.URL) e.message ?: MSG_PHOTO_FAILED
                    else MSG_PHOTO_FAILED
                    _state.update { it.copy(isPreparing = false, errorMessage = message) }
                }
        }
    }

    // ---- confirm ----

    /** Keeps digits only, at most [MAX_PRICE_DIGITS]. */
    fun setPrice(text: String) {
        val digits = text.filter { it.isDigit() }.take(MAX_PRICE_DIGITS).trimStart('0')
        _state.update { it.copy(priceText = digits) }
    }

    // ---- analyze ----

    fun compare() {
        val current = _state.value
        val photo = current.photo ?: return
        if (!current.canCompare) return
        _state.update { it.copy(stage = PurchaseStage.ANALYZING, analysisStep = 0, errorMessage = null) }
        analysisTicker = viewModelScope.launch {
            // Time-based progress only; the last step waits for the response
            while (_state.value.analysisStep < ANALYSIS_STEPS.lastIndex) {
                delay(STEP_DURATION_MS)
                _state.update { it.copy(analysisStep = (it.analysisStep + 1).coerceAtMost(ANALYSIS_STEPS.lastIndex)) }
            }
        }
        checkJob = viewModelScope.launch {
            val result = purchaseCheckRepository.check(photo, current.price)
            analysisTicker?.cancel()
            result
                .onSuccess { body -> _state.update { it.copy(stage = PurchaseStage.RESULT, result = body) } }
                .onFailure { e -> _state.update { it.copy(stage = PurchaseStage.CONFIRM, errorMessage = errorMessageOf(e)) } }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private fun errorMessageOf(e: Throwable): String = when (e) {
        is InterruptedIOException -> MSG_TIMEOUT
        else -> e.serverMessage() ?: MSG_CHECK_FAILED
    }

    override fun onCleared() {
        analysisTicker?.cancel()
        checkJob?.cancel()
    }

    companion object {
        /** Navigation argument name, must match Route.PURCHASE_CHECK. */
        const val ARG_SOURCE = "source"
        const val STEP_DURATION_MS = 1_500L
        const val MAX_PRICE_DIGITS = 9
        val ANALYSIS_STEPS = listOf("옷 분석 (종류·색·소재)", "내 옷장과 비교", "늘어나는 코디 계산")

        const val MSG_PHOTO_FAILED = "사진을 읽을 수 없어요. 다른 사진을 골라주세요."
        const val MSG_URL_EMPTY = "상품 페이지 주소를 입력해주세요."
        const val MSG_URL_FAILED = "상품 정보를 불러오지 못했어요."
        const val MSG_TIMEOUT = "비교가 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요."
        const val MSG_CHECK_FAILED = "비교하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
}
