package com.fashionapp.ui.mypage

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

data class ChangePasswordForm(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val submitted: Boolean = false,
    val currentError: String? = null,   // 서버 판단 (현재 비밀번호 틀림)
    val serverNewError: String? = null, // 서버 판단 (규칙 위반·같은 비밀번호)
    val generalError: String? = null,
    val isSubmitting: Boolean = false
) {
    val currentFieldError: String?
        get() = currentError ?: if (submitted && current.isEmpty()) "현재 비밀번호를 입력해 주세요." else null

    val newError: String?
        get() = serverNewError ?: when {
            !(submitted || new.isNotEmpty()) -> null
            AuthRules.passwordError(new) != null -> AuthRules.passwordError(new)
            new == current -> "지금과 다른 비밀번호를 입력해 주세요."
            else -> null
        }

    val confirmError: String?
        get() = if ((submitted || confirm.isNotEmpty()) && confirm != new) "비밀번호가 일치하지 않아요." else null
}

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _form = MutableStateFlow(ChangePasswordForm())
    val form = _form.asStateFlow()

    fun onCurrentChange(v: String) = update { copy(current = v, currentError = null, generalError = null) }
    fun onNewChange(v: String) = update { copy(new = v, serverNewError = null, generalError = null) }
    fun onConfirmChange(v: String) = update { copy(confirm = v, generalError = null) }

    fun submit(onSuccess: () -> Unit) {
        val f = _form.value.copy(submitted = true)
        _form.value = f
        if (f.isSubmitting || f.currentFieldError != null || f.newError != null || f.confirmError != null) return

        update { copy(isSubmitting = true) }
        viewModelScope.launch {
            authRepository.changePassword(f.current, f.new)
                .onSuccess { onSuccess() }
                .onFailure { e ->
                    val message = e.serverMessage()
                    update {
                        when {
                            e.httpStatus == 400 && message?.startsWith("현재") == true -> copy(currentError = message)
                            e.httpStatus == 400 && message != null -> copy(serverNewError = message)
                            else -> copy(generalError = "비밀번호를 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.")
                        }
                    }
                }
            update { copy(isSubmitting = false) }
        }
    }

    private inline fun update(block: ChangePasswordForm.() -> ChangePasswordForm) {
        _form.value = _form.value.block()
    }
}
