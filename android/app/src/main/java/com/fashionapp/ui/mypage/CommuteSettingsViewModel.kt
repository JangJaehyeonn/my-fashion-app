package com.fashionapp.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.TravelMode
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import com.fashionapp.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** FR-30 commute settings form. Null times mean "not set" (the server then uses 08:00 and 19:00). */
data class CommuteForm(
    val outAt: LocalTime? = null,
    val backAt: LocalTime? = null,
    val travelMode: TravelMode? = null
)

/**
 * FR-30 마이 > 출퇴근 시각·이동 방식 (FR-28 profile fields). The home timeline reads these on the server,
 * so the home tab refreshes its timeline when it shows again after saving.
 */
@HiltViewModel
class CommuteSettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _profile = MutableStateFlow<UserProfile?>(null)

    private val _form = MutableStateFlow(CommuteForm())
    val form = _form.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.getMyProfile()
                .onSuccess { profile ->
                    _profile.value = profile
                    _form.value = formOf(profile)
                }
                .onFailure { _message.value = MSG_LOAD_FAILED }
            _isLoading.value = false
        }
    }

    fun setOutAt(time: LocalTime) = _form.update { it.copy(outAt = time) }

    fun setBackAt(time: LocalTime) = _form.update { it.copy(backAt = time) }

    /** Tapping the selected mode again unselects it. */
    fun selectTravelMode(mode: TravelMode) =
        _form.update { it.copy(travelMode = if (it.travelMode == mode) null else mode) }

    /** Back to "not set": the timeline uses 8시 · 13시 · 19시. */
    fun clearTimes() = _form.update { it.copy(outAt = null, backAt = null) }

    fun save(onSaved: () -> Unit) {
        val current = _profile.value ?: return
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            authRepository.updateProfile(requestOf(current, _form.value))
                .onSuccess {
                    _profile.value = it
                    onSaved()
                }
                .onFailure { _message.value = MSG_SAVE_FAILED }
            _isSaving.value = false
        }
    }

    fun clearMessage() { _message.value = null }

    companion object {
        const val MENU_LABEL = "출퇴근 시각·이동 방식"
        const val MSG_LOAD_FAILED = "프로필을 불러올 수 없어요."
        const val MSG_SAVE_FAILED = "저장에 실패했어요. 잠시 후 다시 시도해 주세요."

        private val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /** "08:00" on the button; "기본 8시" when not set. */
        fun outLabel(form: CommuteForm): String = form.outAt?.format(HH_MM) ?: "기본 8시"

        fun backLabel(form: CommuteForm): String = form.backAt?.format(HH_MM) ?: "기본 19시"

        fun formOf(profile: UserProfile) = CommuteForm(
            outAt = parseTime(profile.commuteOutAt),
            backAt = parseTime(profile.commuteBackAt),
            travelMode = TravelMode.parse(profile.travelMode)
        )

        /**
         * The server overwrites body fields with what it receives, so the current ones are sent back unchanged.
         * Commute fields: "" clears the stored value (FR-28).
         */
        fun requestOf(profile: UserProfile, form: CommuteForm) = UpdateProfileRequest(
            nickname = profile.nickname,
            height = profile.height,
            weight = profile.weight,
            bodyType = profile.bodyType,
            preferredStyles = profile.styleList.map { it.name },
            commuteOutAt = form.outAt?.format(HH_MM) ?: "",
            commuteBackAt = form.backAt?.format(HH_MM) ?: "",
            travelMode = form.travelMode?.name ?: ""
        )

        private fun parseTime(raw: String?): LocalTime? =
            raw?.let { runCatching { LocalTime.parse(it, HH_MM) }.getOrNull() }
    }
}
