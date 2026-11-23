package com.fashionapp.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.PurchaseDecision
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.PurchaseCheckRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeParseException
import javax.inject.Inject

/** Result badge of a recent check: bought = black, skipped = light beige. Undecided checks have no badge. */
enum class ResultBadge(val label: String) {
    BOUGHT("샀어요"), SKIPPED("안 샀어요")
}

/** One row of the recent purchase check list (FR-16). */
data class RecentCheckRow(
    val id: String,
    val title: String,
    val category: ClothesCategory?,
    /** Only bought checks keep a photo; the others show a category icon. */
    val photoUrl: String?,
    val subtitle: String,
    val badge: ResultBadge?,
    val plusText: String?
)

data class ShoppingHomeState(
    val isLoading: Boolean = false,
    val closetCount: Int? = null,
    val comboCount: Int? = null,
    val recentChecks: List<RecentCheckRow> = emptyList(),
    val errorMessage: String? = null
) {
    /** Capture, gallery and product URL buttons are enabled only with a big enough closet. */
    val canCompare: Boolean
        get() = closetCount != null && closetCount >= MIN_CLOSET_SIZE

    val showClosetGuide: Boolean
        get() = closetCount != null && closetCount < MIN_CLOSET_SIZE

    companion object {
        /** Same minimum as the backend purchase check (PurchaseCheckService.MIN_CLOSET_SIZE). */
        const val MIN_CLOSET_SIZE = 5
    }
}

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val clothesRepository: ClothesRepository,
    private val purchaseCheckRepository: PurchaseCheckRepository,
    private val zoneId: ZoneId
) : ViewModel() {

    private val _state = MutableStateFlow(ShoppingHomeState())
    val state = _state.asStateFlow()

    /** Loads closet size, combo count and recent checks together. A failed part keeps its previous value. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val clothes = async { clothesRepository.getMyClothes() }
            val summary = async { clothesRepository.getSummary() }
            val history = async { purchaseCheckRepository.history() }
            val clothesResult = clothes.await()
            val summaryResult = summary.await()
            val historyResult = history.await()
            val failed = listOf(clothesResult, summaryResult, historyResult).any { it.isFailure }
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    closetCount = clothesResult.getOrNull()?.size ?: current.closetCount,
                    comboCount = summaryResult.getOrNull()?.comboCount ?: current.comboCount,
                    recentChecks = historyResult.getOrNull()?.map { toRow(it) } ?: current.recentChecks,
                    errorMessage = if (failed) MSG_LOAD_FAILED else null
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(errorMessage = null) }

    private fun toRow(record: PurchaseRecord): RecentCheckRow {
        val bought = record.decision == PurchaseDecision.BOUGHT
        return RecentCheckRow(
            id = record.id,
            title = record.name?.takeIf { it.isNotBlank() } ?: record.category?.label ?: DEFAULT_TITLE,
            category = record.category,
            photoUrl = if (bought) record.imageUrl else null,
            subtitle = listOfNotNull(dateText(record.createdAt), record.verdict?.label).joinToString(" · "),
            badge = when (record.decision) {
                PurchaseDecision.BOUGHT -> ResultBadge.BOUGHT
                PurchaseDecision.SKIPPED -> ResultBadge.SKIPPED
                else -> null
            },
            plusText = record.plus?.let { "+$it" }
        )
    }

    private fun dateText(createdAt: String?): String? {
        if (createdAt == null) return null
        return try {
            val date = Instant.parse(createdAt).atZone(zoneId).toLocalDate()
            "${date.monthValue}월 ${date.dayOfMonth}일"
        } catch (e: DateTimeParseException) {
            null
        }
    }

    companion object {
        const val MSG_LOAD_FAILED = "쇼핑 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        private const val DEFAULT_TITLE = "옷"
    }
}
