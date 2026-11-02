package com.fashionapp.ui.navigation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavEvent {
    object ToMain : NavEvent()
    object ToLogin : NavEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _navEvent = MutableSharedFlow<NavEvent>()
    val navEvent = _navEvent.asSharedFlow()

    // 로그인 화면에 띄울 안내 (코드 교환 실패, OAuth 실패 등)
    private val _loginMessage = MutableStateFlow<String?>(null)
    val loginMessage = _loginMessage.asStateFlow()

    private val _isLoggingIn = MutableStateFlow(false)
    val isLoggingIn = _isLoggingIn.asStateFlow()

    // 같은 딥링크가 onCreate·onNewIntent로 두 번 들어와도 한 번만 교환 (두 번째는 서버에서 401이 나므로)
    private val handledCodes = mutableSetOf<String>()

    /**
     * OAuth 로그인 결과 딥링크: fashionapp://app/oauth2/callback?code=... (성공) 또는 ?error=... (실패).
     * 서버는 토큰을 딥링크에 싣지 않고 1회용 코드만 보낸다 — 코드를 POST /api/auth/exchange로 토큰과 바꾼다.
     */
    fun handleDeepLink(uri: Uri) {
        if (uri.scheme != "fashionapp" || uri.host != "app" || uri.path != "/oauth2/callback") return

        uri.getQueryParameter("error")?.let { error ->
            showLoginError(oauthErrorMessage(error))
            return
        }

        val code = uri.getQueryParameter("code") ?: return
        if (!handledCodes.add(code)) return
        viewModelScope.launch {
            _isLoggingIn.value = true
            authRepository.exchangeCode(code)
                .onSuccess {
                    _loginMessage.value = null
                    _navEvent.emit(NavEvent.ToMain)
                }
                .onFailure { showLoginError("로그인 시간이 지났어요. 다시 시도해 주세요.") }
            _isLoggingIn.value = false
        }
    }

    fun clearLoginMessage() { _loginMessage.value = null }

    fun checkLoginState(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = authRepository.accessToken.firstOrNull()
            onResult(token != null)
        }
    }

    private fun showLoginError(message: String) {
        _loginMessage.value = message
        viewModelScope.launch { _navEvent.emit(NavEvent.ToLogin) }
    }

    // 서버 OAuth2AuthenticationFailureHandler가 보내는 error 코드
    private fun oauthErrorMessage(error: String) = when (error) {
        "access_denied" -> "로그인을 취소했어요."
        else -> "로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    }
}
