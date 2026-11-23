package com.fashionapp.ui.closet

import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.api.WearApi
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.model.WearRecordRequest
import com.fashionapp.data.model.WearRecordResult
import com.fashionapp.data.model.WearSummary
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.ProductPageRepository
import com.fashionapp.data.repository.WearRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ClosetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clothesApi = ClosetFakeClothesApi()
    private val wearApi = FakeWearApi()
    private val closetChanges = ClosetChanges()

    // Server values: combos and wear counts are shown as sent
    private val knit = piece("knit", ClothesCategory.TOP, combos = 6, wears = 3, lastWorn = "2026-11-10", created = "2026-10-01T10:00:00")
    private val shirt = piece("shirt", ClothesCategory.TOP, combos = 2, wears = 0, lastWorn = null, created = "2026-11-01T10:00:00")
    private val jeans = piece("jeans", ClothesCategory.BOTTOM, combos = 6, wears = 1, lastWorn = "2026-11-20", created = "2026-09-01T10:00:00")
    private val boots = piece("boots", ClothesCategory.SHOES, combos = 4, wears = 1, lastWorn = "2026-10-02", created = "2026-11-15T10:00:00")
    private val unknown = piece("unknown", ClothesCategory.UNCLASSIFIED, combos = 0, wears = 0, lastWorn = null, created = "2026-11-20T10:00:00")
        .copy(classificationFailed = true)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun piece(
        id: String, category: ClothesCategory, combos: Int, wears: Int, lastWorn: String?, created: String
    ) = Clothes(
        id = id, imageUrl = "/api/images/$id.jpg", category = category, color = "블랙", name = id,
        createdAt = created, comboCount = combos, wearCount = wears, lastWornAt = lastWorn, status = "AVAILABLE"
    )

    private fun TestScope.loaded(): ClosetViewModel {
        clothesApi.clothes = listOf(knit, shirt, jeans, boots, unknown)
        val vm = ClosetViewModel(
            ClothesRepository(clothesApi), ProductPageRepository(), closetChanges, WearRepository(wearApi)
        )
        // WhileSubscribed state flows only compute while collected
        vm.filteredClothes.launchIn(backgroundScope)
        vm.summary.launchIn(backgroundScope)
        vm.countByCategory.launchIn(backgroundScope)
        advanceUntilIdle()
        return vm
    }

    private fun ClosetViewModel.ids() = filteredClothes.value.map { it.id }

    @Test
    fun `default sort is most combos first with newer pieces first on ties`() = runTest(dispatcher) {
        val vm = loaded()
        assertEquals(ClosetSort.MOST_COMBOS, vm.sort.value)
        assertEquals(listOf("knit", "jeans", "boots", "shirt", "unknown"), vm.ids())
    }

    @Test
    fun `least worn sort puts fewest wears first, then the one worn longest ago`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectSort(ClosetSort.LEAST_WORN)
        advanceUntilIdle()
        // 0 wears: unknown (newer) then shirt; 1 wear: boots (worn 10-02) before jeans (worn 11-20)
        assertEquals(listOf("unknown", "shirt", "boots", "jeans", "knit"), vm.ids())
    }

    @Test
    fun `recent sort puts the newest registration first`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectSort(ClosetSort.RECENT)
        advanceUntilIdle()
        assertEquals(listOf("unknown", "boots", "shirt", "knit", "jeans"), vm.ids())
    }

    @Test
    fun `category chip filters and keeps the chosen sort, counts come from all pieces`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectSort(ClosetSort.LEAST_WORN)
        vm.selectCategory(ClothesCategory.TOP)
        advanceUntilIdle()
        assertEquals(listOf("shirt", "knit"), vm.ids())

        val counts = vm.countByCategory.value
        assertEquals(5, counts[null])
        assertEquals(2, counts[ClothesCategory.TOP])
        assertEquals(1, counts[ClothesCategory.BOTTOM])
        assertEquals(0, counts[ClothesCategory.OUTER])

        vm.selectCategory(null)
        advanceUntilIdle()
        assertEquals(5, vm.filteredClothes.value.size)
    }

    @Test
    fun `summary row shows server combos and month wears and counts classified never-worn pieces`() = runTest(dispatcher) {
        val vm = loaded()
        // shirt only: unknown is unclassified so it is not "잘 안 입는 옷"
        assertEquals(ClosetSummaryRow(comboCount = 12, monthWearCount = 7, unwornCount = 1), vm.summary.value)
    }

    @Test
    fun `summary stays empty when the server totals fail`() = runTest(dispatcher) {
        clothesApi.failSummary = true
        wearApi.fail = true
        val vm = loaded()
        assertNull(vm.summary.value.comboCount)
        assertNull(vm.summary.value.monthWearCount)
        assertEquals(1, vm.summary.value.unwornCount)
    }

    @Test
    fun `tapping 잘 안 입는 옷 shows all categories least worn first`() = runTest(dispatcher) {
        val vm = loaded()
        vm.selectCategory(ClothesCategory.BOTTOM)
        vm.selectSort(ClosetSort.RECENT)
        vm.showUnworn()
        advanceUntilIdle()
        assertNull(vm.selectedCategory.value)
        assertEquals(ClosetSort.LEAST_WORN, vm.sort.value)
        assertEquals(listOf("unknown", "shirt", "boots", "jeans", "knit"), vm.ids())
    }

    @Test
    fun `a piece bought through 살래요 reloads the closet and gets the NEW badge`() = runTest(dispatcher) {
        val vm = loaded()
        val bought = piece("bought", ClothesCategory.OUTER, combos = 3, wears = 2, lastWorn = "2026-11-21", created = "2026-11-22T10:00:00")
        clothesApi.clothes = clothesApi.clothes + bought

        closetChanges.notifyBought("bought")
        advanceUntilIdle()

        assertEquals(6, vm.filteredClothes.value.size)
        assertEquals(setOf("bought"), vm.boughtIds.value)
        assertEquals(ClothesBadge.NEW, ClosetList.badge(bought, vm.boughtIds.value))
        assertNull(ClosetList.badge(knit, vm.boughtIds.value))
    }

    // ---- card badges and labels ----

    @Test
    fun `only one top-left badge in priority order laundry, unclassified, unworn, new`() {
        val bought = setOf("x")
        val base = piece("x", ClothesCategory.TOP, combos = 1, wears = 0, lastWorn = null, created = "2026-11-01T00:00:00")

        assertEquals(ClothesBadge.LAUNDRY, ClosetList.badge(base.copy(status = "LAUNDRY", classificationFailed = true), bought))
        assertEquals(ClothesBadge.UNCLASSIFIED, ClosetList.badge(base.copy(category = ClothesCategory.UNCLASSIFIED), bought))
        assertEquals(ClothesBadge.UNWORN, ClosetList.badge(base, bought))
        assertEquals(ClothesBadge.NEW, ClosetList.badge(base.copy(wearCount = 1), bought))
        assertNull(ClosetList.badge(base.copy(wearCount = 1), emptySet()))
        // Unknown wear count is not treated as zero
        assertNull(ClosetList.badge(base.copy(wearCount = null), emptySet()))
    }

    @Test
    fun `combo badge and wear line use the server values`() {
        assertEquals("코디 6개", ClosetList.comboLabel(knit))
        assertEquals("코디 0개", ClosetList.comboLabel(knit.copy(comboCount = 0)))
        assertEquals("코디 계산 전", ClosetList.comboLabel(unknown))
        assertEquals("착용 3회", ClosetList.wearLabel(knit))
        assertEquals("아직 안 입었어요", ClosetList.wearLabel(shirt))
        assertNull(ClosetList.wearLabel(knit.copy(wearCount = null)))
    }
}

private class ClosetFakeClothesApi : ClothesApi {
    var clothes: List<Clothes> = emptyList()
    var failSummary = false

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> = error("unused")

    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> = ApiResponse(true, clothes, null)

    override suspend fun getSummary(): ApiResponse<ClosetSummary> {
        if (failSummary) throw RuntimeException("network")
        return ApiResponse(true, ClosetSummary(comboCount = 12), null)
    }

    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> = error("unused")

    override suspend fun reclassify(id: String): ApiResponse<Clothes> = error("unused")

    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")
}

private class FakeWearApi : WearApi {
    var fail = false

    override suspend fun getSummary(): ApiResponse<WearSummary> {
        if (fail) throw RuntimeException("network")
        return ApiResponse(true, WearSummary(weekCount = 2, monthCount = 7), null)
    }

    override suspend fun record(request: WearRecordRequest): ApiResponse<WearRecordResult> = error("unused")
}
