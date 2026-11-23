package com.fashionapp.ui.closet

import androidx.lifecycle.SavedStateHandle
import com.fashionapp.data.api.ClothesApi
import com.fashionapp.data.model.ApiResponse
import com.fashionapp.data.model.ClosetSummary
import com.fashionapp.data.model.Clothes
import com.fashionapp.data.model.ClothesCategory
import com.fashionapp.data.model.ClothesUpdateRequest
import com.fashionapp.data.repository.ClothesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class ClothesDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakeClothesApi()

    private val unclassified = Clothes(
        id = "c1", imageUrl = "/api/images/c1.jpg", category = ClothesCategory.UNCLASSIFIED,
        color = null, name = null, createdAt = null, classificationFailed = true
    )
    private val classified = unclassified.copy(
        category = ClothesCategory.TOP, color = "블랙", name = "검정 니트", classificationFailed = false
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.loadedViewModel(initial: Clothes = unclassified): ClothesDetailViewModel {
        api.clothes = listOf(initial)
        val vm = ClothesDetailViewModel(SavedStateHandle(mapOf("clothesId" to initial.id)), ClothesRepository(api))
        advanceUntilIdle()
        return vm
    }

    @Test
    fun `load fills the form and leaves category empty for unclassified clothes`() = runTest(dispatcher) {
        val vm = loadedViewModel()

        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(unclassified, state.clothes)
        assertNull(state.category)
        assertEquals("", state.color)
        assertFalse(state.canSave)
    }

    @Test
    fun `load shows an error when the clothes is missing or the request fails`() = runTest(dispatcher) {
        api.clothes = emptyList()
        val missing = ClothesDetailViewModel(SavedStateHandle(mapOf("clothesId" to "nope")), ClothesRepository(api))
        advanceUntilIdle()
        assertEquals(MSG_NOT_FOUND, missing.state.value.loadError)

        api.failList = true
        missing.load()
        advanceUntilIdle()
        assertEquals(MSG_LOAD_FAILED, missing.state.value.loadError)
    }

    @Test
    fun `save success sends the edited values and updates the state`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        api.updateResult = { _, _ -> classified }

        vm.selectCategory(ClothesCategory.TOP)
        vm.setColor(" 블랙 ")
        vm.setName("검정 니트")
        assertTrue(vm.state.value.canSave)
        vm.save()
        assertTrue(vm.state.value.isSaving)
        assertFalse(vm.state.value.canSave)
        advanceUntilIdle()

        assertEquals(ClothesUpdateRequest("TOP", "블랙", "검정 니트"), api.lastUpdate)
        val state = vm.state.value
        assertFalse(state.isSaving)
        assertTrue(state.changed)
        assertEquals(MSG_SAVED, state.message)
        assertEquals(classified, state.clothes)
        assertFalse(state.clothes!!.isUnclassified)
        assertEquals(ClothesCategory.TOP, state.category)
        assertFalse(state.hasChanges)
    }

    @Test
    fun `save sends blank fields as null so the server keeps them`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        api.updateResult = { _, _ -> unclassified.copy(category = ClothesCategory.SHOES, classificationFailed = false) }

        vm.selectCategory(ClothesCategory.SHOES)
        vm.setColor("   ")
        vm.save()
        advanceUntilIdle()

        assertEquals(ClothesUpdateRequest("SHOES", null, null), api.lastUpdate)
    }

    @Test
    fun `save failure keeps the input and shows a message`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        api.updateResult = { _, _ -> throw RuntimeException("network") }

        vm.selectCategory(ClothesCategory.BOTTOM)
        vm.setName("청바지")
        vm.save()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isSaving)
        assertFalse(state.changed)
        assertEquals(MSG_SAVE_FAILED, state.message)
        assertEquals(ClothesCategory.BOTTOM, state.category)
        assertEquals("청바지", state.name)
        assertEquals(unclassified, state.clothes)
        assertTrue(state.canSave)

        vm.clearMessage()
        assertNull(vm.state.value.message)
    }

    @Test
    fun `save rejects too long text without calling the server`() = runTest(dispatcher) {
        val vm = loadedViewModel()

        vm.setName("가".repeat(MAX_CLOTHES_TEXT_LENGTH + 1))
        vm.save()
        advanceUntilIdle()

        assertEquals(MSG_TEXT_TOO_LONG, vm.state.value.message)
        assertNull(api.lastUpdate)
    }

    @Test
    fun `unclassified cannot be picked as a category`() = runTest(dispatcher) {
        val vm = loadedViewModel()

        vm.selectCategory(ClothesCategory.UNCLASSIFIED)

        assertNull(vm.state.value.category)
        assertFalse(vm.state.value.canSave)
    }

    @Test
    fun `reclassify success replaces the values`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        api.reclassifyResult = { classified }

        vm.reclassify()
        assertTrue(vm.state.value.isReclassifying)
        assertFalse(vm.state.value.canReclassify)
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isReclassifying)
        assertTrue(state.changed)
        assertEquals(MSG_RECLASSIFIED, state.message)
        assertEquals(classified, state.clothes)
        assertEquals(ClothesCategory.TOP, state.category)
        assertEquals("블랙", state.color)
        assertEquals("검정 니트", state.name)
        assertEquals(1, api.reclassifyCalls)
    }

    @Test
    fun `reclassify failure keeps the values and shows a guide message`() = runTest(dispatcher) {
        val vm = loadedViewModel(classified)
        api.reclassifyResult = { throw RuntimeException("503") }

        vm.setName("내가 고친 이름")
        vm.reclassify()
        advanceUntilIdle()

        val state = vm.state.value
        assertFalse(state.isReclassifying)
        assertFalse(state.changed)
        assertEquals(MSG_RECLASSIFY_FAILED, state.message)
        assertEquals(classified, state.clothes)
        assertEquals("내가 고친 이름", state.name)
    }

    @Test
    fun `reclassify that still returns unclassified shows the guide message`() = runTest(dispatcher) {
        val vm = loadedViewModel()
        api.reclassifyResult = { unclassified }

        vm.reclassify()
        advanceUntilIdle()

        assertEquals(MSG_RECLASSIFY_FAILED, vm.state.value.message)
        assertTrue(vm.state.value.clothes!!.isUnclassified)
    }

    @Test
    fun `isUnclassified follows the flag or the category`() {
        assertTrue(unclassified.isUnclassified)
        assertTrue(unclassified.copy(classificationFailed = false).isUnclassified)
        assertFalse(classified.isUnclassified)
    }
}

private class FakeClothesApi : ClothesApi {
    var clothes: List<Clothes> = emptyList()
    var failList = false
    var updateResult: (String, ClothesUpdateRequest) -> Clothes = { _, _ -> error("not set") }
    var reclassifyResult: (String) -> Clothes = { error("not set") }
    var lastUpdate: ClothesUpdateRequest? = null
    var reclassifyCalls = 0

    override suspend fun register(image: MultipartBody.Part): ApiResponse<Clothes> = error("unused")

    override suspend fun getMyClothes(): ApiResponse<List<Clothes>> {
        if (failList) throw RuntimeException("network")
        return ApiResponse(true, clothes, null)
    }

    override suspend fun getSummary(): ApiResponse<ClosetSummary> = error("unused")

    override suspend fun update(id: String, request: ClothesUpdateRequest): ApiResponse<Clothes> {
        lastUpdate = request
        return ApiResponse(true, updateResult(id, request), null)
    }

    override suspend fun reclassify(id: String): ApiResponse<Clothes> {
        reclassifyCalls++
        return ApiResponse(true, reclassifyResult(id), null)
    }

    override suspend fun delete(id: String): ApiResponse<Unit> = error("unused")
}
