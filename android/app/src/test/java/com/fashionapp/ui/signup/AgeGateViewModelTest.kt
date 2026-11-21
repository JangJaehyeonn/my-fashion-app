package com.fashionapp.ui.signup

import com.fashionapp.data.model.AgeConfirmationRequest
import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.data.model.MSG_AGREE_REQUIRED
import com.fashionapp.data.repository.AuthRepository
import com.fashionapp.ui.navigation.MainViewModel
import com.fashionapp.ui.navigation.NavEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Social first-login confirmation (AgeGateScreen) state held by MainViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
class AgeGateViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeAuthApi()
    private val vm by lazy { MainViewModel(AuthRepository(api, FakeTokenStore())) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `all agreement stays consistent with the individual checks`() {
        vm.onAgeGateAllChange(true)
        assertEquals(Agreements(terms = true, privacy = true, ageOver14 = true), vm.ageGateAgreements.value)

        vm.onAgeGateAgreementChange(AgreementItem.TERMS, false)
        assertFalse(vm.ageGateAgreements.value.all)

        vm.onAgeGateAgreementChange(AgreementItem.TERMS, true)
        assertTrue(vm.ageGateAgreements.value.all)

        vm.onAgeGateAllChange(false)
        assertEquals(Agreements(), vm.ageGateAgreements.value)
    }

    @Test
    fun `confirm without every agreement does not call the server`() = runTest(dispatcher) {
        vm.onAgeGateAgreementChange(AgreementItem.AGE_OVER_14, true)

        vm.confirmAge()
        advanceUntilIdle()

        assertTrue(api.ageConfirmations.isEmpty())
        assertEquals(MSG_AGREE_REQUIRED, vm.ageGateError.value)

        vm.onAgeGateAgreementChange(AgreementItem.TERMS, true)
        assertNull(vm.ageGateError.value)
    }

    @Test
    fun `confirm sends all three agreements and moves to main`() = runTest(dispatcher) {
        vm.onAgeGateAllChange(true)

        vm.confirmAge()
        assertTrue(vm.ageGateWorking.value)
        advanceUntilIdle()

        assertEquals(listOf(AgeConfirmationRequest(ageOver14 = true, termsAgreed = true, privacyAgreed = true)), api.ageConfirmations)
        assertEquals(NavEvent.ToMain, vm.navEvent.first())
        assertFalse(vm.ageGateWorking.value)
        assertNull(vm.ageGateError.value)
    }

    @Test
    fun `confirm failure keeps the checks and shows a retry message`() = runTest(dispatcher) {
        vm.onAgeGateAllChange(true)
        api.confirmAgeError = httpError(500, "error")

        vm.confirmAge()
        advanceUntilIdle()

        assertEquals("저장하지 못했어요. 다시 시도해 주세요.", vm.ageGateError.value)
        assertTrue(vm.ageGateAgreements.value.all)
        assertFalse(vm.ageGateWorking.value)
    }

    @Test
    fun `reset clears the checks for the next account`() {
        vm.onAgeGateAllChange(true)

        vm.resetAgeCheck()

        assertEquals(Agreements(), vm.ageGateAgreements.value)
    }
}
