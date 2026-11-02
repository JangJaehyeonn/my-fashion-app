package com.fashionapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.httpStatus
import com.fashionapp.data.api.serverMessage
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 아이디·비밀번호 로그인 입력 상태 (OAuth 로그인 결과 안내는 MainViewModel.loginMessage) */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _loginId = MutableStateFlow("")
    val loginId = _loginId.asStateFlow()

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun onLoginIdChange(value: String) { _loginId.value = value.trim(); _error.value = null }
    fun onPasswordChange(value: String) { _password.value = value; _error.value = null }

    fun login(onSuccess: () -> Unit) {
        if (_isLoading.value) return
        if (_loginId.value.isBlank() || _password.value.isEmpty()) {
            _error.value = "아이디와 비밀번호를 입력해 주세요."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            authRepository.login(_loginId.value, _password.value)
                .onSuccess {
                    _password.value = ""
                    onSuccess()
                }
                .onFailure { e ->
                    // 401(아이디/비밀번호 오류)·429(잠금)는 서버 문구를 그대로, 그 밖은 연결 문제로 안내
                    _error.value = when (e.httpStatus) {
                        401, 429 -> e.serverMessage() ?: "아이디 또는 비밀번호가 틀렸어요"
                        else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
                    }
                }
            _isLoading.value = false
        }
    }
}
