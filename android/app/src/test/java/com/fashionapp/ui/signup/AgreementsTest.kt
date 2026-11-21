package com.fashionapp.ui.signup

import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.data.model.LegalPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgreementsTest {

    @Test
    fun `all is true only when every required item is checked`() {
        assertFalse(Agreements().all)
        assertFalse(Agreements(terms = true, privacy = true).all)
        assertFalse(Agreements(terms = true, ageOver14 = true).all)
        assertFalse(Agreements(privacy = true, ageOver14 = true).all)
        assertTrue(Agreements(terms = true, privacy = true, ageOver14 = true).all)
    }

    @Test
    fun `checking all checks every item and unchecking all clears every item`() {
        val checked = Agreements(terms = true).withAll(true)
        assertEquals(Agreements(terms = true, privacy = true, ageOver14 = true), checked)

        assertEquals(Agreements(), checked.withAll(false))
    }

    @Test
    fun `unchecking one item turns all off and checking the last item turns it on`() {
        val all = Agreements().withAll(true)
        val oneOff = all.with(AgreementItem.PRIVACY, false)
        assertFalse(oneOff.all)
        assertFalse(oneOff.isChecked(AgreementItem.PRIVACY))
        assertTrue(oneOff.isChecked(AgreementItem.TERMS))
        assertTrue(oneOff.isChecked(AgreementItem.AGE_OVER_14))

        assertTrue(oneOff.with(AgreementItem.PRIVACY, true).all)
    }

    @Test
    fun `terms and privacy items link to the server pages and age has no link`() {
        assertEquals(LegalPage.TERMS, AgreementItem.TERMS.link)
        assertEquals(LegalPage.PRIVACY, AgreementItem.PRIVACY.link)
        assertNull(AgreementItem.AGE_OVER_14.link)
    }

    @Test
    fun `legal page urls are built from the server base url`() {
        assertEquals("http://localhost:8080/terms", LegalPage.TERMS.url("http://localhost:8080"))
        assertEquals("https://api.example.invalid/privacy", LegalPage.PRIVACY.url("https://api.example.invalid/"))
    }
}
