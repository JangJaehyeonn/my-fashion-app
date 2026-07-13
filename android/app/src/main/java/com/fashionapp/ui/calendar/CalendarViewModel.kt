package com.fashionapp.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.CalendarCreateRequest
import com.fashionapp.data.model.Outfit
import com.fashionapp.data.model.OutfitCalendar
import com.fashionapp.data.repository.OutfitRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val outfitRepository: OutfitRepository
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    val currentMonth = _currentMonth.asStateFlow()

    private val _calendarEntries = MutableStateFlow<List<OutfitCalendar>>(emptyList())
    val calendarEntries = _calendarEntries.asStateFlow()

    private val _outfits = MutableStateFlow<List<Outfit>>(emptyList())
    val outfits = _outfits.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    init {
        loadCalendar()
        loadOutfits()
    }

    fun loadCalendar() {
        viewModelScope.launch {
            val month = _currentMonth.value
            outfitRepository.getCalendar(month.year, month.monthValue)
                .onSuccess { _calendarEntries.value = it }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    private fun loadOutfits() {
        viewModelScope.launch {
            outfitRepository.getOutfits()
                .onSuccess { _outfits.value = it }
                .onFailure { _errorMessage.value = "코디 불러오기 실패: ${it.message}" }
        }
    }

    fun previousMonth() {
        _currentMonth.value = _currentMonth.value.minusMonths(1)
        loadCalendar()
    }

    fun nextMonth() {
        _currentMonth.value = _currentMonth.value.plusMonths(1)
        loadCalendar()
    }

    fun addEntry(outfitId: String, date: LocalDate, memo: String?) {
        viewModelScope.launch {
            outfitRepository.addCalendarEntry(
                CalendarCreateRequest(outfitId, date.toString(), memo)
            ).onSuccess { loadCalendar() }
             .onFailure { _errorMessage.value = it.message }
        }
    }

    fun deleteEntry(id: String) {
        viewModelScope.launch {
            outfitRepository.deleteCalendarEntry(id)
                .onSuccess { loadCalendar() }
                .onFailure { _errorMessage.value = it.message }
        }
    }

    fun clearError() { _errorMessage.value = null }
}
