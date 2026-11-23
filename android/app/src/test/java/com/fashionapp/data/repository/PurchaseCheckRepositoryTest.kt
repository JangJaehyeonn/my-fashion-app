package com.fashionapp.data.repository

import com.fashionapp.data.api.LongRunningTimeoutInterceptor
import com.fashionapp.ui.purchase.FakeCompressor
import com.fashionapp.ui.purchase.FakePurchaseCheckApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class PurchaseCheckRepositoryTest {

    private val api = FakePurchaseCheckApi()
    private val compressor = FakeCompressor()
    private val repository = PurchaseCheckRepository(api, compressor)
    private val original = "gallery-original".toByteArray()

    @Test
    fun `compress runs the image compressor`() = runTest {
        val photo = repository.compress { ByteArrayInputStream(original) }.getOrThrow()

        assertEquals(1, compressor.calls)
        assertArrayEquals(FakeCompressor.compressed(original), photo.jpeg)
    }

    @Test
    fun `compress failure is a failed result`() = runTest {
        compressor.fail = true
        assertTrue(repository.compress { ByteArrayInputStream(original) }.isFailure)
    }

    @Test
    fun `check uploads the compressed photo as an image jpeg part with the price`() = runTest {
        val photo = repository.compress { ByteArrayInputStream(original) }.getOrThrow()

        val result = repository.check(photo, 39000L).getOrThrow()

        assertArrayEquals(FakeCompressor.compressed(original), api.uploadedBytes)
        assertEquals("image/jpeg", api.uploadedContentType)
        assertTrue(api.uploadedDisposition!!.contains("name=\"image\""))
        assertTrue(api.uploadedDisposition!!.contains("filename=\"purchase.jpg\""))
        assertEquals("39000", api.uploadedPrice)
        assertEquals("check-1", result.id)
    }

    @Test
    fun `check without a price sends no price part`() = runTest {
        repository.check(CompressedPhoto(byteArrayOf(1, 2)), null).getOrThrow()
        assertNull(api.uploadedPrice)
    }

    @Test
    fun `check failure is a failed result`() = runTest {
        api.fail = RuntimeException("503")
        assertTrue(repository.check(CompressedPhoto(byteArrayOf(1)), null).isFailure)
    }

    @Test
    fun `only POST purchase-checks gets the 30 second read timeout`() {
        assertEquals(30, LongRunningTimeoutInterceptor.PURCHASE_CHECK_READ_TIMEOUT_SECONDS)
        assertTrue(LongRunningTimeoutInterceptor.isPurchaseCheck("POST", "/api/purchase-checks"))
        assertTrue(LongRunningTimeoutInterceptor.isPurchaseCheck("POST", "/api/purchase-checks/"))
        assertFalse(LongRunningTimeoutInterceptor.isPurchaseCheck("GET", "/api/purchase-checks"))
        assertFalse(LongRunningTimeoutInterceptor.isPurchaseCheck("POST", "/api/purchase-checks/abc/decision"))
    }
}
