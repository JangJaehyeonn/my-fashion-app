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
    val preferredStyle: String?
)

data class UpdateProfileRequest(
    val nickname: String?,
    val height: Int?,
    val weight: Int?,
    val bodyType: String?,
    val preferredStyle: String?
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
    TRAVEL("여행"), INTERVIEW("면접"), DAILY("일상")
}

// Clothes
data class Clothes(
    val id: String,
    val imageUrl: String,
    val category: String?,
    val color: String?,
    val pattern: String?,
    val season: String?,
    val styleTag: String?,
    val createdAt: String
)

// Outfit
data class OutfitItem(
    val id: String,
    val clothes: Clothes
)

data class Outfit(
    val id: String,
    val name: String,
    val styleTag: String?,
    val weatherCondition: String?,
    val items: List<OutfitItem>,
    val createdAt: String
)

data class OutfitCreateRequest(
    val name: String,
    val styleTag: String?,
    val weatherCondition: String?,
    val clothesIds: List<String>
)

// Calendar
data class OutfitCalendar(
    val id: String,
    val outfitId: String,
    val outfitName: String?,
    val wornDate: String,
    val memo: String?
)

data class CalendarCreateRequest(
    val outfitId: String,
    val wornDate: String,
    val memo: String? = null
)

// Weather
data class Weather(
    val temperature: Double,
    val condition: String,
    val humidity: Int,
    val windSpeed: Double
)

// Recommend
data class RecommendRequest(
    val temperature: Double,
    val condition: String
)

data class RecommendedOutfit(
    val clothesIds: List<String>,
    val reason: String,
    val styleTag: String
)

data class RecommendResponse(
    val outfits: List<RecommendedOutfit>
)

// Situation-based recommend (no wardrobe)
data class SituationRecommendRequest(
    val temperature: Double,
    val condition: String,
    val situation: String
)

data class SituationOutfitSuggestion(
    val description: String,
    val reason: String,
    val styleTag: String
)

data class SituationRecommendResponse(
    val outfits: List<SituationOutfitSuggestion>
)
