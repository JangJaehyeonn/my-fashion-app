package com.fashionapp.ui.diagnosis

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.StyleDiagnosis
import com.fashionapp.data.repository.DiagnosisRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class DiagnosisViewModel @Inject constructor(
    private val diagnosisRepository: DiagnosisRepository
) : ViewModel() {

    private val _isDiagnosing = MutableStateFlow(false)
    val isDiagnosing = _isDiagnosing.asStateFlow()

    private val _result = MutableStateFlow<StyleDiagnosis?>(null)
    val result = _result.asStateFlow()

    private val _history = MutableStateFlow<List<StyleDiagnosis>>(emptyList())
    val history = _history.asStateFlow()

    private val _isHistoryLoading = MutableStateFlow(false)
    val isHistoryLoading = _isHistoryLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        viewModelScope.launch {
            _isHistoryLoading.value = true
            diagnosisRepository.getDiagnoses()
                .onSuccess { _history.value = it }
                .onFailure { _errorMessage.value = it.message }
            _isHistoryLoading.value = false
        }
    }

    fun diagnose(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isDiagnosing.value = true
            runCatching {
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val stream = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다")
                val bytes = stream.readBytes()
                stream.close()
                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val part = MultipartBody.Part.createFormData("image", "diagnosis.jpg", requestBody)
                diagnosisRepository.diagnose(part)
            }.onSuccess { result ->
                result.onSuccess {
                    _result.value = it
                    loadHistory()
                }.onFailure { _errorMessage.value = it.message }
            }.onFailure {
                _errorMessage.value = it.message
            }
            _isDiagnosing.value = false
        }
    }

    fun clearResult() { _result.value = null }
    fun clearError() { _errorMessage.value = null }
}
