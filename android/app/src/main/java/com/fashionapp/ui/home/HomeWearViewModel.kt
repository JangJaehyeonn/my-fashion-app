package com.fashionapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.WearRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** FR-20 '오늘 이거 입었어요' button of one look card. */
enum class WearButtonState { IDLE, SAVING, DONE }

/** FR-20 home card "이번 주 착용 기록 N회" + "가장 많이 입은 옷: {이름} · N회" (null fields = not shown). */
data class WeekWearCard(
    val weekText: String,
    val mostWornText: String?
)

/**
 * FR-20: records a recommended look as worn (FR-10) and shows this week's wear card on the home tab.
 * A look recorded today stays done until the day changes, even if the same look is recommended again.
 */
@HiltViewModel
class HomeWearViewModel @Inject constructor(
    private val wearRepository: WearRepository,
    private val closetChanges: ClosetChanges,
    private val clock: Clock
) : ViewModel() {

    private val _weekCard = MutableStateFlow<WeekWearCard?>(null)
    val weekCard = _weekCard.asStateFlow()

    // Look key -> day it was recorded in this session
    private val _recorded = MutableStateFlow<Map<String, LocalDate>>(emptyMap())
    val recorded = _recorded.asStateFlow()

    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    val saving = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    /** Called whenever the home tab shows: the card always reflects the server values. */
    fun loadSummary() {
        viewModelScope.launch {
            wearRepository.getSummary().onSuccess { summary ->
                _weekCard.value = WeekWearCard(
                    weekText = "이번 주 착용 기록 ${summary.weekCount}회",
                    mostWornText = summary.mostWorn?.let { "가장 많이 입은 옷: ${it.name ?: "이름 없는 옷"} · ${it.count}회" }
                )
            }
        }
    }

    fun buttonState(look: LookCard): WearButtonState {
        val key = keyOf(look)
        val today = LocalDate.now(clock)
        return when {
            key in _saving.value -> WearButtonState.SAVING
            _recorded.value[key] == today || wornToday(look, today) -> WearButtonState.DONE
            else -> WearButtonState.IDLE
        }
    }

    fun recordWorn(look: LookCard) {
        if (look.items.isEmpty() || buttonState(look) != WearButtonState.IDLE) return
        val key = keyOf(look)
        _saving.update { it + key }
        viewModelScope.launch {
            wearRepository.recordToday(look.items.map { it.id })
                .onSuccess {
                    _recorded.update { it + (key to LocalDate.now(clock)) }
                    // The closet tab shows wear counts: reload it, and refresh this week's card
                    closetChanges.notifyChanged()
                    loadSummary()
                }
                .onFailure { _message.value = MSG_RECORD_FAILED }
            _saving.update { it - key }
        }
    }

    fun clearMessage() { _message.value = null }

    // Every piece already has today's wear on the server (e.g. recorded before the app restarted)
    private fun wornToday(look: LookCard, today: LocalDate): Boolean {
        val day = today.toString()
        return look.items.isNotEmpty() && look.items.all { it.lastWornAt?.startsWith(day) == true }
    }

    companion object {
        const val MSG_RECORD_FAILED = "착용 기록을 남기지 못했어요. 잠시 후 다시 시도해 주세요."

        fun keyOf(look: LookCard): String = look.items.map { it.id }.sorted().joinToString(",")
    }
}
