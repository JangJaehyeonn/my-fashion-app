package com.fashionapp.ui.purchase

import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.PurchaseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseResultFormatterTest {

    @Test
    fun `cost per wear line is shown when the server sends it`() {
        val result = sampleResult().copy(price = 59_000, costPerWear = 2_950, expectedWears = 20)
        val ui = PurchaseResultFormatter.format(result, emptyMap())
        assertEquals("한 번 입을 때 약 2,950원 · 1년에 예상 20회", ui.costPerWearText)
        assertTrue(ui.hasCostPerWear)
        assertEquals("59,000원 · 직접 입력", ui.priceText)
    }

    @Test
    fun `null cost per wear shows the waiting message`() {
        val ui = PurchaseResultFormatter.format(sampleResult(), emptyMap())
        assertEquals(PurchaseResultFormatter.MSG_COST_PENDING, ui.costPerWearText)
        assertFalse(ui.hasCostPerWear)
        assertNull(ui.priceText)
    }

    @Test
    fun `unknown attributes are left out of the chips`() {
        val result = sampleResult().copy(
            item = PurchaseItem(ClothesCategory.OUTER, null, "트렌치", "레귤러", null, null, "FALL")
        )
        assertEquals(listOf("아우터", "레귤러", "가을"), PurchaseResultFormatter.attributeChips(result))
    }

    @Test
    fun `gap box only when the server sends a gap`() {
        assertNull(PurchaseResultFormatter.format(sampleResult(), emptyMap()).gap)
        val ui = PurchaseResultFormatter.format(sampleResult().copy(gap = "밝은 하의가 없어요"), emptyMap())
        assertEquals("밝은 하의가 없어요", ui.gap)
    }

    @Test
    fun `combo line depends on whether combos grow`() {
        assertEquals(PurchaseResultFormatter.MSG_COMBO_UP, PurchaseResultFormatter.format(sampleResult(plus = 3), emptyMap()).comboLine)
        assertEquals(PurchaseResultFormatter.MSG_COMBO_SAME, PurchaseResultFormatter.format(sampleResult(plus = 0), emptyMap()).comboLine)
    }
}
