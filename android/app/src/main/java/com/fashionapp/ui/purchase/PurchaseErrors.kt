package com.fashionapp.ui.purchase

import com.fashionapp.data.api.serverError
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/** User-facing messages for purchase check failures (FR-17 compare, FR-18 decide and re-judge). */
object PurchaseErrors {

    const val CODE_CLOSET_TOO_SMALL = "CLOSET_TOO_SMALL"

    const val MSG_EXPIRED = "시간이 지나서 다시 찍어야 해요"
    const val MSG_CLOSET_TOO_SMALL = "옷장에 5벌 이상 있어야 비교할 수 있어요"
    const val MSG_LIMIT = "오늘은 살까 말까를 더 쓸 수 없어요. 내일 다시 시도해 주세요."
    const val MSG_UNAVAILABLE = "지금은 옷을 분석할 수 없어요. 잠시 후 다시 시도해 주세요."
    const val MSG_TIMEOUT = "비교가 오래 걸리고 있어요. 잠시 후 다시 시도해 주세요."
    const val MSG_NETWORK = "인터넷 연결을 확인하고 다시 시도해 주세요."
    const val MSG_ALREADY_DECIDED = "이미 결정한 판정이에요."

    /** True when the server answered 410: the photo is gone and the check must be taken again. */
    fun isExpired(e: Throwable): Boolean = (e as? HttpException)?.code() == HTTP_GONE

    /** Maps a failure to a message; [fallback] covers anything without its own message. */
    fun messageOf(e: Throwable, fallback: String): String {
        if (e !is HttpException) {
            return when (e) {
                is InterruptedIOException -> MSG_TIMEOUT
                is IOException -> MSG_NETWORK
                else -> fallback
            }
        }
        val body = e.serverError()
        if (body?.code == CODE_CLOSET_TOO_SMALL) return MSG_CLOSET_TOO_SMALL
        return when (e.code()) {
            HTTP_GONE -> MSG_EXPIRED
            HTTP_CONFLICT -> MSG_ALREADY_DECIDED
            HTTP_TOO_MANY -> MSG_LIMIT
            HTTP_UNAVAILABLE -> MSG_UNAVAILABLE
            HTTP_GATEWAY_TIMEOUT -> MSG_TIMEOUT
            else -> body?.message?.takeIf { it.isNotBlank() } ?: fallback
        }
    }

    private const val HTTP_CONFLICT = 409
    private const val HTTP_GONE = 410
    private const val HTTP_TOO_MANY = 429
    private const val HTTP_UNAVAILABLE = 503
    private const val HTTP_GATEWAY_TIMEOUT = 504
}
