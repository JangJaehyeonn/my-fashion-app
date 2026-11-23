package com.fashionapp.data.api

import com.google.gson.JsonParser
import retrofit2.HttpException

/** HTTP 상태 코드 (HTTP 오류가 아니면 null) */
val Throwable.httpStatus: Int?
    get() = (this as? HttpException)?.code()

/** 서버 오류 응답 본문의 message와 code(ErrorCode 이름). 없는 값은 null. */
data class ServerError(val message: String?, val code: String?)

/**
 * 서버 오류 응답 본문({"success":false,"message":"...","code":"..."})을 읽는다. HTTP 오류가 아니거나 본문이 없으면 null.
 * 오류 본문은 한 번만 읽을 수 있으므로 message와 code가 모두 필요하면 이 함수를 한 번만 부른다.
 */
fun Throwable.serverError(): ServerError? {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching {
        val json = JsonParser.parseString(body).asJsonObject
        fun field(name: String): String? = json.get(name)?.takeIf { !it.isJsonNull }?.asString
        ServerError(field("message"), field("code"))
    }.getOrNull()
}

/**
 * 서버 오류 응답 본문의 message. 본문이 없거나 HTTP 오류가 아니면 null.
 * 서버 문구(예: "아이디 또는 비밀번호가 틀렸어요")를 화면에 그대로 보여줄 때 사용.
 */
fun Throwable.serverMessage(): String? = serverError()?.message
