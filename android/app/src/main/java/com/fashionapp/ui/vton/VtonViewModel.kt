package com.fashionapp.ui.vton

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.VtonResult
import com.fashionapp.data.repository.VtonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class VtonViewModel @Inject constructor(
    private val vtonRepository: VtonRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val garmentDesc: String = Uri.decode(savedStateHandle.get<String>("garmentDesc") ?: "")

    private val _personImageUri = MutableStateFlow<Uri?>(null)
    val personImageUri = _personImageUri.asStateFlow()

    private val _garmentImageUri = MutableStateFlow<Uri?>(null)
    val garmentImageUri = _garmentImageUri.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _result = MutableStateFlow<VtonResult?>(null)
    val result = _result.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setPersonImage(uri: Uri) { _personImageUri.value = uri }
    fun setGarmentImage(uri: Uri) { _garmentImageUri.value = uri }
    fun setError(message: String) { _errorMessage.value = message }

    fun tryOn(context: Context) {
        val personUri = _personImageUri.value
        val garmentUri = _garmentImageUri.value
        if (personUri == null || garmentUri == null) {
            _errorMessage.value = "전신 사진과 옷 사진을 모두 준비해주세요."
            return
        }

        viewModelScope.launch {
            _isProcessing.value = true
            runCatching {
                val personPart = uriToPart(context, personUri, "personImage")
                val garmentPart = uriToPart(context, garmentUri, "garmentImage")
                val descBody = garmentDesc.toRequestBody("text/plain".toMediaType())
                vtonRepository.tryOn(personPart, garmentPart, descBody)
            }.onSuccess { repoResult ->
                repoResult.onSuccess { _result.value = it }
                    .onFailure { _errorMessage.value = it.message }
            }.onFailure {
                _errorMessage.value = it.message
            }
            _isProcessing.value = false
        }
    }

    private fun uriToPart(context: Context, uri: Uri, fieldName: String): MultipartBody.Part {
        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
        val stream = context.contentResolver.openInputStream(uri) ?: error("파일을 열 수 없습니다")
        val bytes = stream.readBytes()
        stream.close()
        val requestBody = bytes.toRequestBody(mimeType.toMediaType())
        return MultipartBody.Part.createFormData(fieldName, "$fieldName.jpg", requestBody)
    }

    fun clearError() { _errorMessage.value = null }
}
