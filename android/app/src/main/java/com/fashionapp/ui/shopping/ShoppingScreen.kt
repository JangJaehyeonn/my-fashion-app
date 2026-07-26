package com.fashionapp.ui.shopping

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.ShoppingItemSuggestion
import com.fashionapp.data.model.Situation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(viewModel: ShoppingViewModel = hiltViewModel()) {
    val budgetText by viewModel.budgetText.collectAsState()
    val selectedSituation by viewModel.selectedSituation.collectAsState()
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("쇼핑 도우미", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = budgetText,
                    onValueChange = { viewModel.updateBudget(it) },
                    label = { Text("예산 (원)") },
                    placeholder = { Text("예: 150000") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSituation.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("상황") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        Situation.entries.forEach { situation ->
                            DropdownMenuItem(
                                text = { Text(situation.label) },
                                onClick = {
                                    viewModel.selectSituation(situation)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { viewModel.recommend() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                    } else {
                        Text(if (result == null) "🛒  아이템 추천받기" else "🔄  다시 추천받기")
                    }
                }
            }

            result?.let { response ->
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0x2200C853)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "총 예상 금액 ${"%,d".format(response.totalEstimatedPrice)}원",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
                            Text(response.usageTip, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)
                        }
                    }
                }

                items(response.items) { suggestion ->
                    ShoppingItemCard(suggestion)
                }
            }
        }
    }
}

@Composable
private fun ShoppingItemCard(suggestion: ShoppingItemSuggestion) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(suggestion.item, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            Text("약 ${"%,d".format(suggestion.estimatedPrice)}원", fontSize = 13.sp, color = Color(0xFF888888))
            androidx.compose.foundation.layout.Spacer(Modifier.height(6.dp))
            Text(suggestion.reason, fontSize = 14.sp, color = Color(0xFF444444), lineHeight = 22.sp)
            androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
            Text(
                "🛍️ ${suggestion.site}에서 '${suggestion.searchKeyword}' 검색해보세요",
                fontSize = 13.sp,
                color = Color(0xFF1A73E8)
            )
        }
    }
}
