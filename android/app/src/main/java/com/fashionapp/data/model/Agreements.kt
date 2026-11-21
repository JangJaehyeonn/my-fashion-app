package com.fashionapp.data.model

/**
 * Required agreements shown on the ID signup screen and the social first-login screen (FR-06).
 * "전체 동의" is derived from the three individual checks, so the two can never disagree.
 */
data class Agreements(
    val terms: Boolean = false,
    val privacy: Boolean = false,
    val ageOver14: Boolean = false
) {
    val all: Boolean
        get() = terms && privacy && ageOver14

    /** Checking "전체 동의" checks every item; unchecking it clears every item. */
    fun withAll(checked: Boolean): Agreements = Agreements(checked, checked, checked)

    fun with(item: AgreementItem, checked: Boolean): Agreements = when (item) {
        AgreementItem.TERMS -> copy(terms = checked)
        AgreementItem.PRIVACY -> copy(privacy = checked)
        AgreementItem.AGE_OVER_14 -> copy(ageOver14 = checked)
    }

    fun isChecked(item: AgreementItem): Boolean = when (item) {
        AgreementItem.TERMS -> terms
        AgreementItem.PRIVACY -> privacy
        AgreementItem.AGE_OVER_14 -> ageOver14
    }
}

/** Display order on screen. [link] is the legal page opened in the browser ("보기"). */
enum class AgreementItem(val label: String, val link: LegalPage?) {
    TERMS("이용약관 동의 (필수)", LegalPage.TERMS),
    PRIVACY("개인정보처리방침 동의 (필수)", LegalPage.PRIVACY),
    AGE_OVER_14("만 14세 이상입니다 (필수)", null)
}

/** Public pages served by the backend without login (LegalPagesController). */
enum class LegalPage(val path: String) {
    TERMS("/terms"),
    PRIVACY("/privacy");

    fun url(serverBaseUrl: String): String = serverBaseUrl.trimEnd('/') + path
}

const val MSG_AGREE_REQUIRED = "필수 항목에 모두 동의해 주세요."
