package com.fashionapp.ui.navigation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.data.model.MSG_AGREE_REQUIRED
import com.fashionapp.data.repository.AppOpenReporter
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NavEvent {
    object ToMain : NavEvent()
    object ToLogin : NavEvent()
    object ToAgeGate : NavEvent()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val appOpenReporter: AppOpenReporter
) : ViewModel() {

    // Channel: 화면(NavHost)이 준비되기 전에 보낸 이동 이벤트도 버리지 않고 준비된 뒤 전달
    private val _navEvent = Channel<NavEvent>(Channel.BUFFERED)
    val navEvent = _navEvent.receiveAsFlow()

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
                    _navEvent.send(NavEvent.ToMain)
                }
                .onFailure { showLoginError("로그인 시간이 지났어요. 다시 시도해 주세요.") }
            _isLoggingIn.value = false
        }
    }

    fun clearLoginMessage() { _loginMessage.value = null }

    // ---- 만 14세 이상 확인 + 이용약관·개인정보처리방침 동의 (소셜 첫 가입·기존 사용자).
    // 서버도 확인 전에는 대부분의 API를 403으로 막는다
    private var ageChecked = false
    private val _ageGateAgreements = MutableStateFlow(Agreements())
    val ageGateAgreements = _ageGateAgreements.asStateFlow()

    fun onAgeGateAllChange(checked: Boolean) {
        _ageGateAgreements.value = _ageGateAgreements.value.withAll(checked)
        _ageGateError.value = null
    }

    fun onAgeGateAgreementChange(item: AgreementItem, checked: Boolean) {
        _ageGateAgreements.value = _ageGateAgreements.value.with(item, checked)
        _ageGateError.value = null
    }
    private val _ageGateWorking = MutableStateFlow(false)
    val ageGateWorking = _ageGateWorking.asStateFlow()
    private val _ageGateError = MutableStateFlow<String?>(null)
    val ageGateError = _ageGateError.asStateFlow()

    /** 메인 화면에 들어올 때마다 호출 — 확인이 끝났으면 다시 묻지 않는다 */
    fun ensureAgeConfirmed() {
        if (ageChecked) return
        viewModelScope.launch {
            authRepository.getMyProfile().onSuccess { profile ->
                if (profile.ageConfirmedAt != null) ageChecked = true else _navEvent.send(NavEvent.ToAgeGate)
            }
        }
    }

    /** FR-20: called whenever the main screen shows; APP_OPENED reaches the server at most once a day. */
    fun reportAppOpened() {
        viewModelScope.launch { appOpenReporter.reportIfNeeded() }
    }

    fun confirmAge() {
        val agreements = _ageGateAgreements.value
        if (!agreements.all) {
            _ageGateError.value = MSG_AGREE_REQUIRED
            return
        }
        if (_ageGateWorking.value) return
        _ageGateWorking.value = true
        _ageGateError.value = null
        viewModelScope.launch {
            authRepository.confirmAge(agreements)
                .onSuccess {
                    ageChecked = true
                    _navEvent.send(NavEvent.ToMain)
                }
                .onFailure { _ageGateError.value = "저장하지 못했어요. 다시 시도해 주세요." }
            _ageGateWorking.value = false
        }
    }

    /** 만 14세 미만: 방금 만들어진 계정을 삭제하고 로그인 화면으로 */
    fun declineUnder14() {
        viewModelScope.launch {
            _ageGateWorking.value = true
            _ageGateError.value = null
            authRepository.deleteAccount()
                .onSuccess {
                    resetAgeCheck()
                    _loginMessage.value = "만 14세 미만은 가입할 수 없어요."
                    _navEvent.send(NavEvent.ToLogin)
                }
                .onFailure { _ageGateError.value = "처리하지 못했어요. 다시 시도해 주세요." }
            _ageGateWorking.value = false
        }
    }

    /** 로그아웃·탈퇴 후 다른 계정으로 로그인할 수 있으므로 다시 확인하게 */
    fun resetAgeCheck() {
        ageChecked = false
        _ageGateAgreements.value = Agreements()
    }

    fun checkLoginState(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val token = authRepository.accessToken.firstOrNull()
            onResult(token != null)
        }
    }

    // 이미 로그인된 상태에서 오래된 로그인 딥링크(만료·재사용 코드, error)가 다시 열리면 무시 — 로그아웃시키지 않음
    private fun showLoginError(message: String) {
        viewModelScope.launch {
            if (authRepository.accessToken.firstOrNull() != null) return@launch
            _loginMessage.value = message
            _navEvent.send(NavEvent.ToLogin)
        }
    }

    // 서버 OAuth2AuthenticationFailureHandler가 보내는 error 코드
    private fun oauthErrorMessage(error: String) = when (error) {
        "access_denied" -> "로그인을 취소했어요."
        else -> "로그인에 실패했어요. 잠시 후 다시 시도해 주세요."
    }
}
