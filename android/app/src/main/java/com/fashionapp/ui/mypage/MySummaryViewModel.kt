package com.fashionapp.ui.mypage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.PurchaseDecision
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.repository.PurchaseCheckRepository
import com.fashionapp.data.repository.WearRepository
import com.fashionapp.ui.purchase.PurchaseResultFormatter.won
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** FR-20 my tab "이번 달" cells; null = the server value could not be loaded ("—"). */
data class MonthSummaryUi(
    val wearText: String? = null,
    val checkText: String? = null,
    val plusText: String? = null
)

/** FR-20 one row of "살까 말까로 산 옷". [unworn] rows show [costText] in the accent brown. */
data class BoughtRow(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val costText: String,
    val unworn: Boolean
)

/** FR-20: this month's summary and the pieces bought through 살까 말까, all from server values. */
@HiltViewModel
class MySummaryViewModel @Inject constructor(
    private val wearRepository: WearRepository,
    private val purchaseCheckRepository: PurchaseCheckRepository
) : ViewModel() {

    private val _summary = MutableStateFlow(MonthSummaryUi())
    val summary = _summary.asStateFlow()

    private val _bought = MutableStateFlow<List<BoughtRow>>(emptyList())
    val bought = _bought.asStateFlow()

    /** Called whenever the my tab shows. A failed part keeps its previous value. */
    fun load() {
        viewModelScope.launch {
            val wear = async { wearRepository.getSummary() }
            val purchase = async { purchaseCheckRepository.summary() }
            val history = async { purchaseCheckRepository.history() }
            val current = _summary.value
            _summary.value = MonthSummaryUi(
                wearText = wear.await().getOrNull()?.let { "${it.monthCount}회" } ?: current.wearText,
                checkText = purchase.await().getOrNull()?.let { "${it.monthCount}회" } ?: current.checkText,
                plusText = purchase.await().getOrNull()?.let { "+${it.monthPlus}개" } ?: current.plusText
            )
            history.await().onSuccess { records -> _bought.value = records.mapNotNull(::toBoughtRow) }
        }
    }

    companion object {
        const val MSG_UNWORN = "아직 안 입었어요"

        /** Bought pieces that still exist (the server sends wearCount only for those). */
        fun toBoughtRow(record: PurchaseRecord): BoughtRow? {
            if (record.decision != PurchaseDecision.BOUGHT) return null
            val wears = record.wearCount ?: return null
            return BoughtRow(
                id = record.id,
                name = record.name ?: "이름 없는 옷",
                imageUrl = record.imageUrl,
                costText = costText(record.expectedCostPerWear, record.currentCostPerWear, wears),
                unworn = wears == 0
            )
        }

        fun costText(expected: Long?, current: Long?, wears: Int): String = when {
            wears == 0 -> MSG_UNWORN
            expected != null && current != null -> "예상 1회 ${won(expected)} → 지금 1회 ${won(current)}"
            current != null -> "지금 1회 ${won(current)} · 착용 ${wears}회"
            else -> "착용 ${wears}회"
        }
    }
}
