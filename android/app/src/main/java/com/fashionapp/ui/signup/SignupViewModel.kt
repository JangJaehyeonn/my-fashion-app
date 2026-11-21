package com.fashionapp.ui.signup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.api.httpStatus
import com.fashionapp.data.api.serverMessage
import com.fashionapp.data.model.AuthRules
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IdCheck { NOT_CHECKED, CHECKING, AVAILABLE, TAKEN, FAILED }

data class SignupForm(
    val loginId: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val nickname: String = "",
    val ageOver14: Boolean = false,
    val idCheck: IdCheck = IdCheck.NOT_CHECKED,
    // 가입 버튼을 한 번 눌렀으면 비어 있는 칸에도 안내를 띄움
    val submitted: Boolean = false,
    // 서버가 돌려준 칸별 오류 (규칙 위반 400 등)
    val serverLoginIdError: String? = null,
    val serverPasswordError: String? = null,
    val serverNicknameError: String? = null,
    val generalError: String? = null,
    val isSubmitting: Boolean = false
) {
    private fun show(value: String) = submitted || value.isNotEmpty()

    val loginIdError: String?
        get() = serverLoginIdError
            ?: (if (show(loginId)) AuthRules.loginIdError(loginId) else null)
            ?: when (idCheck) {
                IdCheck.TAKEN -> "이미 사용 중인 아이디예요."
                IdCheck.FAILED -> "중복 확인에 실패했어요. 다시 시도해 주세요."
                IdCheck.NOT_CHECKED -> if (submitted) "아이디 중복 확인을 해 주세요." else null
                else -> null
            }

    val loginIdHelper: String?
        get() = when (idCheck) {
            IdCheck.AVAILABLE -> "사용할 수 있는 아이디예요."
            else -> AuthRules.LOGIN_ID_RULE
        }

    val passwordError: String?
        get() = serverPasswordError ?: if (show(password)) AuthRules.passwordError(password) else null

    val passwordConfirmError: String?
        get() = if (show(passwordConfirm) && passwordConfirm != password) "비밀번호가 일치하지 않아요." else null

    val nicknameError: String?
        get() = serverNicknameError ?: if (show(nickname)) AuthRules.nicknameError(nickname) else null

    val ageError: String?
        get() = if (submitted && !ageOver14) "만 14세 이상만 가입할 수 있어요." else null

    val canCheckId: Boolean
        get() = AuthRules.loginIdError(loginId) == null && idCheck != IdCheck.CHECKING
}

@HiltViewModel
class SignupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _form = MutableStateFlow(SignupForm())
    val form = _form.asStateFlow()

    // 아이디를 바꾸면 이전 중복 확인 결과는 무효
    fun onLoginIdChange(value: String) = update {
        copy(loginId = value.trim(), idCheck = IdCheck.NOT_CHECKED, serverLoginIdError = null, generalError = null)
    }
    fun onPasswordChange(value: String) = update { copy(password = value, serverPasswordError = null, generalError = null) }
    fun onPasswordConfirmChange(value: String) = update { copy(passwordConfirm = value, generalError = null) }
    fun onAgeOver14Change(value: Boolean) = update { copy(ageOver14 = value, generalError = null) }
    fun onNicknameChange(value: String) = update { copy(nickname = value, serverNicknameError = null, generalError = null) }

    fun checkLoginId() {
        val loginId = _form.value.loginId
        if (AuthRules.loginIdError(loginId) != null) {
            update { copy(submitted = true) }
            return
        }
        update { copy(idCheck = IdCheck.CHECKING) }
        viewModelScope.launch {
            val result = authRepository.isLoginIdAvailable(loginId)
            // 확인하는 사이 아이디를 바꿨으면 결과를 버림
            if (_form.value.loginId != loginId) return@launch
            update {
                copy(idCheck = result.fold(
                    onSuccess = { if (it) IdCheck.AVAILABLE else IdCheck.TAKEN },
                    onFailure = { IdCheck.FAILED }
                ))
            }
        }
    }

    fun submit(onSuccess: () -> Unit) {
        val f = _form.value.copy(submitted = true)
        _form.value = f
        if (f.isSubmitting) return
        val invalid = f.loginIdError != null || f.passwordError != null || f.passwordConfirmError != null ||
                f.nicknameError != null || f.ageError != null || f.idCheck != IdCheck.AVAILABLE
        if (invalid) return

        update { copy(isSubmitting = true) }
        viewModelScope.launch {
            authRepository.signup(f.loginId, f.password, f.nickname.trim(), f.ageOver14)
                .onSuccess { onSuccess() }
                .onFailure { e ->
                    val message = e.serverMessage()
                    update {
                        when {
                            e.httpStatus == 409 -> copy(idCheck = IdCheck.TAKEN)
                            e.httpStatus == 400 && message?.contains("아이디") == true -> copy(serverLoginIdError = message)
                            e.httpStatus == 400 && message?.contains("비밀번호") == true -> copy(serverPasswordError = message)
                            e.httpStatus == 400 && message?.contains("닉네임") == true -> copy(serverNicknameError = message)
                            e.httpStatus == 400 && message?.contains("14세") == true -> copy(generalError = message)
                            else -> copy(generalError = "가입하지 못했어요. 잠시 후 다시 시도해 주세요.")
                        }
                    }
                }
            update { copy(isSubmitting = false) }
        }
    }

    private inline fun update(block: SignupForm.() -> SignupForm) {
        _form.value = _form.value.block()
    }
}
