package com.fashionapp.ui.purchase

import androidx.lifecycle.SavedStateHandle
import com.fashionapp.data.repository.ClosetChanges
import com.fashionapp.data.repository.ClothesRepository
import com.fashionapp.data.repository.FittingGarmentHandoff
import com.fashionapp.data.repository.PurchaseCheckRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseCheckViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val api = FakePurchaseCheckApi()
    private val compressor = FakeCompressor()
    private val products = FakeProductImageSource()
    private val original = "camera-original".toByteArray()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(source: PurchaseSource) = PurchaseCheckViewModel(
        SavedStateHandle(mapOf(PurchaseCheckViewModel.ARG_SOURCE to source.name)),
        PurchaseCheckRepository(api, compressor),
        products,
        ClothesRepository(FakeClothesApi()),
        ClosetChanges(),
        FittingGarmentHandoff()
    )

    private fun TestScope.confirmed(source: PurchaseSource): PurchaseCheckViewModel {
        val vm = viewModel(source)
        if (source == PurchaseSource.URL) {
            vm.setUrl("https://shop.example/goods/1")
            vm.fetchProduct()
            advanceUntilIdle()
            vm.useSelectedProductImage()
        } else {
            vm.onPhotoPicked { ByteArrayInputStream(original) }
        }
        advanceUntilIdle()
        return vm
    }

    // ---- three paths lead to the same confirm step ----

    @Test
    fun `camera and gallery start with the picker, url starts with the url input`() {
        assertEquals(PurchaseStage.PICKING, viewModel(PurchaseSource.CAMERA).state.value.stage)
        assertEquals(PurchaseStage.PICKING, viewModel(PurchaseSource.GALLERY).state.value.stage)
        assertEquals(PurchaseStage.URL_INPUT, viewModel(PurchaseSource.URL).state.value.stage)
    }

    @Test
    fun `every source reaches the same confirm step with a compressed photo`() = runTest(dispatcher) {
        PurchaseSource.entries.forEach { source ->
            val state = confirmed(source).state.value
            assertEquals(source.name, PurchaseStage.CONFIRM, state.stage)
            assertTrue(source.name, state.canCompare)
            val expected = if (source == PurchaseSource.URL) products.imageBytes else original
            assertArrayEquals(source.name, FakeCompressor.compressed(expected), state.photo!!.jpeg)
        }
        assertEquals(3, compressor.calls)
    }

    @Test
    fun `url path downloads the selected candidate image`() = runTest(dispatcher) {
        val vm = viewModel(PurchaseSource.URL)
        vm.setUrl("https://shop.example/goods/1")
        vm.fetchProduct()
        advanceUntilIdle()
        assertEquals("https://shop.example/a.jpg", vm.state.value.productUrl.selectedImageUrl)

        vm.selectProductImage("https://shop.example/b.jpg")
        vm.useSelectedProductImage()
        advanceUntilIdle()

        assertEquals("https://shop.example/b.jpg", products.downloadedUrl)
        assertEquals(PurchaseStage.CONFIRM, vm.state.value.stage)
    }

    @Test
    fun `url fetch failure shows the message and stays on url input`() = runTest(dispatcher) {
        products.failFetch = true
        val vm = viewModel(PurchaseSource.URL)
        vm.setUrl("https://shop.example/goods/1")
        vm.fetchProduct()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(PurchaseStage.URL_INPUT, state.stage)
        assertEquals("이 페이지에서 옷 이미지를 찾지 못했어요.", state.productUrl.error)
        assertFalse(state.productUrl.isFetching)
    }

    @Test
    fun `empty url is rejected without fetching`() = runTest(dispatcher) {
        val vm = viewModel(PurchaseSource.URL)
        vm.fetchProduct()
        advanceUntilIdle()
        assertEquals(PurchaseCheckViewModel.MSG_URL_EMPTY, vm.state.value.productUrl.error)
    }

    @Test
    fun `cancelling the picker without a photo closes the flow`() = runTest(dispatcher) {
        val vm = viewModel(PurchaseSource.GALLERY)
        vm.onPickCancelled()
        assertTrue(vm.state.value.closed)
    }

    // ---- compression ----

    @Test
    fun `uploaded image is the compressed photo as jpeg`() = runTest(dispatcher) {
        val vm = confirmed(PurchaseSource.CAMERA)
        vm.compare()
        advanceUntilIdle()

        assertArrayEquals(FakeCompressor.compressed(original), api.uploadedBytes)
        assertEquals("image/jpeg", api.uploadedContentType)
    }

    @Test
    fun `unreadable photo shows an error and never uploads the original`() = runTest(dispatcher) {
        compressor.fail = true
        val vm = viewModel(PurchaseSource.GALLERY)
        vm.onPhotoPicked { ByteArrayInputStream(original) }
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(PurchaseStage.PICKING, state.stage)
        assertNull(state.photo)
        assertEquals(PurchaseCheckViewModel.MSG_PHOTO_FAILED, state.errorMessage)
        assertEquals(0, api.calls)
    }

    // ---- price ----

    @Test
    fun `price keeps digits only and is sent when entered`() = runTest(dispatcher) {
        val vm = confirmed(PurchaseSource.GALLERY)
        vm.setPrice("59,000원")
        assertEquals("59000", vm.state.value.priceText)

        vm.compare()
        advanceUntilIdle()
        assertEquals("59000", api.uploadedPrice)
    }

    @Test
    fun `price is optional`() = runTest(dispatcher) {
        val vm = confirmed(PurchaseSource.GALLERY)
        vm.compare()
        advanceUntilIdle()
        assertNull(api.uploadedPrice)
        assertEquals(PurchaseStage.RESULT, vm.state.value.stage)
    }

    // ---- analyzing ----

    @Test
    fun `fast response goes straight to the result without waiting for the steps`() = runTest(dispatcher) {
        val vm = confirmed(PurchaseSource.GALLERY)
        vm.compare()
        runCurrent() // no virtual time passes

        val state = vm.state.value
        assertEquals(PurchaseStage.RESULT, state.stage)
        assertEquals(6, state.result!!.plus)
        // The step timer is stopped, so nothing changes the result stage later
        advanceUntilIdle()
        assertEquals(PurchaseStage.RESULT, vm.state.value.stage)
    }

    @Test
    fun `slow response advances the steps by time and stops at the last one`() = runTest(dispatcher) {
        api.responseDelayMs = 10_000
        val vm = confirmed(PurchaseSource.GALLERY)
        val step = PurchaseCheckViewModel.STEP_DURATION_MS
        vm.compare()
        runCurrent()
        assertEquals(PurchaseStage.ANALYZING, vm.state.value.stage)
        assertEquals(0, vm.state.value.analysisStep)

        advanceTimeBy(step + 1)
        assertEquals(1, vm.state.value.analysisStep)
        advanceTimeBy(step)
        assertEquals(2, vm.state.value.analysisStep)
        advanceTimeBy(step * 3)
        assertEquals(2, vm.state.value.analysisStep)
        assertEquals(PurchaseStage.ANALYZING, vm.state.value.stage)

        advanceUntilIdle()
        assertEquals(PurchaseStage.RESULT, vm.state.value.stage)
        assertNotNull(vm.state.value.result)
    }

    @Test
    fun `failed check returns to confirm with a message and keeps the photo`() = runTest(dispatcher) {
        api.fail = RuntimeException("network")
        val vm = confirmed(PurchaseSource.CAMERA)
        vm.compare()
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(PurchaseStage.CONFIRM, state.stage)
        assertEquals(PurchaseCheckViewModel.MSG_CHECK_FAILED, state.errorMessage)
        assertNotNull(state.photo)
    }

    @Test
    fun `timeout shows the timeout message`() = runTest(dispatcher) {
        api.fail = SocketTimeoutException("timeout")
        val vm = confirmed(PurchaseSource.CAMERA)
        vm.compare()
        advanceUntilIdle()
        assertEquals(PurchaseCheckViewModel.MSG_TIMEOUT, vm.state.value.errorMessage)
    }
}
