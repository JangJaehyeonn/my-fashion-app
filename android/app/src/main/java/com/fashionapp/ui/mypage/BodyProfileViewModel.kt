package com.fashionapp.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.BodyType
import com.fashionapp.data.model.PreferredStyle
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BodyProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.getMyProfile()
                .onSuccess { _profile.value = it }
                .onFailure { _errorMessage.value = "프로필을 불러올 수 없습니다." }
            _isLoading.value = false
        }
    }

    fun save(
        height: Int?,
        weight: Int?,
        bodyType: BodyType?,
        preferredStyles: List<PreferredStyle>,
        onSaved: () -> Unit
    ) {
        val current = _profile.value ?: return
        viewModelScope.launch {
            _isSaving.value = true
            authRepository.updateProfile(
                UpdateProfileRequest(
                    nickname = current.nickname,
                    height = height,
                    weight = weight,
                    bodyType = bodyType?.name,
                    // 빈 목록도 그대로 보내야 서버가 "모두 해제"로 처리함 (null이면 옛 단일 값 경로로 해석)
                    preferredStyles = preferredStyles.map { it.name }
                )
            ).onSuccess {
                _profile.value = it
                onSaved()
            }.onFailure {
                _errorMessage.value = "저장에 실패했습니다."
            }
            _isSaving.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
