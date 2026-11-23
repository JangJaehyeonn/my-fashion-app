package com.fashionapp.ui.mypage

import androidx.compose.ui.platform.LocalContext
import com.fashionapp.data.model.LegalPage
import com.fashionapp.ui.signup.openLegalPage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fashionapp.data.model.BodyType
import com.fashionapp.data.model.UserProfile
import com.fashionapp.ui.theme.WearonColors
import com.fashionapp.ui.theme.WearonShapes

@Composable
fun MyPageScreen(
    onLogout: () -> Unit,
    onEditBodyProfile: () -> Unit = {},
    onChangePassword: () -> Unit = {},
    viewModel: MyPageViewModel = hiltViewModel(),
    summaryViewModel: MySummaryViewModel = hiltViewModel()
) {
    val monthSummary by summaryViewModel.summary.collectAsState()
    val boughtRows by summaryViewModel.bought.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteError by viewModel.deleteError.collectAsState()
    val context = LocalContext.current
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRevokeDialog by remember { mutableStateOf(false) }

    // 체형·취향 설정에서 저장 후 돌아오면 다시 컴포지션되므로 여기서 갱신
    LaunchedEffect(Unit) {
        viewModel.loadProfile()
        // FR-20: this month's summary and bought pieces follow the server each time the tab shows
        summaryViewModel.load()
    }

    if (showRevokeDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeDialog = false },
            title = { Text("사진 전송 동의 철회") },
            text = {
                Text(
                    "철회하면 가상 피팅을 쓸 때 다시 동의해야 해요. 이미 외부 서비스로 보낸 사진에는 영향이 없어요.",
                    fontSize = 14.sp,
                    color = WearonColors.Ink
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.revokeVtonConsent()
                    showRevokeDialog = false
                }) { Text("철회", color = Color(0xFFD64545)) }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeDialog = false }) { Text("취소", color = WearonColors.Ink) }
            },
            containerColor = WearonColors.White
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isDeleting) {
                    showDeleteDialog = false
                    viewModel.clearDeleteError()
                }
            },
            title = { Text("회원 탈퇴") },
            text = {
                Column {
                    Text(
                        "탈퇴하면 등록한 옷 사진, 체형·취향 정보, 진단 기록이 모두 삭제되며 되돌릴 수 없어요.\n정말 탈퇴하시겠어요?",
                        fontSize = 14.sp,
                        color = WearonColors.Ink
                    )
                    deleteError?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, fontSize = 13.sp, color = Color(0xFFD64545))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = { viewModel.deleteAccount(onLogout) }
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(color = Color(0xFFD64545), strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("탈퇴하기", color = Color(0xFFD64545))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        showDeleteDialog = false
                        viewModel.clearDeleteError()
                    }
                ) { Text("취소", color = WearonColors.Ink) }
            },
            containerColor = WearonColors.White
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("로그아웃") },
            text = { Text("정말 로그아웃 하시겠습니까?") },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout(onLogout)
                }) { Text("로그아웃", color = Color(0xFFD64545)) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("취소", color = WearonColors.Ink) }
            },
            containerColor = WearonColors.White
        )
    }

    Scaffold(
        containerColor = WearonColors.Ivory,
        // 바깥 AppNavigation Scaffold가 이미 시스템 바 인셋을 적용하므로 중복 적용 방지
        contentWindowInsets = WindowInsets(0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 32.dp)
        ) {
            Text("마이", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)

            Spacer(Modifier.height(20.dp))
            if (isLoading) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WearonColors.Ink, strokeWidth = 2.dp)
                }
            } else {
                ProfileHeader(
                    nickname = profile?.nickname ?: "사용자",
                    // 아이디 가입 사용자는 이메일이 없어 아이디를 대신 표시
                    email = profile?.email ?: profile?.loginId?.let { "@$it" }.orEmpty(),
                    profileImageUrl = profile?.profileImageUrl
                )
                Spacer(Modifier.height(20.dp))
                MonthSummaryRow(monthSummary)
                if (boughtRows.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    BoughtList(boughtRows)
                }
                Spacer(Modifier.height(24.dp))
                BodyProfileCard(profile = profile, onEdit = onEditBodyProfile)
            }

            Spacer(Modifier.height(24.dp))
            Text("설정", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WearonShapes.Card)
                    .background(WearonColors.White)
                    .border(1.dp, WearonColors.Line, WearonShapes.Card)
            ) {
                MenuRow(label = "체형·취향 수정", onClick = onEditBodyProfile)
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                // 비밀번호가 있는 아이디 가입 사용자만 (Google·Kakao 로그인은 비밀번호가 없음)
                if (profile?.provider == "local") {
                    MenuRow(label = "비밀번호 변경", onClick = onChangePassword)
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                }
                // 동의한 사용자에게만 표시
                if (profile?.vtonConsentAt != null) {
                    MenuRow(label = "가상 피팅 사진 전송 동의 철회") { showRevokeDialog = true }
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                }
                // 서버가 로그인 없이 보여주는 정적 페이지 (릴리즈/디버그 빌드의 서버 주소 기준)
                MenuRow(label = "개인정보처리방침") { openLegalPage(context, LegalPage.PRIVACY) }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                MenuRow(label = "이용약관") { openLegalPage(context, LegalPage.TERMS) }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(WearonColors.Line))
                MenuRow(label = "로그아웃", color = WearonColors.SubText) { showLogoutDialog = true }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                "회원 탈퇴",
                fontSize = 12.sp,
                color = WearonColors.SubText,
                modifier = Modifier
                    .clickable { showDeleteDialog = true }
                    .padding(vertical = 6.dp, horizontal = 4.dp)
            )

            Spacer(Modifier.height(32.dp))
            Text(
                "Wearon v1.0",
                fontSize = 12.sp,
                color = WearonColors.SubText,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileHeader(nickname: String, email: String, profileImageUrl: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (profileImageUrl != null) {
            AsyncImage(
                model = profileImageUrl,
                contentDescription = "프로필",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(WearonColors.Beige)
            )
        } else {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(WearonColors.Beige),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = WearonColors.Ink, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(nickname, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
            if (email.isNotBlank()) {
                Text(email, fontSize = 13.sp, color = WearonColors.SubText)
            }
        }
    }
}

// FR-20: 이번 달 착용 기록 / 살까 말까 / 늘어난 코디 (베이지 3칸)
@Composable
private fun MonthSummaryRow(summary: MonthSummaryUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .background(WearonColors.Beige)
            .padding(vertical = 14.dp)
    ) {
        SummaryCell("이번 달 착용 기록", summary.wearText, Modifier.weight(1f))
        SummaryCell("살까 말까", summary.checkText, Modifier.weight(1f))
        SummaryCell("늘어난 코디", summary.plusText, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value ?: "—", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 11.sp, color = WearonColors.SubText)
    }
}

// FR-20: 살까 말까로 산 옷 — 사진 48dp · 이름 · 예상 1회 → 지금 1회 (착용 0회면 갈색 "아직 안 입었어요")
@Composable
private fun BoughtList(rows: List<BoughtRow>) {
    Column {
        Text("살까 말까로 산 옷", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = row.imageUrl,
                        contentDescription = row.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(48.dp).clip(WearonShapes.Card).background(WearonColors.Beige)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(row.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WearonColors.Ink, maxLines = 1)
                        Text(
                            row.costText,
                            fontSize = 12.sp,
                            fontWeight = if (row.unworn) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (row.unworn) WearonColors.Accent else WearonColors.SubText
                        )
                    }
                }
            }
        }
    }
}

// 키·몸무게·체형·스타일을 한눈에 보는 요약 카드 — 미설정이면 설정 유도
@Composable
private fun BodyProfileCard(profile: UserProfile?, onEdit: () -> Unit) {
    val bodyType = profile?.bodyType?.let { name -> BodyType.entries.find { it.name == name }?.label }
    val style = profile?.styleList?.takeIf { it.isNotEmpty() }?.joinToString(" · ") { style -> style.label.toList().joinToString("\u2060") }  // 단어 중간(예: 스트/릿)에서 줄바꿈되지 않게 글자 사이에 WORD JOINER
    val isEmpty = profile?.height == null && profile?.weight == null && bodyType == null && style == null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WearonShapes.Card)
            .background(WearonColors.Beige)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("내 체형·취향", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WearonColors.Ink, modifier = Modifier.weight(1f))
            Text(
                if (isEmpty) "설정하기" else "수정",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = WearonColors.Ink,
                modifier = Modifier.clickable(onClick = onEdit).padding(4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (isEmpty) "설정하면 체형에 맞는 코디를 추천해드려요" else "코디 추천에 반영되고 있어요",
            fontSize = 12.sp,
            color = WearonColors.SubText
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("키", profile?.height?.let { "${it}cm" }, Modifier.weight(1f))
            StatCell("몸무게", profile?.weight?.let { "${it}kg" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("체형", bodyType, Modifier.weight(1f))
            StatCell("스타일", style, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(WearonShapes.Image)
            .background(WearonColors.White)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(label, fontSize = 11.sp, color = WearonColors.SubText)
        Spacer(Modifier.height(2.dp))
        Text(
            value ?: "—",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (value != null) WearonColors.Ink else WearonColors.SubText
        )
    }
}

@Composable
private fun MenuRow(label: String, color: Color = WearonColors.Ink, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, color = color, modifier = Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = WearonColors.BeigeDeep, modifier = Modifier.size(20.dp))
    }
}
