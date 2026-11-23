package com.fashionapp.ui.purchase

import androidx.lifecycle.SavedStateHandle
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesSeason
import com.fashionapp.data.model.PurchaseAttributesRequest
import com.fashionapp.data.model.PurchaseOutfit
import com.fashionapp.data.model.PurchaseVerdict
import com.fashionapp.data.model.SimilarClothes
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.FittingGarmentHandoff
import com.fashionapp.data.repository.PurchaseCheckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** FR-18: result stage states, decisions (BUY/SKIP, 410), re-judge and per-error messages. */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseResultViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakePurchaseCheckApi()
    private val clothesApi = FakeClothesApi()
    private val closetChanges = ClosetChanges()
    private val handoff = FittingGarmentHandoff()
    private val original = "camera-original".toByteArray()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = PurchaseCheckViewModel(
        SavedStateHandle(mapOf(PurchaseCheckViewModel.ARG_SOURCE to PurchaseSource.CAMERA.name)),
        PurchaseCheckRepository(api, FakeCompressor()),
        FakeProductImageSource(),
        ClothesRepository(clothesApi),
        closetChanges,
        handoff
    )

    private fun TestScope.onResult(): PurchaseCheckViewModel {
        val vm = viewModel()
        vm.onPhotoPicked { ByteArrayInputStream(original) }
        advanceUntilIdle()
        vm.compare()
        advanceUntilIdle()
        assertEquals(PurchaseStage.RESULT, vm.state.value.stage)
        return vm
    }

    // ---- verdict states ----

    @Test
    fun `each verdict has its own card and badge`() = runTest(dispatcher) {
        val expected = mapOf(
            PurchaseVerdict.SIMILAR to VerdictStyle(VerdictCardTone.BEIGE, VerdictBadgeTone.BROWN, "비슷한 게 있어요"),
            PurchaseVerdict.BUY to VerdictStyle(VerdictCardTone.INK, VerdictBadgeTone.WHITE, "사요"),
            PurchaseVerdict.THINK to VerdictStyle(VerdictCardTone.WHITE, VerdictBadgeTone.LIGHT_BEIGE, "고민해보세요")
        )
        expected.forEach { (verdict, style) ->
            api.result = sampleResult(verdict = verdict)
            val ui = onResult().state.value.resultUi!!
            assertEquals(verdict.name, style, ui.verdict)
            assertEquals("코디가 많이 늘어나요", ui.headline)
            assertEquals(listOf("a", "b", "c"), ui.reasons)
        }
    }

    @Test
    fun `plus block shows the server numbers`() = runTest(dispatcher) {
        api.result = sampleResult(plus = 4).copy(comboCountBefore = 12, comboCountAfter = 16)
        val ui = onResult().state.value.resultUi!!
        assertEquals("+4", ui.plusText)
        assertEquals("지금 12개 → 16개", ui.comboChangeText)
    }

    @Test
    fun `similar clothes and combos get photos from the closet list`() = runTest(dispatcher) {
        api.result = sampleResult(verdict = PurchaseVerdict.SIMILAR).copy(
            similar = listOf(SimilarClothes("c1", "회색 니트", 0.918, 7, null, "색만 달라요")),
            outfits = listOf(PurchaseOutfit(listOf("c2", "missing"), "출근"))
        )
        val ui = onResult().state.value.resultUi!!

        val row = ui.similar.single()
        assertEquals("/api/images/c1.jpg", row.photoUrl)
        assertEquals("92%", row.similarityText)
        assertEquals("착용 7회", row.wearText)
        assertEquals("색만 달라요", row.note)
        assertEquals(listOf("/api/images/c2.jpg", null), ui.outfits.single().photoUrls)
        assertEquals("출근", ui.outfits.single().label)
        assertEquals("6개 중 대표", ui.outfitHeaderText)
    }

    // ---- 살래요 / 안 살래요 ----

    @Test
    fun `buy registers the photo, shows the done step and refreshes the closet`() = runTest(dispatcher) {
        api.result = sampleResult(plus = 3).copy(comboCountBefore = 10, comboCountAfter = 13)
        val vm = onResult()
        val versionBefore = closetChanges.version.value

        vm.buy()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(listOf("check-1" to "BUY"), api.decisions)
        assertEquals(PurchaseStage.DONE, state.stage)
        assertEquals("new-1", state.registered?.id)
        assertEquals("만들 수 있는 코디가 13개가 됐어요. 사진을 다시 찍지 않아도 돼요.", state.doneMessage)
        assertEquals(versionBefore + 1, closetChanges.version.value)
    }

    @Test
    fun `skip records the decision and closes back to the shopping home`() = runTest(dispatcher) {
        val vm = onResult()
        vm.skip()
        advanceUntilIdle()

        assertEquals(listOf("check-1" to "SKIP"), api.decisions)
        assertTrue(vm.state.value.closed)
        assertEquals(0, closetChanges.version.value)
    }

    @Test
    fun `expired check shows the retake message and blocks buying`() = runTest(dispatcher) {
        api.decideFail = httpError(410, "PURCHASE_CHECK_EXPIRED")
        val vm = onResult()

        vm.buy()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(PurchaseStage.RESULT, state.stage)
        assertTrue(state.expired)
        assertFalse(state.canDecide)
        assertEquals("시간이 지나서 다시 찍어야 해요", state.errorMessage)

        // Buying again does nothing; skipping just leaves
        vm.buy()
        vm.skip()
        advanceUntilIdle()
        assertEquals(1, api.decisions.size)
        assertTrue(vm.state.value.closed)
    }

    @Test
    fun `failed decision keeps the result and allows a retry`() = runTest(dispatcher) {
        api.decideFail = RuntimeException("boom")
        val vm = onResult()
        vm.buy()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(PurchaseStage.RESULT, state.stage)
        assertEquals(PurchaseCheckViewModel.MSG_DECIDE_FAILED, state.errorMessage)
        assertTrue(state.canDecide)
    }

    // ---- 고치기 ----

    @Test
    fun `changing the category re-judges and replaces the result`() = runTest(dispatcher) {
        api.recheckResult = sampleResult(plus = 2, verdict = PurchaseVerdict.THINK)
            .copy(item = sampleResult().item!!.copy(category = ClothesCategory.OUTER), comboCountAfter = 12)
        val vm = onResult()
        vm.openAttributeSheet()
        assertTrue(vm.state.value.attributeSheetOpen)

        val edit = AttributeEdit.of(vm.state.value.result!!.item).copy(category = ClothesCategory.OUTER)
        vm.recheck(edit)
        advanceUntilIdle()

        // Only the changed field is sent
        assertEquals(listOf(PurchaseAttributesRequest(category = "OUTER")), api.recheckRequests)
        val state = vm.state.value
        assertFalse(state.attributeSheetOpen)
        assertEquals("+2", state.resultUi!!.plusText)
        assertEquals("아우터", state.resultUi!!.attributeChips.first())
        assertEquals(VerdictCardTone.WHITE, state.resultUi!!.verdict!!.card)
    }

    @Test
    fun `unchanged attributes close the sheet without a request`() = runTest(dispatcher) {
        val vm = onResult()
        vm.openAttributeSheet()
        vm.recheck(AttributeEdit.of(vm.state.value.result!!.item))
        advanceUntilIdle()
        assertTrue(api.recheckRequests.isEmpty())
        assertFalse(vm.state.value.attributeSheetOpen)
    }

    @Test
    fun `season and texts are sent by their server names`() = runTest(dispatcher) {
        val vm = onResult()
        vm.recheck(AttributeEdit.of(vm.state.value.result!!.item).copy(season = ClothesSeason.WINTER, material = " 울 "))
        advanceUntilIdle()
        assertEquals(PurchaseAttributesRequest(material = "울", season = "WINTER"), api.recheckRequests.single())
    }

    @Test
    fun `re-judging an expired check shows the retake message`() = runTest(dispatcher) {
        api.recheckFail = httpError(410, "PURCHASE_CHECK_EXPIRED")
        val vm = onResult()
        vm.openAttributeSheet()
        vm.recheck(AttributeEdit(category = ClothesCategory.BOTTOM))
        advanceUntilIdle()

        val state = vm.state.value
        assertTrue(state.expired)
        assertFalse(state.attributeSheetOpen)
        assertEquals(PurchaseErrors.MSG_EXPIRED, state.errorMessage)
    }

    // ---- 입어보기 ----

    @Test
    fun `try on hands this photo to the fitting screen`() = runTest(dispatcher) {
        val vm = onResult()
        vm.tryOn()
        assertTrue(vm.state.value.openFitting)

        val handed = handoff.take()!!
        assertArrayEquals(FakeCompressor.compressed(original), handed.jpeg)
        assertEquals("울 니트", handed.description)
        assertNull(handoff.take())

        vm.onFittingOpened()
        assertFalse(vm.state.value.openFitting)
    }

    // ---- error messages ----

    @Test
    fun `compare errors map to their own messages`() = runTest(dispatcher) {
        val cases = listOf(
            httpError(400, "CLOSET_TOO_SMALL") to PurchaseErrors.MSG_CLOSET_TOO_SMALL,
            httpError(429, "PURCHASE_CHECK_LIMIT") to PurchaseErrors.MSG_LIMIT,
            httpError(503, "PURCHASE_CHECK_UNAVAILABLE") to PurchaseErrors.MSG_UNAVAILABLE,
            httpError(504, "AI_SERVER_TIMEOUT") to PurchaseErrors.MSG_TIMEOUT,
            SocketTimeoutException("timeout") to PurchaseErrors.MSG_TIMEOUT,
            UnknownHostException("offline") to PurchaseErrors.MSG_NETWORK,
            httpError(400, "INVALID_PURCHASE_PRICE", "가격은 0원 이상 숫자로 입력해 주세요.") to
                "가격은 0원 이상 숫자로 입력해 주세요."
        )
        cases.forEach { (error, message) ->
            api.fail = error
            val vm = viewModel()
            vm.onPhotoPicked { ByteArrayInputStream(original) }
            advanceUntilIdle()
            vm.compare()
            advanceUntilIdle()
            assertEquals(error.toString(), message, vm.state.value.errorMessage)
            assertEquals(PurchaseStage.CONFIRM, vm.state.value.stage)
        }
    }

    @Test
    fun `decision errors map to their own messages`() = runTest(dispatcher) {
        val cases = listOf(
            httpError(409, "PURCHASE_ALREADY_DECIDED") to PurchaseErrors.MSG_ALREADY_DECIDED,
            httpError(503) to PurchaseErrors.MSG_UNAVAILABLE,
            httpError(504) to PurchaseErrors.MSG_TIMEOUT,
            UnknownHostException("offline") to PurchaseErrors.MSG_NETWORK
        )
        cases.forEach { (error, message) ->
            api.decideFail = error
            val vm = onResult()
            vm.buy()
            advanceUntilIdle()
            assertEquals(error.toString(), message, vm.state.value.errorMessage)
            assertFalse(vm.state.value.expired)
        }
    }
}
