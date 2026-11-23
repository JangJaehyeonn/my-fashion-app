package com.fashionapp.ui.mypage

import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.api.WearApi
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.PurchaseAttributesRequest
import com.fashionapp.data.model.PurchaseCheckResult
import com.fashionapp.data.model.PurchaseDecision
import com.fashionapp.data.model.PurchaseDecisionRequest
import com.fashionapp.data.model.PurchaseDecisionResult
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.model.PurchaseSummary
import com.fashionapp.data.model.PurchaseVerdict
import com.fashionapp.data.model.WearRecordRequest
import com.fashionapp.data.model.WearRecordResult
import com.fashionapp.data.model.WearSummary
import com.fashionapp.data.repository.PurchaseCheckRepository
import com.fashionapp.data.repository.WearRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MySummaryViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val wearApi = FakeMyWearApi()
    private val purchaseApi = FakeMyPurchaseApi()
    private val vm by lazy {
        MySummaryViewModel(WearRepository(wearApi), PurchaseCheckRepository(purchaseApi) { error("unused") })
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun record(
        id: String,
        decision: PurchaseDecision,
        wears: Int?,
        expected: Long? = null,
        current: Long? = null
    ) = PurchaseRecord(
        id = id, name = "니트 $id", category = ClothesCategory.TOP, color = "블랙", price = 59_000,
        verdict = PurchaseVerdict.BUY, plus = 5, decision = decision, clothesId = "c-$id",
        imageUrl = "/api/images/$id.jpg", createdAt = null,
        expectedCostPerWear = expected, wearCount = wears, currentCostPerWear = current
    )

    @Test
    fun `month cells show the server values`() = runTest(dispatcher) {
        wearApi.month = 12
        purchaseApi.summary = PurchaseSummary(monthCount = 4, monthPlus = 9)

        vm.load()
        advanceUntilIdle()

        assertEquals(MonthSummaryUi(wearText = "12회", checkText = "4회", plusText = "+9개"), vm.summary.value)
    }

    @Test
    fun `bought list has only bought pieces that still exist, with before and now cost per wear`() = runTest(dispatcher) {
        purchaseApi.records = listOf(
            record("a", PurchaseDecision.BOUGHT, wears = 7, expected = 2_950, current = 8_429),
            record("b", PurchaseDecision.BOUGHT, wears = 0, expected = 2_950),
            record("c", PurchaseDecision.SKIPPED, wears = null),
            record("d", PurchaseDecision.UNDECIDED, wears = null),
            // Bought, then the piece was deleted: the server sends no wear count
            record("e", PurchaseDecision.BOUGHT, wears = null)
        )

        vm.load()
        advanceUntilIdle()

        assertEquals(
            listOf(
                BoughtRow("a", "니트 a", "/api/images/a.jpg", "예상 1회 2,950원 → 지금 1회 8,429원", unworn = false),
                BoughtRow("b", "니트 b", "/api/images/b.jpg", "아직 안 입었어요", unworn = true)
            ),
            vm.bought.value
        )
    }

    @Test
    fun `cost line without an estimate or a price still shows what the server knows`() {
        assertEquals("지금 1회 5,000원 · 착용 3회", MySummaryViewModel.costText(expected = null, current = 5_000, wears = 3))
        assertEquals("착용 3회", MySummaryViewModel.costText(expected = 2_000, current = null, wears = 3))
        assertEquals(MySummaryViewModel.MSG_UNWORN, MySummaryViewModel.costText(expected = 2_000, current = null, wears = 0))
    }

    @Test
    fun `a failed load keeps the cells empty and a later failure keeps the last values`() = runTest(dispatcher) {
        wearApi.fail = true
        purchaseApi.fail = true
        vm.load()
        advanceUntilIdle()
        assertEquals(MonthSummaryUi(), vm.summary.value)
        assertEquals(emptyList<BoughtRow>(), vm.bought.value)

        wearApi.fail = false
        purchaseApi.fail = false
        wearApi.month = 3
        purchaseApi.summary = PurchaseSummary(monthCount = 1, monthPlus = 2)
        vm.load()
        advanceUntilIdle()

        wearApi.fail = true
        purchaseApi.fail = true
        vm.load()
        advanceUntilIdle()
        assertEquals(MonthSummaryUi(wearText = "3회", checkText = "1회", plusText = "+2개"), vm.summary.value)
    }
}

private class FakeMyWearApi : WearApi {
    var month = 0
    var fail = false

    override suspend fun getSummary(): ApiResponse<WearSummary> {
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, WearSummary(weekCount = 0, monthCount = month), null)
    }

    override suspend fun record(request: WearRecordRequest): ApiResponse<WearRecordResult> = error("unused")
}

private class FakeMyPurchaseApi : PurchaseCheckApi {
    var records: List<PurchaseRecord> = emptyList()
    var summary = PurchaseSummary(monthCount = 0, monthPlus = 0)
    var fail = false

    override suspend fun check(image: MultipartBody.Part, price: RequestBody?): ApiResponse<PurchaseCheckResult> =
        error("unused")

    override suspend fun history(): ApiResponse<List<PurchaseRecord>> {
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, records, null)
    }

    override suspend fun summary(): ApiResponse<PurchaseSummary> {
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, summary, null)
    }

    override suspend fun decide(id: String, request: PurchaseDecisionRequest): ApiResponse<PurchaseDecisionResult> =
        error("unused")

    override suspend fun updateAttributes(id: String, request: PurchaseAttributesRequest): ApiResponse<PurchaseCheckResult> =
        error("unused")
}
