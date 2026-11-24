package com.fashionapp.ui.mypage

import com.fashionapp.data.api.AuthApi
import com.fashionapp.data.model.AgeConfirmationRequest
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ChangePasswordRequest
import com.fashionapp.data.model.CheckIdResponse
import com.fashionapp.data.model.ExchangeRequest
import com.fashionapp.data.model.LoginRequest
import com.fashionapp.data.model.RefreshRequest
import com.fashionapp.data.model.SignupRequest
import com.fashionapp.data.model.TokenResponse
import com.fashionapp.data.model.TravelMode
import com.fashionapp.data.model.UpdateProfileRequest
import com.fashionapp.data.model.UserProfile
import com.fashionapp.data.repository.AuthRepository
import com.fashionapp.ui.signup.FakeTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import java.time.LocalTime

@OptIn(ExperimentalCoroutinesApi::class)
class CommuteSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeProfileApi()
    private val vm by lazy { CommuteSettingsViewModel(AuthRepository(api, FakeTokenStore())) }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `the form starts from the saved commute profile`() = runTest(dispatcher) {
        api.profile = api.profile.copy(commuteOutAt = "07:30", commuteBackAt = "20:00", travelMode = "TRANSIT")

        vm.form
        advanceUntilIdle()

        assertEquals(CommuteForm(LocalTime.of(7, 30), LocalTime.of(20, 0), TravelMode.TRANSIT), vm.form.value)
        assertEquals("07:30", CommuteSettingsViewModel.outLabel(vm.form.value))
        assertFalse(vm.isLoading.value)
    }

    @Test
    fun `unset times show the default moments of the timeline`() = runTest(dispatcher) {
        vm.form
        advanceUntilIdle()

        assertEquals(CommuteForm(), vm.form.value)
        assertEquals("기본 8시", CommuteSettingsViewModel.outLabel(vm.form.value))
        assertEquals("기본 19시", CommuteSettingsViewModel.backLabel(vm.form.value))
    }

    @Test
    fun `saving sends the commute fields and keeps the body profile unchanged`() = runTest(dispatcher) {
        vm.form
        advanceUntilIdle()
        vm.setOutAt(LocalTime.of(7, 0))
        vm.setBackAt(LocalTime.of(21, 30))
        vm.selectTravelMode(TravelMode.WALK)
        var saved = false

        vm.save { saved = true }
        advanceUntilIdle()

        val sent = api.updates.single()
        assertEquals("07:00", sent.commuteOutAt)
        assertEquals("21:30", sent.commuteBackAt)
        assertEquals("WALK", sent.travelMode)
        // The server overwrites body fields with what it gets: the current values go back as they are
        assertEquals("tester", sent.nickname)
        assertEquals(170, sent.height)
        assertEquals(60, sent.weight)
        assertEquals("SLIM", sent.bodyType)
        assertEquals(listOf("CASUAL", "MINIMAL"), sent.preferredStyles)
        assertTrue(saved)
    }

    @Test
    fun `clearing times and unselecting the mode sends empty strings so the server clears them`() = runTest(dispatcher) {
        api.profile = api.profile.copy(commuteOutAt = "07:30", commuteBackAt = "20:00", travelMode = "CAR")
        vm.form
        advanceUntilIdle()

        vm.clearTimes()
        vm.selectTravelMode(TravelMode.CAR)
        vm.save {}
        advanceUntilIdle()

        val sent = api.updates.single()
        assertEquals("", sent.commuteOutAt)
        assertEquals("", sent.commuteBackAt)
        assertEquals("", sent.travelMode)
    }

    @Test
    fun `a failed save shows a message and does not leave the screen`() = runTest(dispatcher) {
        api.failUpdate = true
        vm.form
        advanceUntilIdle()
        var saved = false

        vm.save { saved = true }
        advanceUntilIdle()

        assertFalse(saved)
        assertEquals(CommuteSettingsViewModel.MSG_SAVE_FAILED, vm.message.value)
        vm.clearMessage()
        assertNull(vm.message.value)
    }
}

private class FakeProfileApi : AuthApi {
    var profile = UserProfile(
        id = "u1", email = null, nickname = "tester", profileImageUrl = null, provider = "google",
        height = 170, weight = 60, bodyType = "SLIM", preferredStyle = "CASUAL",
        preferredStyles = listOf("CASUAL", "MINIMAL"), ageConfirmedAt = "2026-11-21T00:00:00"
    )
    var failUpdate = false
    val updates = mutableListOf<UpdateProfileRequest>()

    override suspend fun getMyProfile(): ApiResponse<UserProfile> = ApiResponse(true, profile, null)

    override suspend fun updateProfile(request: UpdateProfileRequest): ApiResponse<UserProfile> {
        updates += request
        if (failUpdate) throw RuntimeException("network")
        profile = profile.copy(
            commuteOutAt = request.commuteOutAt?.ifEmpty { null },
            commuteBackAt = request.commuteBackAt?.ifEmpty { null },
            travelMode = request.travelMode?.ifEmpty { null }
        )
        return ApiResponse(true, profile, null)
    }

    override suspend fun exchange(request: ExchangeRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun login(request: LoginRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun signup(request: SignupRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun checkLoginId(loginId: String): ApiResponse<CheckIdResponse> = error("unused")
    override suspend fun refreshToken(request: RefreshRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun logout(request: RefreshRequest): ApiResponse<Unit> = error("unused")
    override suspend fun changePassword(request: ChangePasswordRequest): ApiResponse<TokenResponse> = error("unused")
    override suspend fun confirmAge(request: AgeConfirmationRequest): ApiResponse<UserProfile> = error("unused")
    override suspend fun grantVtonConsent(): ApiResponse<UserProfile> = error("unused")
    override suspend fun revokeVtonConsent(): ApiResponse<UserProfile> = error("unused")
    override suspend fun deleteAccount(): ApiResponse<Unit> = error("unused")
}
