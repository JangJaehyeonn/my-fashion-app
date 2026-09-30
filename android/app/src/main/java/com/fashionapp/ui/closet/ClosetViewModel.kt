package com.fashionapp.ui.closet

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.repository.ClothesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class ClosetViewModel @Inject constructor(
    private val clothesRepository: ClothesRepository
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
    fun registerAll(context: Context, uris: List<Uri>) {
        if (uris.isEmpty() || _registerProgress.value != null) return
        viewModelScope.launch {
            val added = mutableListOf<Clothes>()
            var failed = 0
            uris.forEachIndexed { index, uri ->
                _registerProgress.value = RegisterProgress(current = index + 1, total = uris.size)
                runCatching { uploadOne(context, uri) }
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

    private suspend fun uploadOne(context: Context, uri: Uri): Clothes {
        // 실제 MIME 타입을 보내야 서버 허용 목록(jpeg/png/webp/heic)을 통과함 — "image/*" 금지
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("파일을 열 수 없습니다")
        val part = MultipartBody.Part.createFormData(
            "image", "clothes.jpg", bytes.toRequestBody(mimeType.toMediaType())
        )
        return clothesRepository.register(part).getOrThrow()
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

data class RegisterProgress(val current: Int, val total: Int)
