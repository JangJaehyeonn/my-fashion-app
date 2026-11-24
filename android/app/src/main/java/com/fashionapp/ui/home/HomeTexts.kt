package com.fashionapp.ui.home

import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.DayWeather
import com.fashionapp.data.model.DayWeatherPoint
import kotlin.math.roundToInt

/**
 * FR-30 commute timeline under the weather card: "8시 3° → 13시 12° → 19시 7°" and "일교차 9°".
 * [rangeEmphasized] makes "일교차" bold (ui-spec: 8° or more). It is display only; the rule itself runs on the server.
 */
data class CommuteTimeline(
    val pointsText: String,
    val rangeText: String,
    val rangeEmphasized: Boolean
)

/** Texts of the FR-30 home tab. Rules are decided by the server; these only turn its values into Korean lines. */
object HomeTexts {

    // ui-spec 홈 탭 2: "일교차 8° 이상이면 굵게"
    const val RANGE_EMPHASIS_DEGREES = 8

    // FR-28 appliedRules values sent by the server
    const val RULE_TEMP_RANGE = "TEMP_RANGE"
    const val RULE_RAIN = "RAIN"

    const val NOTE_TEMP_RANGE = "외투를 벗어도 괜찮은 코디로 골랐어요"
    const val NOTE_RAIN = "비 소식이 있어 스웨이드·흰 신발은 뺐어요"

    // ui-spec 홈 탭 4: badge on the long-unworn piece of a revived combo (FR-29)
    const val REVIVED_BADGE = "오랜만이에요"

    fun timeline(day: DayWeather): CommuteTimeline {
        val range = day.tempRange.roundToInt()
        return CommuteTimeline(
            pointsText = listOf(day.out, day.day, day.back).joinToString(" → ") { point(it) },
            rangeText = "일교차 ${range}°",
            rangeEmphasized = range >= RANGE_EMPHASIS_DEGREES
        )
    }

    /** "08:00" -> "8시", "07:30" -> "7시 30분". Unreadable values are shown as they are. */
    fun timeLabel(time: String): String {
        val parts = time.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return if (minute == 0) "${hour}시" else "${hour}시 ${minute}분"
    }

    /** One line per weather rule the server applied to the recommendation, in a fixed order. */
    fun ruleNotes(appliedRules: List<String>?): List<String> {
        val rules = appliedRules.orEmpty()
        return listOfNotNull(
            NOTE_TEMP_RANGE.takeIf { RULE_TEMP_RANGE in rules },
            NOTE_RAIN.takeIf { RULE_RAIN in rules }
        )
    }

    /** "입을 수 있는 옷 N벌 중에서 · 세탁 중 N벌 제외" (the laundry part only when something is in the laundry). */
    fun availabilityText(closet: List<Clothes>): String {
        val available = closet.count(::isAvailable)
        val laundry = closet.count { it.isInLaundry }
        val base = "입을 수 있는 옷 ${available}벌 중에서"
        return if (laundry > 0) "$base · 세탁 중 ${laundry}벌 제외" else base
    }

    // Old servers do not send `available`: then everything classified and not in the laundry counts
    private fun isAvailable(clothes: Clothes): Boolean =
        clothes.available ?: (!clothes.isUnclassified && !clothes.isInLaundry)

    private fun point(point: DayWeatherPoint): String =
        "${timeLabel(point.time)} ${point.temperature.roundToInt()}°"
}
