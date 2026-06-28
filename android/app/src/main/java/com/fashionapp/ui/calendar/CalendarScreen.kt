package com.fashionapp.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fashionapp.data.model.Outfit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: CalendarViewModel = hiltViewModel()) {
    val currentMonth by viewModel.currentMonth.collectAsState()
    val entries by viewModel.calendarEntries.collectAsState()
    val outfits by viewModel.outfits.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val entryDates = entries.map { it.wornDate }.toSet()

    Scaffold(
        topBar = { TopAppBar(title = { Text("코디 캘린더", fontWeight = FontWeight.Bold) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    // 월 네비게이션
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.previousMonth() }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "이전 달")
                        }
                        Text(
                            text = currentMonth.format(DateTimeFormatter.ofPattern("yyyy년 MM월")),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.nextMonth() }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "다음 달")
                        }
                    }

                    // 요일 헤더
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("일", "월", "화", "수", "목", "금", "토").forEach { day ->
                            Text(
                                text = day,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // 날짜 그리드
                    CalendarGrid(
                        yearMonth = currentMonth,
                        entryDates = entryDates,
                        selectedDate = selectedDate,
                        onDateClick = { date -> selectedDate = if (selectedDate == date) null else date }
                    )
                }
            }

            // 선택된 날짜의 코디 기록
            val selectedEntries = selectedDate?.let { date ->
                entries.filter { it.wornDate == date.toString() }
            } ?: emptyList()

            if (selectedDate != null) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedDate!!.format(DateTimeFormatter.ofPattern("M월 d일"))} 착용 기록",
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = { showAddDialog = true },
                            enabled = outfits.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "기록 추가")
                        }
                    }
                }

                if (selectedEntries.isEmpty()) {
                    item {
                        Text(
                            text = if (outfits.isEmpty()) "저장된 코디가 없습니다" else "이 날의 기록이 없습니다",
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                } else {
                    items(selectedEntries) { entry ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.outfitName ?: "코디", fontWeight = FontWeight.SemiBold)
                                    entry.memo?.let { Text(it, fontSize = 13.sp, color = Color.Gray) }
                                }
                                IconButton(onClick = { viewModel.deleteEntry(entry.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "삭제", tint = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog && selectedDate != null) {
        AddCalendarEntryDialog(
            outfits = outfits,
            onConfirm = { outfitId, memo ->
                viewModel.addEntry(outfitId, selectedDate!!, memo)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCalendarEntryDialog(
    outfits: List<Outfit>,
    onConfirm: (outfitId: String, memo: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedOutfit by remember { mutableStateOf(outfits.firstOrNull()) }
    var memo by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("착용 기록 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedOutfit?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("코디 선택") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        outfits.forEach { outfit ->
                            DropdownMenuItem(
                                text = { Text(outfit.name) },
                                onClick = {
                                    selectedOutfit = outfit
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = memo,
                    onValueChange = { memo = it },
                    label = { Text("메모 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedOutfit?.let { onConfirm(it.id, memo.ifBlank { null }) }
                },
                enabled = selectedOutfit != null
            ) {
                Text("저장")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        }
    )
}

@Composable
fun CalendarGrid(
    yearMonth: YearMonth,
    entryDates: Set<String>,
    selectedDate: LocalDate?,
    onDateClick: (LocalDate) -> Unit
) {
    val firstDay = yearMonth.atDay(1)
    val totalDays = yearMonth.lengthOfMonth()
    val startOffset = firstDay.dayOfWeek.value % 7
    val totalCells = startOffset + totalDays
    val rows = (totalCells + 6) / 7

    Column {
        repeat(rows) { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { col ->
                    val dayIndex = row * 7 + col - startOffset + 1
                    if (dayIndex in 1..totalDays) {
                        val date = yearMonth.atDay(dayIndex)
                        val hasEntry = date.toString() in entryDates
                        val isSelected = date == selectedDate
                        val isToday = date == LocalDate.now()

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isSelected -> MaterialTheme.colorScheme.primary
                                        isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onDateClick(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayIndex.toString(),
                                    fontSize = 13.sp,
                                    color = when {
                                        isSelected -> Color.White
                                        col == 0 -> Color.Red
                                        col == 6 -> Color(0xFF4444FF)
                                        else -> Color(0xFF1A1A1A)
                                    }
                                )
                                if (hasEntry) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .background(
                                                if (isSelected) Color.White else MaterialTheme.colorScheme.secondary,
                                                CircleShape
                                            )
                                    )
                                }
                            }
                        }
                    } else {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}
