package com.fashionapp.ui.shopping

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.PurchaseCheckApi
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.model.PurchaseDecision
import com.fashionapp.data.model.PurchaseRecord
import com.fashionapp.data.model.PurchaseVerdict
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.PurchaseCheckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clothesApi = FakeClothesApi()
    private val purchaseApi = FakePurchaseCheckApi()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loadedViewModel(): ShoppingViewModel {
        val vm = ShoppingViewModel(
            ClothesRepository(clothesApi),
            PurchaseCheckRepository(purchaseApi),
            ZoneId.of("Asia/Seoul")
        )
        vm.load()
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `summary shows the closet size and combo count from the server`() = runTest(dispatcher) {
        clothesApi.clothes = closetOf(7)
        clothesApi.comboCount = 23

        val state = loadedViewModel().state.value

        assertFalse(state.isLoading)
        assertEquals(7, state.closetCount)
        assertEquals(23, state.comboCount)
        assertNull(state.errorMessage)
    }

    @Test
    fun `closet of five or more enables the compare buttons without the guide`() = runTest(dispatcher) {
        clothesApi.clothes = closetOf(5)

        val state = loadedViewModel().state.value

        assertTrue(state.canCompare)
        assertFalse(state.showClosetGuide)
    }

    @Test
    fun `closet of four disables the compare buttons and shows the guide`() = runTest(dispatcher) {
        clothesApi.clothes = closetOf(4)

        val state = loadedViewModel().state.value

        assertFalse(state.canCompare)
        assertTrue(state.showClosetGuide)
    }

    @Test
    fun `before the closet is known the buttons stay disabled without the guide`() {
        val state = ShoppingHomeState()

        assertFalse(state.canCompare)
        assertFalse(state.showClosetGuide)
    }

    @Test
    fun `recent checks keep server order and only bought ones show a photo`() = runTest(dispatcher) {
        clothesApi.clothes = closetOf(6)
        purchaseApi.records = listOf(
            record("p1", PurchaseVerdict.BUY, PurchaseDecision.BOUGHT, plus = 4, imageUrl = "/api/images/p1.jpg",
                createdAt = "2026-11-22T16:30:00Z"),
            record("p2", PurchaseVerdict.SIMILAR, PurchaseDecision.SKIPPED, plus = 0, imageUrl = null,
                createdAt = "2026-11-20T03:00:00Z"),
            record("p3", PurchaseVerdict.THINK, PurchaseDecision.UNDECIDED, plus = null, imageUrl = null,
                createdAt = "2026-11-19T03:00:00Z", name = null, category = ClothesCategory.OUTER)
        )

        val rows = loadedViewModel().state.value.recentChecks

        assertEquals(listOf("p1", "p2", "p3"), rows.map { it.id })

        assertEquals("/api/images/p1.jpg", rows[0].photoUrl)
        assertEquals("니트 p1", rows[0].title)
        // 16:30 UTC on the 22nd is the 23rd in Seoul
        assertEquals("11월 23일 · 사요", rows[0].subtitle)
        assertEquals(ResultBadge.BOUGHT, rows[0].badge)
        assertEquals("+4", rows[0].plusText)

        assertNull(rows[1].photoUrl)
        assertEquals(ClothesCategory.TOP, rows[1].category)
        assertEquals("11월 20일 · 비슷한 게 있어요", rows[1].subtitle)
        assertEquals(ResultBadge.SKIPPED, rows[1].badge)
        assertEquals("+0", rows[1].plusText)

        assertNull(rows[2].photoUrl)
        assertEquals("아우터", rows[2].title)
        assertEquals("11월 19일 · 고민해보세요", rows[2].subtitle)
        assertNull(rows[2].badge)
        assertNull(rows[2].plusText)
    }

    @Test
    fun `a bought record without a photo url falls back to the category icon`() = runTest(dispatcher) {
        purchaseApi.records = listOf(
            record("p1", PurchaseVerdict.BUY, PurchaseDecision.BOUGHT, plus = 2, imageUrl = null, createdAt = null)
        )

        val row = loadedViewModel().state.value.recentChecks.single()

        assertNull(row.photoUrl)
        assertEquals("사요", row.subtitle)
    }

    @Test
    fun `a failed request shows an error and keeps the values that loaded`() = runTest(dispatcher) {
        clothesApi.clothes = closetOf(8)
        clothesApi.comboCount = 12
        purchaseApi.records = listOf(record("p1", PurchaseVerdict.BUY, PurchaseDecision.BOUGHT, 1, null, null))
        val vm = loadedViewModel()

        clothesApi.failSummary = true
        purchaseApi.fail = true
        clothesApi.clothes = closetOf(9)
        vm.load()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(ShoppingViewModel.MSG_LOAD_FAILED, state.errorMessage)
        assertEquals(9, state.closetCount)
        assertEquals(12, state.comboCount)
        assertEquals(listOf("p1"), state.recentChecks.map { it.id })

        vm.clearError()
        assertNull(vm.state.value.errorMessage)
    }

    @Test
    fun `closet request failure keeps buttons disabled without the guide`() = runTest(dispatcher) {
        clothesApi.failList = true

        val state = loadedViewModel().state.value

        assertNull(state.closetCount)
        assertFalse(state.canCompare)
        assertFalse(state.showClosetGuide)
        assertEquals(ShoppingViewModel.MSG_LOAD_FAILED, state.errorMessage)
    }

    private fun closetOf(size: Int): List<Clothes> = (1..size).map {
        Clothes(id = "c$it", imageUrl = "/api/images/c$it.jpg", category = ClothesCategory.TOP,
            color = "블랙", name = "옷 $it", createdAt = null)
    }

    private fun record(
        id: String,
        verdict: PurchaseVerdict,
        decision: PurchaseDecision,
        plus: Int?,
        imageUrl: String?,
        createdAt: String?,
        name: String? = "니트 $id",
        category: ClothesCategory = ClothesCategory.TOP
    ) = PurchaseRecord(
        id = id, name = name, category = category, color = "블랙", price = null, verdict = verdict,
        plus = plus, decision = decision, clothesId = null, imageUrl = imageUrl, createdAt = createdAt
    )
}

private class FakeClothesApi : ClothesApi {
    var clothes: List<Clothes> = emptyList()
    var comboCount = 0
    var failList = false
    var failSummary = false

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> = error("unused")

    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> {
        if (failList) throw RuntimeException("network")
        return ApiResponse(true, clothes, null)
    }

    override suspend fun getSummary(): ApiResponse<ClosetSummary> {
        if (failSummary) throw RuntimeException("network")
        return ApiResponse(true, ClosetSummary(comboCount), null)
    }

    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> = error("unused")

    override suspend fun reclassify(id: String): ApiResponse<Clothes> = error("unused")

    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")
}

private class FakePurchaseCheckApi : PurchaseCheckApi {
    var records: List<PurchaseRecord> = emptyList()
    var fail = false

    override suspend fun history(): ApiResponse<List<PurchaseRecord>> {
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, records, null)
    }
}
