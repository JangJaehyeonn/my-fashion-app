package com.fashionapp.ui.signup

import com.fashionapp.data.model.AgreementItem
import com.fashionapp.data.model.Agreements
import com.fashionapp.data.model.MSG_AGREE_REQUIRED
import com.fashionapp.data.model.SignupRequest
import com.fashionapp.data.repository.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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

@OptIn(ExperimentalCoroutinesApi::class)
class SignupViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeAuthApi()
    private val tokens = FakeTokenStore()
    private val vm by lazy { SignupViewModel(AuthRepository(api, tokens)) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.fillValidFields() {
        vm.onLoginIdChange("wearon01")
        vm.checkLoginId()
        advanceUntilIdle()
        vm.onPasswordChange("abcd1234")
        vm.onPasswordConfirmChange("abcd1234")
        vm.onNicknameChange(" 웨어온 ")
    }

    @Test
    fun `signup button is enabled only after every required agreement is checked`() {
        assertFalse(vm.form.value.canSubmit)

        vm.onAgreementChange(AgreementItem.TERMS, true)
        assertFalse(vm.form.value.canSubmit)
        vm.onAgreementChange(AgreementItem.PRIVACY, true)
        assertFalse(vm.form.value.canSubmit)
        vm.onAgreementChange(AgreementItem.AGE_OVER_14, true)
        assertTrue(vm.form.value.canSubmit)

        vm.onAgreementChange(AgreementItem.TERMS, false)
        assertFalse(vm.form.value.canSubmit)
    }

    @Test
    fun `all agreement checks and clears every item consistently`() {
        vm.onAllAgreementsChange(true)
        assertEquals(Agreements(terms = true, privacy = true, ageOver14 = true), vm.form.value.agreements)
        assertTrue(vm.form.value.agreements.all)
        assertTrue(vm.form.value.canSubmit)

        // Unchecking one item also unchecks "전체 동의"
        vm.onAgreementChange(AgreementItem.AGE_OVER_14, false)
        assertFalse(vm.form.value.agreements.all)
        assertTrue(vm.form.value.agreements.terms)
        assertTrue(vm.form.value.agreements.privacy)

        // Checking every item one by one turns "전체 동의" back on
        vm.onAgreementChange(AgreementItem.AGE_OVER_14, true)
        assertTrue(vm.form.value.agreements.all)

        vm.onAllAgreementsChange(false)
        assertEquals(Agreements(), vm.form.value.agreements)
        assertFalse(vm.form.value.canSubmit)
    }

    @Test
    fun `submit sends all three agreements in the signup body`() = runTest(dispatcher) {
        fillValidFields()
        vm.onAllAgreementsChange(true)
        var signedUp = false

        vm.submit { signedUp = true }
        assertTrue(vm.form.value.isSubmitting)
        assertFalse(vm.form.value.canSubmit)
        advanceUntilIdle()

        assertTrue(signedUp)
        assertEquals(
            listOf(SignupRequest("wearon01", "abcd1234", "웨어온", ageOver14 = true, termsAgreed = true, privacyAgreed = true)),
            api.signupRequests
        )
        assertEquals("access", tokens.accessToken.value)
        assertFalse(vm.form.value.isSubmitting)
    }

    @Test
    fun `submit without every agreement does not call the server and shows a guide`() = runTest(dispatcher) {
        fillValidFields()
        vm.onAgreementChange(AgreementItem.TERMS, true)
        vm.onAgreementChange(AgreementItem.AGE_OVER_14, true)

        vm.submit { error("must not sign up") }
        advanceUntilIdle()

        assertTrue(api.signupRequests.isEmpty())
        assertEquals(MSG_AGREE_REQUIRED, vm.form.value.agreementError)

        vm.onAgreementChange(AgreementItem.PRIVACY, true)
        assertNull(vm.form.value.agreementError)
    }

    @Test
    fun `server rejection for missing agreement shows the server message`() = runTest(dispatcher) {
        fillValidFields()
        vm.onAllAgreementsChange(true)
        api.signupError = httpError(400, "이용약관과 개인정보처리방침에 동의해 주세요.")

        vm.submit { error("must not sign up") }
        advanceUntilIdle()

        assertEquals("이용약관과 개인정보처리방침에 동의해 주세요.", vm.form.value.generalError)
        assertFalse(vm.form.value.isSubmitting)
        assertTrue(vm.form.value.canSubmit)
    }
}
