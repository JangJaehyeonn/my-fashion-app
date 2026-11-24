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

// 아이디·비밀번호 로그인 (data class라 toString에 비밀번호가 나오지만, 앱은 이 객체를 로그로 남기지 않음 —
// 디버그 빌드의 HTTP 본문 로그에는 보이므로 디버그 로그를 외부에 공유하지 말 것)
data class LoginRequest(
    val loginId: String,
    val password: String
)

data class SignupRequest(
    val loginId: String,
    val password: String,
    val nickname: String,
    // "만 14세 이상입니다" 체크와 이용약관·개인정보처리방침 동의 — 서버가 셋 다 true가 아니면 400 (FR-05)
    val ageOver14: Boolean,
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean
)

// 소셜 첫 로그인 확인: POST /api/users/me/age-confirmation 본문 (셋 다 true여야 200, 아니면 400)
data class AgeConfirmationRequest(
    val ageOver14: Boolean,
    val termsAgreed: Boolean,
    val privacyAgreed: Boolean
)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

data class CheckIdResponse(
    val available: Boolean
)

// OAuth 로그인 딥링크(fashionapp://app/oauth2/callback?code=...)로 받은 1회용 코드 → 토큰 교환
data class ExchangeRequest(
    val code: String
)

// User
data class UserProfile(
    val id: String,
    // 아이디 가입·카카오 사용자는 이메일이 없을 수 있음
    val email: String?,
    val nickname: String,
    val profileImageUrl: String?,
    // google / kakao / local
    val provider: String,
    // 아이디·비밀번호 가입 사용자만 값이 있음
    val loginId: String? = null,
    val height: Int?,
    val weight: Int?,
    val bodyType: String?,
    // 옛 서버 호환용 단일 값(선택한 스타일 중 첫 번째). 다중 선택은 preferredStyles
    val preferredStyle: String?,
    val preferredStyles: List<String>? = null,
    // 만 14세 이상 확인 시각. null이면 미확인 → 확인 화면(소셜 첫 가입·기존 사용자)
    val ageConfirmedAt: String? = null,
    // 가상 피팅 사진 전송(Hugging Face) 동의 시각. null이면 미동의
    val vtonConsentAt: String? = null,
    // FR-28 "HH:mm" (null = not set; the server uses 08:00 / 19:00) and WALK/TRANSIT/CAR
    val commuteOutAt: String? = null,
    val commuteBackAt: String? = null,
    val travelMode: String? = null
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
    val preferredStyles: List<String>? = null,
    // FR-28: null (omitted) keeps the stored value, "" clears it
    val commuteOutAt: String? = null,
    val commuteBackAt: String? = null,
    val travelMode: String? = null
)

// FR-28 how the user commutes (stored only; not used by rules yet)
enum class TravelMode(val label: String) {
    WALK("도보"), TRANSIT("대중교통"), CAR("자가용");

    companion object {
        fun parse(raw: String?): TravelMode? = entries.find { it.name == raw }
    }
}

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
    val situation: String,
    // FR-28: where the user is, so the server applies the day forecast rules. null = no forecast rules
    val lat: Double? = null,
    val lon: Double? = null
)

// FR-28 GET /api/weather/day: going out, midday and coming back. tempRange = highest - lowest of the three.
data class DayWeather(
    val out: DayWeatherPoint,
    val day: DayWeatherPoint,
    val back: DayWeatherPoint,
    val tempRange: Double
)

// time is "HH:mm" (Korean time), precipitationProbability is 0..100
data class DayWeatherPoint(
    val time: String,
    val temperature: Double,
    val precipitationProbability: Int,
    val windSpeed: Double
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

// Virtual try-on
data class VtonResult(
    val resultImageUrl: String
)

// Closet (옷장)
enum class ClothesCategory(val label: String) {
    TOP("상의"), BOTTOM("하의"), OUTER("아우터"), SHOES("신발"), ETC("기타"),
    // Backend FR-01: AI classification failed. Without this value Gson would leave category null.
    UNCLASSIFIED("미분류")
}

data class Clothes(
    val id: String,
    val imageUrl: String,
    val category: ClothesCategory,
    val color: String?,
    val name: String?,
    val createdAt: String?,
    val classificationFailed: Boolean = false,
    // FR-09/FR-10: filled by the server in the clothes list only; null elsewhere (unknown)
    val comboCount: Int? = null,
    val wearCount: Int? = null,
    val lastWornAt: String? = null,
    // FR-27: AVAILABLE or LAUNDRY
    val status: String? = null,
    // FR-27: can be worn now (available, not in cooldown, classified). null = old server
    val available: Boolean? = null
) {
    /** FR-27: the piece is in the laundry basket. */
    val isInLaundry: Boolean
        get() = status == "LAUNDRY"

    /** True when AI classification failed and the user has not fixed the category yet (FR-01). */
    val isUnclassified: Boolean
        get() = classificationFailed || category == ClothesCategory.UNCLASSIFIED
}

// PATCH /api/clothes/{id}. Null fields are omitted by Gson and keep their current value.
data class ClothesUpdateRequest(
    val category: String?,
    val color: String?,
    val name: String?
)

// FR-27 PATCH /api/clothes/status. status is "AVAILABLE" or "LAUNDRY".
data class ClothesStatusRequest(
    val clothesIds: List<String>,
    val status: String
)

// FR-27 status change result. statusUntil is when the laundry ends (null for AVAILABLE).
data class ClothesStatusResult(
    val clothesIds: List<String>?,
    val status: String?,
    val statusUntil: String?
)

// Closet-based recommend (내 옷장 옷들로 코디 조합)
data class ClosetOutfit(
    val items: List<Clothes>,
    val reason: String,
    val styleTag: String,
    // FR-29: the combo that brings back a long-unworn piece (revivedClothesId)
    val revived: Boolean = false,
    val revivedClothesId: String? = null
)

data class ClosetRecommendResponse(
    val outfits: List<ClosetOutfit>,
    // FR-28: weather rules the server applied ("TEMP_RANGE", "RAIN"). null = old server
    val appliedRules: List<String>? = null
)

// FR-09: GET /api/clothes/summary
data class ClosetSummary(
    val comboCount: Int
)

// FR-10: GET /api/wears/summary. mostWorn is null when nothing is recorded (the closet tab does not use it).
data class WearSummary(
    val weekCount: Int,
    val monthCount: Int,
    val mostWorn: MostWorn? = null
)

// FR-20: the piece worn on the most days overall and how many days
data class MostWorn(
    val id: String,
    val name: String?,
    val count: Int
)

// FR-10: POST /api/wears. date is "YYYY-MM-DD"; null means today on the server.
data class WearRecordRequest(
    val clothesIds: List<String>,
    val date: String? = null
)

// FR-10 POST /api/wears result: new records only (a piece already recorded that day is not counted)
data class WearRecordResult(
    val date: String?,
    val recorded: Int
)

// FR-11 verdict, decided by backend rules
enum class PurchaseVerdict(val label: String) {
    BUY("사요"), THINK("고민해보세요"), SIMILAR("비슷한 게 있어요")
}

// FR-12: what the user did with a purchase check
enum class PurchaseDecision {
    BOUGHT, SKIPPED, UNDECIDED
}

// FR-12: GET /api/purchase-checks entry. imageUrl is set only for BOUGHT checks; price and plus may be null.
data class PurchaseRecord(
    val id: String,
    val name: String?,
    val category: ClothesCategory?,
    val color: String?,
    val price: Long?,
    val verdict: PurchaseVerdict?,
    val plus: Int?,
    val decision: PurchaseDecision?,
    val clothesId: String?,
    val imageUrl: String?,
    val createdAt: String?,
    // FR-20: cost per wear estimated at check time, wears of the bought piece so far and price ÷ wears now.
    // All null when unknown (not bought, no price, or before the first wear).
    val expectedCostPerWear: Long? = null,
    val wearCount: Int? = null,
    val currentCostPerWear: Long? = null
)

// FR-20: GET /api/purchase-checks/summary. Checks this month and combos added by pieces bought from them.
data class PurchaseSummary(
    val monthCount: Int,
    val monthPlus: Int
)

// FR-11: POST /api/purchase-checks response. Numbers and the verdict come from backend rules.
data class PurchaseCheckResult(
    val id: String,
    val item: PurchaseItem?,
    val verdict: PurchaseVerdict?,
    val plus: Int,
    val comboCountBefore: Int,
    val comboCountAfter: Int,
    val similar: List<SimilarClothes>?,
    val outfits: List<PurchaseOutfit>?,
    val headline: String?,
    val reasons: List<String>?,
    val gap: String?,
    val expectedWears: Int?,
    val price: Long?,
    val costPerWear: Long?
)

// The analysed piece (null attribute = unknown)
data class PurchaseItem(
    val category: ClothesCategory?,
    val color: String?,
    val name: String?,
    val fit: String?,
    val material: String?,
    val pattern: String?,
    val season: String?
)

data class SimilarClothes(
    val id: String,
    val name: String?,
    val similarity: Double,
    val wearCount: Int,
    val lastWornAt: String?,
    val note: String?
)

// Representative combo: closet clothes ids only, the new piece is implied
data class PurchaseOutfit(
    val clothesIds: List<String>,
    val label: String?
)

// FR-12: POST /api/purchase-checks/{id}/decision body. decision is "BUY" or "SKIP".
data class PurchaseDecisionRequest(
    val decision: String
)

// FR-12 decision response: the updated record, and for BUY the clothes created from the photo (null for SKIP)
data class PurchaseDecisionResult(
    val check: PurchaseRecord?,
    val clothes: Clothes?
)

// FR-13: PATCH /api/purchase-checks/{id}/attributes. Null fields are omitted by Gson and keep the analysed value.
data class PurchaseAttributesRequest(
    val category: String? = null,
    val color: String? = null,
    val name: String? = null,
    val fit: String? = null,
    val material: String? = null,
    val pattern: String? = null,
    val season: String? = null
)

// FR-07 season of a piece (server enum names)
enum class ClothesSeason(val label: String) {
    SPRING("봄"), SUMMER("여름"), FALL("가을"), WINTER("겨울");

    companion object {
        fun parse(raw: String?): ClothesSeason? = entries.find { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}
