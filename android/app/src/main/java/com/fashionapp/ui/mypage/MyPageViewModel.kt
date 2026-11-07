package com.fashionapp.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.UserProfile
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MyPageViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    // 화면 진입 때마다 호출 — 체형·취향 설정에서 돌아왔을 때 요약 카드를 갱신하기 위함
    fun loadProfile() {
        viewModelScope.launch {
            // 이미 표시 중인 프로필이 있으면 스피너 없이 조용히 갱신
            _isLoading.value = _profile.value == null
            authRepository.getMyProfile()
                .onSuccess { _profile.value = it }
            _isLoading.value = false
        }
    }

    // 가상 피팅 사진 전송 동의 철회 → 다음 피팅 때 다시 동의 화면
    fun revokeVtonConsent() {
        viewModelScope.launch {
            authRepository.revokeVtonConsent().onSuccess { _profile.value = it }
        }
    }

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting = _isDeleting.asStateFlow()

    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError = _deleteError.asStateFlow()

    fun clearDeleteError() { _deleteError.value = null }

    fun deleteAccount(onDone: () -> Unit) {
        if (_isDeleting.value) return
        viewModelScope.launch {
            _isDeleting.value = true
            _deleteError.value = null
            authRepository.deleteAccount()
                .onSuccess { onDone() }
                .onFailure { _deleteError.value = "탈퇴 처리에 실패했어요. 잠시 후 다시 시도해 주세요." }
            _isDeleting.value = false
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            authRepository.logout()
            onDone()
        }
    }
}
