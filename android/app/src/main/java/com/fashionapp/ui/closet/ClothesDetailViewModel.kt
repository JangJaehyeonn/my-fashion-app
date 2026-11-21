package com.fashionapp.ui.closet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.serverMessage
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.repository.ClothesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// Same limit as the backend (ClothesService.MAX_TEXT_LENGTH)
internal const val MAX_CLOTHES_TEXT_LENGTH = 50

internal const val MSG_LOAD_FAILED = "옷 정보를 불러오지 못했어요."
internal const val MSG_NOT_FOUND = "옷을 찾을 수 없어요. 이미 삭제됐을 수 있어요."
internal const val MSG_SAVED = "저장했어요"
internal const val MSG_SAVE_FAILED = "저장하지 못했어요. 다시 시도해 주세요."
internal const val MSG_TEXT_TOO_LONG = "색상·이름은 ${MAX_CLOTHES_TEXT_LENGTH}자 이내로 입력해 주세요."
internal const val MSG_RECLASSIFIED = "다시 분류했어요"
internal const val MSG_RECLASSIFY_FAILED = "지금은 AI 분류를 할 수 없어요. 잠시 후 다시 시도하거나 직접 입력해 주세요."

/** FR-02 clothes detail: edit category/color/name and request AI reclassification. */
@HiltViewModel
class ClothesDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val clothesRepository: ClothesRepository
) : ViewModel() {

    private val clothesId: String = checkNotNull(savedStateHandle[ARG_CLOTHES_ID])

    private val _state = MutableStateFlow(ClothesDetailState())
    val state: StateFlow<ClothesDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            clothesRepository.getMyClothes()
                .onSuccess { list ->
                    val found = list.firstOrNull { it.id == clothesId }
                    if (found == null) _state.update { it.copy(isLoading = false, loadError = MSG_NOT_FOUND) }
                    else _state.update { it.copy(isLoading = false).withClothes(found) }
                }
                .onFailure { _state.update { it.copy(isLoading = false, loadError = MSG_LOAD_FAILED) } }
        }
    }

    fun selectCategory(category: ClothesCategory) {
        if (category == ClothesCategory.UNCLASSIFIED) return
        _state.update { it.copy(category = category) }
    }

    fun setColor(color: String) = _state.update { it.copy(color = color) }
    fun setName(name: String) = _state.update { it.copy(name = name) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        val color = current.color.trim()
        val name = current.name.trim()
        if (color.length > MAX_CLOTHES_TEXT_LENGTH || name.length > MAX_CLOTHES_TEXT_LENGTH) {
            _state.update { it.copy(message = MSG_TEXT_TOO_LONG) }
            return
        }
        // Blank fields are sent as null (keep current value); the server rejects blank strings
        val request = ClothesUpdateRequest(
            category = current.category?.name,
            color = color.ifEmpty { null },
            name = name.ifEmpty { null }
        )
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            clothesRepository.update(clothesId, request)
                .onSuccess { updated ->
                    _state.update { it.copy(isSaving = false, changed = true, message = MSG_SAVED).withClothes(updated) }
                }
                .onFailure { e ->
                    // Keep the user's input so they can fix it and retry
                    _state.update { it.copy(isSaving = false, message = e.serverMessage() ?: MSG_SAVE_FAILED) }
                }
        }
    }

    fun reclassify() {
        if (!_state.value.canReclassify) return
        _state.update { it.copy(isReclassifying = true) }
        viewModelScope.launch {
            clothesRepository.reclassify(clothesId)
                .onSuccess { updated ->
                    val message = if (updated.isUnclassified) MSG_RECLASSIFY_FAILED else MSG_RECLASSIFIED
                    _state.update { it.copy(isReclassifying = false, changed = true, message = message).withClothes(updated) }
                }
                .onFailure { e ->
                    _state.update { it.copy(isReclassifying = false, message = e.serverMessage() ?: MSG_RECLASSIFY_FAILED) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        const val ARG_CLOTHES_ID = "clothesId"
    }
}

data class ClothesDetailState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val clothes: Clothes? = null,
    // Form values. category is null while the clothes is unclassified and the user has not picked one
    val category: ClothesCategory? = null,
    val color: String = "",
    val name: String = "",
    val isSaving: Boolean = false,
    val isReclassifying: Boolean = false,
    // True after a successful save or reclassify, so the closet grid reloads
    val changed: Boolean = false,
    val message: String? = null
) {
    val isBusy: Boolean get() = isSaving || isReclassifying

    val hasChanges: Boolean
        get() {
            val c = clothes ?: return false
            val categoryChanged = category != null && category != c.category
            val colorChanged = color.trim().isNotEmpty() && color.trim() != c.color.orEmpty()
            val nameChanged = name.trim().isNotEmpty() && name.trim() != c.name.orEmpty()
            return categoryChanged || colorChanged || nameChanged
        }

    val canSave: Boolean get() = clothes != null && !isBusy && hasChanges
    val canReclassify: Boolean get() = clothes != null && !isBusy

    /** Replaces the loaded clothes and resets the form to its values. */
    fun withClothes(clothes: Clothes): ClothesDetailState = copy(
        clothes = clothes,
        category = clothes.category.takeUnless { clothes.isUnclassified },
        color = clothes.color.orEmpty(),
        name = clothes.name.orEmpty()
    )
}
