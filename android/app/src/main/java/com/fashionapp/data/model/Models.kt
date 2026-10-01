package com.fashionapp.data.model

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)

// Auth
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

// User
data class UserProfile(
    val id: String,
    val email: String,
    val nickname: String,
    val profileImageUrl: String?,
    val provider: String,
    val height: Int?,
    val weight: Int?,
    val bodyType: String?,
    // 옛 서버 호환용 단일 값(선택한 스타일 중 첫 번째). 다중 선택은 preferredStyles
    val preferredStyle: String?,
    val preferredStyles: List<String>? = null
) {
    /** 선택한 선호 스타일 (서버가 아직 preferredStyles를 안 주면 단일 값으로 폴백) */
    val styleList: List<PreferredStyle>
        get() = (preferredStyles ?: listOfNotNull(preferredStyle))
            .mapNotNull { name -> PreferredStyle.entries.find { it.name == name } }
}

data class UpdateProfileRequest(
    val nickname: String?,
    val height: Int?,
    val weight: Int?,
    val bodyType: String?,
    val preferredStyle: String? = null,
    val preferredStyles: List<String>? = null
)

enum class BodyType(val label: String) {
    SLIM("슬림"), NORMAL("보통"), MUSCULAR("근육질"), CHUBBY("통통")
}

enum class PreferredStyle(val label: String) {
    CASUAL("캐주얼"), FORMAL("포멀"), SPORTY("스포티"),
    STREET("스트릿"), VINTAGE("빈티지"), MINIMAL("미니멀")
}

enum class Situation(val label: String) {
    WORK("출근"), DATE("데이트"), EXERCISE("운동"),
    TRAVEL("여행"), INTERVIEW("면접"), DAILY("데일리")
}

// Weather
data class Weather(
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double
)

// Recommend request (weather + situation) — 옷장 기반 추천에서도 동일하게 사용
data class SituationRecommendRequest(
    val temperature: Double,
    val condition: String,
    val situation: String
)


// Style diagnosis
data class SimilarStyleSuggestion(
    val styleTag: String,
    val description: String
)

data class StyleDiagnosis(
    val id: String,
    val imageUrl: String,
    val score: Int,
    val feedback: String,
    val similarStyles: List<SimilarStyleSuggestion>,
    val createdAt: String
)

// Shopping assistant
data class ShoppingRecommendRequest(
    val budget: Int,
    val situation: String
)

data class ShoppingItemSuggestion(
    val item: String,
    val reason: String,
    val estimatedPrice: Int,
    val site: String,
    val searchKeyword: String
)

data class ShoppingRecommendResponse(
    val items: List<ShoppingItemSuggestion>,
    val totalEstimatedPrice: Int,
    val usageTip: String
)

// Virtual try-on
data class VtonResult(
    val resultImageUrl: String
)

// Closet (옷장)
enum class ClothesCategory(val label: String) {
    TOP("상의"), BOTTOM("하의"), OUTER("아우터"), SHOES("신발"), ETC("기타")
}

data class Clothes(
    val id: String,
    val imageUrl: String,
    val category: ClothesCategory,
    val color: String?,
    val name: String?,
    val createdAt: String?
)

// Closet-based recommend (내 옷장 옷들로 코디 조합)
data class ClosetOutfit(
    val items: List<Clothes>,
    val reason: String,
    val styleTag: String
)

data class ClosetRecommendResponse(
    val outfits: List<ClosetOutfit>
)
