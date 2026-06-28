package com.fashionapp.ui.wardrobe

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.repository.ClothesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class WardrobeViewModel @Inject constructor(
    private val clothesRepository: ClothesRepository
) : ViewModel() {

    private val _clothes = MutableStateFlow<List<Clothes>>(emptyList())
    val clothes = _clothes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory = _selectedCategory.asStateFlow()

    init {
        loadClothes()
    }

    fun loadClothes() {
        viewModelScope.launch {
            _isLoading.value = true
            clothesRepository.getClothes()
                .onSuccess { _clothes.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isLoading.value = false
        }
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun uploadClothes(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            runCatching {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val stream = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다")
                val bytes = stream.readBytes()
                stream.close()
                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("image", "upload.jpg", requestBody)
                clothesRepository.uploadClothes(part)
            }.onSuccess { result ->
                result.onSuccess { loadClothes() }
                    .onFailure { _errorMessage.value = it.message }
            }.onFailure {
                _errorMessage.value = it.message
            }
            _isLoading.value = false
        }
    }

    fun deleteClothes(id: String) {
        viewModelScope.launch {
            clothesRepository.deleteClothes(id)
                .onSuccess { loadClothes() }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun clearError() { _errorMessage.value = null }

    val filteredClothes = combine(_clothes, _selectedCategory) { clothes, category ->
        if (category == null) clothes
        else clothes.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}
