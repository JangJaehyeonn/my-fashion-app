package com.fashionapp.ui.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fashionapp.data.model.ShoppingRecommendRequest
import com.fashionapp.data.model.ShoppingRecommendResponse
import com.fashionapp.data.model.Situation
import com.fashionapp.data.repository.ShoppingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShoppingViewModel @Inject constructor(
    private val shoppingRepository: ShoppingRepository
) : ViewModel() {

    private val _budgetText = MutableStateFlow("")
    val budgetText = _budgetText.asStateFlow()

    private val _selectedSituation = MutableStateFlow(Situation.WORK)
    val selectedSituation = _selectedSituation.asStateFlow()

    private val _result = MutableStateFlow<ShoppingRecommendResponse?>(null)
    val result = _result.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun updateBudget(text: String) {
        _budgetText.value = text.filter { it.isDigit() }
    }

    fun selectSituation(situation: Situation) {
        _selectedSituation.value = situation
    }

    fun recommend() {
        val budget = _budgetText.value.toIntOrNull()
        if (budget == null || budget <= 0) {
            _errorMessage.value = "예산을 올바르게 입력해주세요."
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            shoppingRepository.recommend(
                ShoppingRecommendRequest(budget, _selectedSituation.value.name)
            ).onSuccess { _result.value = it }
                .onFailure { _errorMessage.value = "쇼핑 추천에 실패했습니다." }
            _isLoading.value = false
        }
    }

    fun clearError() { _errorMessage.value = null }
}
