package com.fashionapp.ui.navigation

import com.fashionapp.data.api.EventApi
import com.fashionapp.data.datastore.AppOpenStore
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.repository.AppOpenReporter
import com.fashionapp.data.repository.AuthRepository
import com.fashionapp.ui.signup.FakeAuthApi
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** FR-20: MainViewModel sends APP_OPENED (FR-14) at most once a day. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppOpenViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val eventApi = FakeEventApi()
    private val store = FakeAppOpenStore()
    private val clock = MutableClock(Instant.parse("2026-11-23T01:00:00Z"))
    private val vm by lazy {
        MainViewModel(AuthRepository(FakeAuthApi(), FakeTokenStore()), AppOpenReporter(eventApi, store, clock))
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening the main screen many times on one day sends APP_OPENED once`() = runTest(dispatcher) {
        vm.reportAppOpened()
        vm.reportAppOpened()
        advanceUntilIdle()
        vm.reportAppOpened()
        advanceUntilIdle()

        assertEquals(1, eventApi.calls)
        assertEquals("2026-11-23", store.lastSentDate)
    }

    @Test
    fun `the next day sends APP_OPENED again`() = runTest(dispatcher) {
        vm.reportAppOpened()
        advanceUntilIdle()

        clock.now = Instant.parse("2026-11-24T01:00:00Z")
        vm.reportAppOpened()
        advanceUntilIdle()

        assertEquals(2, eventApi.calls)
        assertEquals("2026-11-24", store.lastSentDate)
    }

    @Test
    fun `a day already sent before the app restarted is not sent again`() = runTest(dispatcher) {
        store.lastSentDate = "2026-11-23"

        vm.reportAppOpened()
        advanceUntilIdle()

        assertEquals(0, eventApi.calls)
    }

    @Test
    fun `a failed send is not remembered and is retried`() = runTest(dispatcher) {
        eventApi.fail = true
        vm.reportAppOpened()
        advanceUntilIdle()
        assertEquals(1, eventApi.calls)
        assertNull(store.lastSentDate)

        eventApi.fail = false
        vm.reportAppOpened()
        advanceUntilIdle()
        assertEquals(2, eventApi.calls)
        assertEquals("2026-11-23", store.lastSentDate)
    }
}

internal class FakeEventApi : EventApi {
    var calls = 0
    var fail = false

    override suspend fun appOpened(): ApiResponse<Unit> {
        calls++
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, null, null)
    }
}

internal class FakeAppOpenStore : AppOpenStore {
    override var lastSentDate: String? = null
}

/** A clock the test can move forward (UTC). */
internal class MutableClock(var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
}
