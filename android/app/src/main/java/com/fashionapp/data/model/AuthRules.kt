package com.fashionapp.data.model

/**
 * 아이디·비밀번호·닉네임 규칙 — 서버 LocalAuthRules와 같게 유지할 것.
 * 화면은 이 규칙으로 입력 칸 아래 안내를 먼저 띄우고, 최종 판단은 서버가 한다.
 */
object AuthRules {
    private val LOGIN_ID = Regex("^[a-z0-9]{4,20}$")
    private const val PASSWORD_MIN = 8
    private const val PASSWORD_MAX = 64
    private const val NICKNAME_MAX = 20

    const val LOGIN_ID_RULE = "영문 소문자와 숫자로 4~20자"
    const val PASSWORD_RULE = "영문과 숫자를 포함해 8~64자"

    fun loginIdError(value: String): String? =
        if (LOGIN_ID.matches(value)) null else "아이디는 ${LOGIN_ID_RULE}여야 해요."

    fun passwordError(value: String): String? = when {
        value.length < PASSWORD_MIN || value.length > PASSWORD_MAX -> "비밀번호는 ${PASSWORD_RULE}여야 해요."
        value.none { it in 'a'..'z' || it in 'A'..'Z' } -> "영문을 1자 이상 넣어 주세요."
        value.none { it.isDigit() } -> "숫자를 1자 이상 넣어 주세요."
        else -> null
    }

    fun nicknameError(value: String): String? {
        val trimmed = value.trim()
        return if (trimmed.isEmpty() || trimmed.length > NICKNAME_MAX) "닉네임은 1~20자로 입력해 주세요." else null
    }
}
