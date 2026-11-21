package com.fashionapp.data.api

import com.google.gson.JsonParser
import retrofit2.HttpException

/** HTTP 상태 코드 (HTTP 오류가 아니면 null) */
val Throwable.httpStatus: Int?
    get() = (this as? HttpException)?.code()

/**
 * 서버 오류 응답 본문({"success":false,"message":"..."})의 message. 본문이 없거나 HTTP 오류가 아니면 null.
 * 서버 문구(예: "아이디 또는 비밀번호가 틀렸어요")를 화면에 그대로 보여줄 때 사용.
 */
fun Throwable.serverMessage(): String? {
    val body = (this as? HttpException)?.response()?.errorBody()?.string() ?: return null
    return runCatching {
        JsonParser.parseString(body).asJsonObject.get("message")?.takeIf { !it.isJsonNull }?.asString
    }.getOrNull()
}
