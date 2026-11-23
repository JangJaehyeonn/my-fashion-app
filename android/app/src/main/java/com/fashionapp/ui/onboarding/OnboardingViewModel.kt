package com.fashionapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.image.UploadImageCompressor
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.OnboardingGate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import javax.inject.Inject

/** A photo picked from the gallery. [open] must return a new stream on every call. */
class PickedPhoto(val open: () -> InputStream?, val mimeType: String?)

/** Upload in progress: [current] is the 1-based photo number being registered. */
data class OnboardingUpload(val current: Int, val total: Int)

data class OnboardingState(
    /** null until the closet size is loaded. */
    val closetCount: Int? = null,
    val upload: OnboardingUpload? = null,
    val message: String? = null
) {
    /** Progress shown as "3/5"; never above the goal. */
    val progress: Int
        get() = (closetCount ?: 0).coerceAtMost(GOAL)

    val progressText: String
        get() = "$progress/$GOAL"

    val isComplete: Boolean
        get() = (closetCount ?: 0) >= GOAL

    val title: String
        get() = if (isComplete) TITLE_DONE else TITLE_START

    val canPick: Boolean
        get() = closetCount != null && !isComplete && upload == null

    val uploadText: String?
        get() = upload?.let { if (it.total > 1) "${it.current}/${it.total}벌째 등록하는 중…" else "옷을 등록하는 중…" }

    companion object {
        const val GOAL = OnboardingGate.MIN_CLOSET_SIZE
        const val TITLE_START = "5벌만 등록하면 살까 말까를 쓸 수 있어요"
        const val TITLE_DONE = "이제 살까 말까를 써볼 수 있어요"
    }
}

/** FR-22: first-run onboarding. Registers gallery photos one by one and counts toward 5 pieces. */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val clothesRepository: ClothesRepository,
    private val closetChanges: ClosetChanges,
    private val onboardingGate: OnboardingGate,
    private val compressor: UploadImageCompressor
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            clothesRepository.getMyClothes()
                .onSuccess { clothes -> _state.update { it.copy(closetCount = clothes.size) } }
                .onFailure { _state.update { it.copy(closetCount = it.closetCount ?: 0, message = MSG_LOAD_FAILED) } }
        }
    }

    /** Registers the photos one at a time (the AI server has a per-minute limit); each success moves the progress. */
    fun registerAll(photos: List<PickedPhoto>) {
        if (photos.isEmpty() || _state.value.upload != null) return
        viewModelScope.launch {
            var added = 0
            photos.forEachIndexed { index, photo ->
                _state.update { it.copy(upload = OnboardingUpload(index + 1, photos.size)) }
                runCatching { upload(photo) }.onSuccess {
                    added++
                    _state.update { it.copy(closetCount = (it.closetCount ?: 0) + 1) }
                }
            }
            val failed = photos.size - added
            _state.update { it.copy(upload = null, message = if (failed > 0) failedMessage(added, failed) else null) }
            // The closet tab keeps its ViewModel, so tell it new pieces arrived
            if (added > 0) closetChanges.notifyChanged()
        }
    }

    /** "나중에 하기": go home and do not show the onboarding again today. */
    fun later() = onboardingGate.dismissToday()

    fun clearMessage() = _state.update { it.copy(message = null) }

    private suspend fun upload(photo: PickedPhoto): Clothes {
        val (bytes, mimeType) = prepare(photo)
        val part = MultipartBody.Part.createFormData("image", "clothes.jpg", bytes.toRequestBody(mimeType.toMediaType()))
        return clothesRepository.register(part).getOrThrow()
    }

    // Same as the closet tab: compressed JPEG (with FR-21 background removal), or the original when compression fails
    private suspend fun prepare(photo: PickedPhoto): Pair<ByteArray, String> =
        runCatching { compressor.compress(photo.open) to "image/jpeg" }
            .getOrElse {
                withContext(Dispatchers.IO) {
                    val raw = photo.open()?.use { it.readBytes() } ?: error("cannot open photo")
                    raw to (photo.mimeType ?: "image/jpeg")
                }
            }

    private fun failedMessage(added: Int, failed: Int): String =
        if (added == 0) MSG_REGISTER_FAILED else "${added}벌 등록, ${failed}벌은 실패했어요"

    companion object {
        const val MSG_LOAD_FAILED = "옷장을 불러오지 못했어요."
        const val MSG_REGISTER_FAILED = "옷 등록에 실패했어요. 다시 시도해주세요."
    }
}
