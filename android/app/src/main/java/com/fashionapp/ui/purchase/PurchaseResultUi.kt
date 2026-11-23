package com.fashionapp.ui.purchase

import com.fashionapp.data.model.ClothesSeason
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseVerdict
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToInt

/** Card background of a verdict (ui-spec 결과 4). Mapped to theme colors by the screen. */
enum class VerdictCardTone { BEIGE, INK, WHITE }

/** Badge background of a verdict: brown + white text, white + black text, light beige + black text. */
enum class VerdictBadgeTone { BROWN, WHITE, LIGHT_BEIGE }

data class VerdictStyle(val card: VerdictCardTone, val badge: VerdictBadgeTone, val label: String)

/** One "비슷한 내 옷" row. photoUrl comes from the closet list (null while it is not loaded). */
data class SimilarRow(
    val id: String,
    val name: String,
    val note: String?,
    val photoUrl: String?,
    val wearText: String,
    val similarityText: String
)

/** One representative combo card. The new piece is always part of it and shown first with NEW. */
data class OutfitCard(val label: String, val photoUrls: List<String?>)

/** Everything the result screen shows, derived from the server result only (numbers are never computed here). */
data class PurchaseResultUi(
    val title: String,
    val priceText: String?,
    val attributeChips: List<String>,
    val plusText: String,
    val comboChangeText: String,
    val comboLine: String,
    val costPerWearText: String,
    val hasCostPerWear: Boolean,
    val verdict: VerdictStyle?,
    val headline: String?,
    val reasons: List<String>,
    val similar: List<SimilarRow>,
    val outfits: List<OutfitCard>,
    val outfitHeaderText: String,
    val gap: String?
)

object PurchaseResultFormatter {

    const val DEFAULT_TITLE = "옷"
    const val DEFAULT_OUTFIT_LABEL = "데일리"
    const val MSG_COST_PENDING = "착용 기록이 쌓이면 1회당 가격을 알려드려요"
    const val MSG_COMBO_UP = "내 옷장 옷과 새로 맞춰 입을 수 있어요"
    const val MSG_COMBO_SAME = "지금 옷장으로는 새 코디가 생기지 않아요"

    fun verdictStyle(verdict: PurchaseVerdict): VerdictStyle = when (verdict) {
        PurchaseVerdict.SIMILAR -> VerdictStyle(VerdictCardTone.BEIGE, VerdictBadgeTone.BROWN, verdict.label)
        PurchaseVerdict.BUY -> VerdictStyle(VerdictCardTone.INK, VerdictBadgeTone.WHITE, verdict.label)
        PurchaseVerdict.THINK -> VerdictStyle(VerdictCardTone.WHITE, VerdictBadgeTone.LIGHT_BEIGE, verdict.label)
    }

    /** [closetPhotos] maps closet clothes id to its photo url. */
    fun format(result: PurchaseCheckResult, closetPhotos: Map<String, String>): PurchaseResultUi {
        val outfits = result.outfits.orEmpty()
        return PurchaseResultUi(
            title = result.item?.name?.takeIf { it.isNotBlank() } ?: result.item?.category?.label ?: DEFAULT_TITLE,
            priceText = result.price?.let { "${won(it)} · 직접 입력" },
            attributeChips = attributeChips(result),
            plusText = "+${result.plus}",
            comboChangeText = "지금 ${result.comboCountBefore}개 → ${result.comboCountAfter}개",
            comboLine = if (result.plus > 0) MSG_COMBO_UP else MSG_COMBO_SAME,
            costPerWearText = costPerWearText(result),
            hasCostPerWear = result.costPerWear != null,
            verdict = result.verdict?.let(::verdictStyle),
            headline = result.headline?.takeIf { it.isNotBlank() },
            reasons = result.reasons.orEmpty().filter { it.isNotBlank() }.take(MAX_REASONS),
            similar = result.similar.orEmpty().map { similar ->
                SimilarRow(
                    id = similar.id,
                    name = similar.name?.takeIf { it.isNotBlank() } ?: DEFAULT_TITLE,
                    note = similar.note?.takeIf { it.isNotBlank() },
                    photoUrl = closetPhotos[similar.id],
                    wearText = "착용 ${similar.wearCount}회",
                    similarityText = "${(similar.similarity * 100).roundToInt()}%"
                )
            },
            outfits = outfits.map { outfit ->
                OutfitCard(
                    label = outfit.label?.takeIf { it.isNotBlank() } ?: DEFAULT_OUTFIT_LABEL,
                    photoUrls = outfit.clothesIds.map { closetPhotos[it] }
                )
            },
            outfitHeaderText = "${result.plus}개 중 대표",
            gap = result.gap?.takeIf { it.isNotBlank() }
        )
    }

    /** Category, color, fit, material, pattern, season; unknown (null) attributes are left out. */
    fun attributeChips(result: PurchaseCheckResult): List<String> {
        val item = result.item ?: return emptyList()
        return listOfNotNull(
            item.category?.label,
            item.color,
            item.fit,
            item.material,
            item.pattern,
            item.season?.let { ClothesSeason.parse(it)?.label ?: it }
        ).filter { it.isNotBlank() }
    }

    fun costPerWearText(result: PurchaseCheckResult): String {
        val cost = result.costPerWear ?: return MSG_COST_PENDING
        val wears = result.expectedWears ?: return "한 번 입을 때 약 ${won(cost)}"
        return "한 번 입을 때 약 ${won(cost)} · 1년에 예상 ${wears}회"
    }

    fun won(amount: Long): String = NumberFormat.getNumberInstance(Locale.KOREA).format(amount) + "원"

    private const val MAX_REASONS = 3
}
