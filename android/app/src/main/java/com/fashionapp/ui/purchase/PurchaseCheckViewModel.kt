package com.fashionapp.ui.purchase

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesSeason
import com.fashionapp.data.model.PurchaseAttributesRequest
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseItem
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.CompressedPhoto
import com.fashionapp.data.repository.FittingGarmentHandoff
import com.fashionapp.data.repository.HandoffGarment
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
import javax.inject.Inject

/** Where the photo comes from. All three lead to the same confirm step. */
enum class PurchaseSource { CAMERA, GALLERY, URL }

enum class PurchaseStage { PICKING, URL_INPUT, CONFIRM, ANALYZING, RESULT, DONE }

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
    /** The picker was cancelled before any photo was chosen, or the user skipped: the screen should close. */
    val closed: Boolean = false,
    /** FR-18: closet clothes id → photo url, for similar clothes and combos (the result has ids only). */
    val closetPhotos: Map<String, String> = emptyMap(),
    /** FR-18: a BUY/SKIP request is in flight. */
    val isDeciding: Boolean = false,
    /** FR-18: a re-judge with corrected attributes is in flight. */
    val isRechecking: Boolean = false,
    val attributeSheetOpen: Boolean = false,
    /** FR-18: the server answered 410, the photo is gone. Only going back (or trying on locally) is left. */
    val expired: Boolean = false,
    /** FR-18: the clothes registered by BUY, shown on the done step. */
    val registered: Clothes? = null,
    /** FR-18: the photo was handed to the fitting screen, which should open now. */
    val openFitting: Boolean = false
) {
    val price: Long? get() = priceText.toLongOrNull()?.takeIf { it > 0 }
    val canCompare: Boolean get() = stage == PurchaseStage.CONFIRM && photo != null && !isPreparing
    val isBusy: Boolean get() = isDeciding || isRechecking
    val canDecide: Boolean get() = stage == PurchaseStage.RESULT && result != null && !isBusy && !expired
    val resultUi: PurchaseResultUi? get() = result?.let { PurchaseResultFormatter.format(it, closetPhotos) }

    /** Done step: "만들 수 있는 코디가 B개가 됐어요" with B from the server. */
    val doneMessage: String?
        get() = result?.let { "만들 수 있는 코디가 ${it.comboCountAfter}개가 됐어요. 사진을 다시 찍지 않아도 돼요." }
}

/** FR-18 attribute sheet values. Blank texts and null choices mean "keep the analysed value". */
data class AttributeEdit(
    val category: ClothesCategory? = null,
    val name: String = "",
    val color: String = "",
    val fit: String = "",
    val material: String = "",
    val pattern: String = "",
    val season: ClothesSeason? = null
) {
    companion object {
        fun of(item: PurchaseItem?) = AttributeEdit(
            category = item?.category?.takeIf { it != ClothesCategory.UNCLASSIFIED },
            name = item?.name.orEmpty(),
            color = item?.color.orEmpty(),
            fit = item?.fit.orEmpty(),
            material = item?.material.orEmpty(),
            pattern = item?.pattern.orEmpty(),
            season = ClothesSeason.parse(item?.season)
        )
    }
}

/**
 * FR-17: picks one photo (camera, gallery or product URL), compresses it with ImageCompressor,
 * takes an optional price and runs the purchase check. Analysis steps advance on a timer,
 * but the result is shown as soon as the response arrives.
 * FR-18: on the result, 살래요/안 살래요 record the decision, 고치기 re-judges (FR-13) and 입어보기 opens fitting.
 */
@HiltViewModel
class PurchaseCheckViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val purchaseCheckRepository: PurchaseCheckRepository,
    private val productImageSource: ProductImageSource,
    private val clothesRepository: ClothesRepository,
    private val closetChanges: ClosetChanges,
    private val garmentHandoff: FittingGarmentHandoff
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
                .onSuccess { body ->
                    _state.update { it.copy(stage = PurchaseStage.RESULT, result = body) }
                    loadClosetPhotos()
                }
                .onFailure { e ->
                    val message = PurchaseErrors.messageOf(e, MSG_CHECK_FAILED)
                    _state.update { it.copy(stage = PurchaseStage.CONFIRM, errorMessage = message) }
                }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    // ---- result (FR-18) ----

    /** Similar clothes and combos carry closet ids only; photos come from the closet list. Failure keeps placeholders. */
    private fun loadClosetPhotos() {
        viewModelScope.launch {
            clothesRepository.getMyClothes().onSuccess { clothes ->
                _state.update { s -> s.copy(closetPhotos = clothes.associate { it.id to it.imageUrl }) }
            }
        }
    }

    /** 살래요: registers the photo in the closet and shows the done step. */
    fun buy() = decide(buy = true)

    /** 안 살래요: records SKIP and closes; the shopping home reloads its recent checks when shown again. */
    fun skip() {
        if (_state.value.expired) {
            _state.update { it.copy(closed = true) }
            return
        }
        decide(buy = false)
    }

    private fun decide(buy: Boolean) {
        val current = _state.value
        val result = current.result ?: return
        if (!current.canDecide) return
        _state.update { it.copy(isDeciding = true, errorMessage = null) }
        viewModelScope.launch {
            purchaseCheckRepository.decide(result.id, buy)
                .onSuccess { decided ->
                    if (buy) {
                        closetChanges.notifyBought(decided.clothes?.id)
                        _state.update { it.copy(isDeciding = false, stage = PurchaseStage.DONE, registered = decided.clothes) }
                    } else {
                        _state.update { it.copy(isDeciding = false, closed = true) }
                    }
                }
                .onFailure { e -> onActionFailed(e, MSG_DECIDE_FAILED) }
        }
    }

    fun openAttributeSheet() {
        if (_state.value.canDecide) _state.update { it.copy(attributeSheetOpen = true) }
    }

    fun closeAttributeSheet() = _state.update { it.copy(attributeSheetOpen = false) }

    /** 고치기: re-judges with the changed attributes only. Nothing changed = just close the sheet. */
    fun recheck(edit: AttributeEdit) {
        val current = _state.value
        val result = current.result ?: return
        if (!current.canDecide) return
        val request = changesOf(result.item, edit)
        if (request == PurchaseAttributesRequest()) {
            closeAttributeSheet()
            return
        }
        _state.update { it.copy(isRechecking = true, errorMessage = null) }
        viewModelScope.launch {
            purchaseCheckRepository.recheck(result.id, request)
                .onSuccess { body ->
                    _state.update { it.copy(isRechecking = false, attributeSheetOpen = false, result = body) }
                }
                .onFailure { e -> onActionFailed(e, MSG_RECHECK_FAILED) }
        }
    }

    /** 입어보기: hands this photo to the fitting screen as the garment image. */
    fun tryOn() {
        val current = _state.value
        val photo = current.photo ?: return
        garmentHandoff.put(HandoffGarment(photo.jpeg, current.resultUi?.title.orEmpty()))
        _state.update { it.copy(openFitting = true) }
    }

    fun onFittingOpened() = _state.update { it.copy(openFitting = false) }

    private fun onActionFailed(e: Throwable, fallback: String) {
        val expired = PurchaseErrors.isExpired(e)
        _state.update {
            it.copy(
                isDeciding = false,
                isRechecking = false,
                attributeSheetOpen = it.attributeSheetOpen && !expired,
                expired = it.expired || expired,
                errorMessage = PurchaseErrors.messageOf(e, fallback)
            )
        }
    }

    private fun changesOf(item: PurchaseItem?, edit: AttributeEdit): PurchaseAttributesRequest {
        fun changed(value: String, old: String?): String? = value.trim().takeIf { it.isNotEmpty() && it != old }
        return PurchaseAttributesRequest(
            category = edit.category?.takeIf { it != item?.category }?.name,
            color = changed(edit.color, item?.color),
            name = changed(edit.name, item?.name),
            fit = changed(edit.fit, item?.fit),
            material = changed(edit.material, item?.material),
            pattern = changed(edit.pattern, item?.pattern),
            season = edit.season?.takeIf { it != ClothesSeason.parse(item?.season) }?.name
        )
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
        const val MSG_TIMEOUT = PurchaseErrors.MSG_TIMEOUT
        const val MSG_CHECK_FAILED = "비교하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val MSG_DECIDE_FAILED = "결정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
        const val MSG_RECHECK_FAILED = "다시 판정하지 못했어요. 잠시 후 다시 시도해 주세요."
    }
}
