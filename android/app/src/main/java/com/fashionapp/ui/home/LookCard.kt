package com.fashionapp.ui.home

import com.fashionapp.data.model.ShoppingSuggestion
import com.fashionapp.data.model.SituationOutfitSuggestion

// 카드에 표시되는 순서대로 선언 (아우터 → 상의 → 하의 → 신발 → 기타)
enum class PieceCategory(val label: String) {
    OUTER("아우터"), DRESS("원피스"), TOP("상의"), BOTTOM("하의"), SHOES("신발"), ETC("기타")
}

data class LookPiece(val category: PieceCategory, val name: String)

data class LookCard(
    val pieces: List<LookPiece>,
    val reason: String,
    val styleTag: String,
    val shoppingSuggestions: List<ShoppingSuggestion>
)

// 추천 API는 description을 "네이비 셔츠 + 베이지 치노 팬츠 + 로퍼" 형태의 한 문자열로 주므로
// API 변경 없이 클라이언트에서 " + " 기준으로 나눈 뒤 키워드로 카테고리를 추정한다.
fun SituationOutfitSuggestion.toLookCard() = LookCard(
    pieces = description.split("+")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { LookPiece(categorize(it), it) }
        .sortedBy { it.category.ordinal },
    reason = reason,
    styleTag = styleTag,
    shoppingSuggestions = shoppingSuggestions
)

// 검사 순서가 중요: "데님 자켓"은 아우터, "부츠컷 팬츠"는 하의로 먼저 걸러야 함
private val KEYWORDS = listOf(
    PieceCategory.OUTER to listOf("자켓", "재킷", "코트", "패딩", "점퍼", "블루종", "가디건", "카디건", "아우터", "트렌치", "무스탕", "바람막이", "야상", "블레이저", "집업"),
    PieceCategory.DRESS to listOf("원피스", "드레스"),
    PieceCategory.BOTTOM to listOf("팬츠", "바지", "슬랙스", "청바지", "데님 진", "조거", "쇼츠", "스커트", "치마", "레깅스", "트라우저", "치노"),
    PieceCategory.SHOES to listOf("스니커즈", "운동화", "로퍼", "구두", "부츠", "샌들", "슬리퍼", "힐", "플랫", "슈즈", "뮬", "더비", "옥스포드", "러닝화", "워커"),
    PieceCategory.TOP to listOf("셔츠", "티", "니트", "스웨터", "맨투맨", "후드", "블라우스", "탑", "폴로", "터틀넥", "베스트", "조끼", "나시", "슬리브리스", "스웻"),
)

private fun categorize(name: String): PieceCategory =
    KEYWORDS.firstOrNull { (_, words) -> words.any { name.contains(it) } }?.first ?: PieceCategory.ETC
