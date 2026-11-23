package com.fashionapp.ui.home

import com.fashionapp.data.api.WearApi
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.MostWorn
import com.fashionapp.data.model.WearRecordRequest
import com.fashionapp.data.model.WearRecordResult
import com.fashionapp.data.model.WearSummary
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.WearRepository
import com.fashionapp.ui.navigation.MutableClock
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeWearViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeHomeWearApi()
    private val closetChanges = ClosetChanges()
    private val clock = MutableClock(Instant.parse("2026-11-23T03:00:00Z"))
    private val vm by lazy { HomeWearViewModel(WearRepository(api), closetChanges, clock) }

    private val look = LookCard(
        items = listOf(piece("top"), piece("pants"), piece("shoes")),
        reason = "편한 하루",
        styleTag = "캐주얼"
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun piece(id: String, lastWorn: String? = null) = Clothes(
        id = id, imageUrl = "/api/images/$id.jpg", category = ClothesCategory.TOP, color = "블랙", name = id,
        createdAt = null, wearCount = 1, lastWornAt = lastWorn
    )

    @Test
    fun `recording a look sends its pieces, marks it done and reloads the closet and week card`() = runTest(dispatcher) {
        assertEquals(WearButtonState.IDLE, vm.buttonState(look))
        val closetVersion = closetChanges.version.value

        vm.recordWorn(look)
        assertEquals(WearButtonState.SAVING, vm.buttonState(look))
        api.week = 4
        advanceUntilIdle()

        assertEquals(listOf(listOf("top", "pants", "shoes")), api.recorded)
        assertEquals(WearButtonState.DONE, vm.buttonState(look))
        assertEquals(closetVersion + 1, closetChanges.version.value)
        assertEquals("이번 주 착용 기록 4회", vm.weekCard.value?.weekText)
        assertNull(vm.message.value)
    }

    @Test
    fun `a look already recorded today cannot be recorded again`() = runTest(dispatcher) {
        vm.recordWorn(look)
        advanceUntilIdle()

        vm.recordWorn(look)
        // Same pieces in another order are the same look
        vm.recordWorn(look.copy(items = look.items.reversed()))
        advanceUntilIdle()

        assertEquals(1, api.recorded.size)
        assertEquals(WearButtonState.DONE, vm.buttonState(look.copy(items = look.items.reversed())))
    }

    @Test
    fun `pressing twice while saving sends one request`() = runTest(dispatcher) {
        api.gate = CompletableDeferred()
        vm.recordWorn(look)
        advanceUntilIdle()
        vm.recordWorn(look)
        api.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, api.recorded.size)
        assertEquals(WearButtonState.DONE, vm.buttonState(look))
    }

    @Test
    fun `a failed record shows a message and the button goes back`() = runTest(dispatcher) {
        api.failRecord = true
        val closetVersion = closetChanges.version.value

        vm.recordWorn(look)
        advanceUntilIdle()

        assertEquals(HomeWearViewModel.MSG_RECORD_FAILED, vm.message.value)
        assertEquals(WearButtonState.IDLE, vm.buttonState(look))
        assertEquals(closetVersion, closetChanges.version.value)

        // The user can try again
        vm.clearMessage()
        api.failRecord = false
        vm.recordWorn(look)
        advanceUntilIdle()
        assertEquals(WearButtonState.DONE, vm.buttonState(look))
        assertNull(vm.message.value)
    }

    @Test
    fun `the done state ends when the day changes`() = runTest(dispatcher) {
        vm.recordWorn(look)
        advanceUntilIdle()

        clock.now = Instant.parse("2026-11-24T03:00:00Z")

        assertEquals(WearButtonState.IDLE, vm.buttonState(look))
    }

    @Test
    fun `a look whose pieces the server already has as worn today is done`() {
        val today = "2026-11-23"
        val worn = look.copy(items = look.items.map { it.copy(lastWornAt = today) })
        val partly = look.copy(items = look.items.mapIndexed { i, c -> if (i == 0) c.copy(lastWornAt = today) else c })

        assertEquals(WearButtonState.DONE, vm.buttonState(worn))
        assertEquals(WearButtonState.IDLE, vm.buttonState(partly))
    }

    @Test
    fun `week card shows the server week count and most worn piece`() = runTest(dispatcher) {
        api.week = 5
        api.mostWorn = MostWorn(id = "knit", name = "네이비 니트", count = 7)

        vm.loadSummary()
        advanceUntilIdle()

        assertEquals(WeekWearCard("이번 주 착용 기록 5회", "가장 많이 입은 옷: 네이비 니트 · 7회"), vm.weekCard.value)
    }

    @Test
    fun `week card without any wear has no most worn line, and a failed load shows no card`() = runTest(dispatcher) {
        vm.loadSummary()
        advanceUntilIdle()
        assertEquals(WeekWearCard("이번 주 착용 기록 0회", null), vm.weekCard.value)

        val failing = HomeWearViewModel(WearRepository(FakeHomeWearApi().apply { failSummary = true }), closetChanges, clock)
        failing.loadSummary()
        advanceUntilIdle()
        assertNull(failing.weekCard.value)
    }
}

private class FakeHomeWearApi : WearApi {
    var week = 0
    var mostWorn: MostWorn? = null
    var failSummary = false
    var failRecord = false
    var gate: CompletableDeferred<Unit>? = null
    val recorded = mutableListOf<List<String>>()

    override suspend fun getSummary(): ApiResponse<WearSummary> {
        if (failSummary) throw RuntimeException("network")
        return ApiResponse(true, WearSummary(weekCount = week, monthCount = week, mostWorn = mostWorn), null)
    }

    override suspend fun record(request: WearRecordRequest): ApiResponse<WearRecordResult> {
        recorded += request.clothesIds
        gate?.await()
        if (failRecord) throw RuntimeException("network")
        return ApiResponse(true, WearRecordResult(date = "2026-11-23", recorded = request.clothesIds.size), null)
    }
}
