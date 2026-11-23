package com.fashionapp.ui.onboarding

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.datastore.OnboardingStore
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.repository.AppOpenReporter
import com.fashionapp.data.repository.AuthRepository
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.OnboardingGate
import com.fashionapp.ui.navigation.FakeAppOpenStore
import com.fashionapp.ui.navigation.FakeEventApi
import com.fashionapp.ui.navigation.MainViewModel
import com.fashionapp.ui.navigation.MutableClock
import com.fashionapp.ui.navigation.NavEvent
import com.fashionapp.ui.purchase.FakeCompressor
import com.fashionapp.ui.signup.FakeAuthApi
import com.fashionapp.ui.signup.FakeTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** FR-22: when the onboarding shows, and how its progress moves while registering. */
@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = OnboardingFakeClothesApi()
    private val store = FakeOnboardingStore()
    private val clock = MutableClock(Instant.parse("2026-11-23T01:00:00Z"))
    private val closetChanges = ClosetChanges()
    private val gate = OnboardingGate(ClothesRepository(api), store, clock)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun mainViewModel() = MainViewModel(
        AuthRepository(FakeAuthApi(), FakeTokenStore()),
        AppOpenReporter(FakeEventApi(), FakeAppOpenStore(), clock),
        gate
    )

    private fun TestScope.navEvents(vm: MainViewModel): List<NavEvent> {
        val events = mutableListOf<NavEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.navEvent.collect { events += it } }
        return events
    }

    private fun TestScope.onboarding(): OnboardingViewModel {
        val vm = OnboardingViewModel(ClothesRepository(api), closetChanges, gate, FakeCompressor())
        advanceUntilIdle()
        return vm
    }

    private fun photo() = PickedPhoto(open = { "photo".byteInputStream() }, mimeType = "image/jpeg")

    // ---- display condition

    @Test
    fun `a new account with no clothes sees the onboarding after login`() = runTest(dispatcher) {
        api.count = 0
        val vm = mainViewModel()
        val events = navEvents(vm)

        vm.checkOnboarding()
        advanceUntilIdle()

        assertEquals(listOf(NavEvent.ToOnboarding), events)
    }

    @Test
    fun `an account with 5 or more clothes does not see the onboarding`() = runTest(dispatcher) {
        api.count = 5
        val vm = mainViewModel()
        val events = navEvents(vm)

        vm.checkOnboarding()
        advanceUntilIdle()

        assertTrue(events.isEmpty())
        assertEquals(false, gate.shouldShow())
    }

    @Test
    fun `4 clothes still shows the onboarding`() = runTest(dispatcher) {
        api.count = 4
        assertEquals(true, gate.shouldShow())
    }

    @Test
    fun `the onboarding is decided once per session, not on every tab switch`() = runTest(dispatcher) {
        api.count = 0
        val vm = mainViewModel()
        val events = navEvents(vm)

        vm.checkOnboarding()
        vm.checkOnboarding()
        advanceUntilIdle()
        vm.checkOnboarding()
        advanceUntilIdle()

        assertEquals(listOf(NavEvent.ToOnboarding), events)
    }

    @Test
    fun `a failed closet load does not show it and asks again next time`() = runTest(dispatcher) {
        api.failList = true
        val vm = mainViewModel()
        val events = navEvents(vm)

        vm.checkOnboarding()
        advanceUntilIdle()
        assertTrue(events.isEmpty())

        api.failList = false
        vm.checkOnboarding()
        advanceUntilIdle()
        assertEquals(listOf(NavEvent.ToOnboarding), events)
    }

    @Test
    fun `later goes home and does not show again the same day`() = runTest(dispatcher) {
        api.count = 2
        onboarding().later()
        assertEquals("2026-11-23", store.dismissedDate)

        // Even after logging out and in again on the same day
        val vm = mainViewModel()
        val events = navEvents(vm)
        vm.checkOnboarding()
        advanceUntilIdle()
        assertTrue(events.isEmpty())
        assertEquals(false, gate.shouldShow())
    }

    @Test
    fun `later shows again the next day while the closet is still small`() = runTest(dispatcher) {
        api.count = 2
        store.dismissedDate = "2026-11-23"
        assertEquals(false, gate.shouldShow())

        clock.now = Instant.parse("2026-11-24T01:00:00Z")
        assertEquals(true, gate.shouldShow())
    }

    // ---- progress

    @Test
    fun `progress starts from the current closet size`() = runTest(dispatcher) {
        api.count = 3
        val state = onboarding().state.value

        assertEquals(3, state.closetCount)
        assertEquals("3/5", state.progressText)
        assertFalse(state.isComplete)
        assertEquals(OnboardingState.TITLE_START, state.title)
        assertEquals("5벌만 등록하면 살까 말까를 쓸 수 있어요", state.title)
        assertTrue(state.canPick)
    }

    @Test
    fun `each registration moves the progress and 5 pieces shows the completion`() = runTest(dispatcher) {
        api.count = 3
        api.holdRegistrations = true
        val vm = onboarding()

        vm.registerAll(listOf(photo(), photo()))
        advanceUntilIdle()
        assertEquals("3/5", vm.state.value.progressText)
        assertEquals(OnboardingUpload(1, 2), vm.state.value.upload)
        assertEquals("1/2벌째 등록하는 중…", vm.state.value.uploadText)
        assertFalse(vm.state.value.canPick)

        api.release.send(Unit)
        advanceUntilIdle()
        assertEquals("4/5", vm.state.value.progressText)
        assertEquals(OnboardingUpload(2, 2), vm.state.value.upload)
        assertFalse(vm.state.value.isComplete)

        api.release.send(Unit)
        advanceUntilIdle()
        val done = vm.state.value
        assertEquals("5/5", done.progressText)
        assertTrue(done.isComplete)
        assertEquals("이제 살까 말까를 써볼 수 있어요", done.title)
        assertNull(done.upload)
        assertNull(done.message)
        assertFalse(done.canPick)
        assertEquals(2, api.registered)
        // The closet tab reloads with the new pieces
        assertEquals(1, closetChanges.version.value)
    }

    @Test
    fun `progress never goes above the goal`() = runTest(dispatcher) {
        api.count = 4
        val vm = onboarding()

        vm.registerAll(listOf(photo(), photo(), photo()))
        advanceUntilIdle()

        assertEquals(7, vm.state.value.closetCount)
        assertEquals("5/5", vm.state.value.progressText)
        assertTrue(vm.state.value.isComplete)
    }

    @Test
    fun `a failed registration does not move the progress`() = runTest(dispatcher) {
        api.count = 1
        api.failRegister = true
        val vm = onboarding()

        vm.registerAll(listOf(photo()))
        advanceUntilIdle()

        assertEquals("1/5", vm.state.value.progressText)
        assertEquals(OnboardingViewModel.MSG_REGISTER_FAILED, vm.state.value.message)
        assertEquals(0, closetChanges.version.value)
        assertTrue(vm.state.value.canPick)
    }
}

/** A MainViewModel gate for tests that do not care about the onboarding (closet already big enough). */
internal fun fakeOnboardingGate(): OnboardingGate =
    OnboardingGate(ClothesRepository(OnboardingFakeClothesApi().apply { count = 5 }), FakeOnboardingStore(), java.time.Clock.systemUTC())

internal class FakeOnboardingStore : OnboardingStore {
    override var dismissedDate: String? = null
}

internal class OnboardingFakeClothesApi : ClothesApi {
    var count = 0
    var failList = false
    var failRegister = false
    var holdRegistrations = false
    var registered = 0
    val release = Channel<Unit>(Channel.UNLIMITED)

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> {
        if (holdRegistrations) release.receive()
        if (failRegister) throw RuntimeException("network")
        registered++
        return ApiResponse(true, piece("new$registered"), null)
    }

    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> {
        if (failList) throw RuntimeException("network")
        return ApiResponse(true, (1..count).map { piece("c$it") }, null)
    }

    override suspend fun getSummary(): ApiResponse<ClosetSummary> = error("unused")
    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> = error("unused")
    override suspend fun reclassify(id: String): ApiResponse<Clothes> = error("unused")
    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")

    private fun piece(id: String) = Clothes(
        id = id, imageUrl = "/api/images/$id.jpg", category = ClothesCategory.TOP, color = "블랙", name = id, createdAt = null
    )
}
