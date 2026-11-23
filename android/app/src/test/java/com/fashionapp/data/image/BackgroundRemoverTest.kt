package com.fashionapp.data.image

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundRemoverTest {

    private val settings = FakeBackgroundRemovalSettings()
    private val segmenter = FakeSegmenter()
    private val remover = BackgroundRemover(settings, segmenter)

    // 4x4 photo: red garment in the middle 2x2, green background around it
    private val garment = 0xFFC81E1E.toInt()
    private val wall = 0xFF2E8B57.toInt()
    private val photo = ArgbImage(4, 4, IntArray(16) { i -> if (i in CENTER) garment else wall })
    private val centerMask = FloatArray(16) { i -> if (i in CENTER) 1f else 0f }

    @Test
    fun `enabled - background becomes one solid color and the garment keeps its pixels`() = runTest {
        segmenter.mask = centerMask

        val result = remover.process(photo)

        CENTER.forEach { assertEquals(garment, result.pixels[it]) }
        val background = result.pixels.filterIndexed { i, _ -> i !in CENTER }.toSet()
        assertEquals(setOf(BackgroundRemover.BACKGROUND_COLOR), background)
        assertEquals(1, segmenter.calls)
    }

    @Test
    fun `background color is a light solid color`() {
        val c = BackgroundRemover.BACKGROUND_COLOR
        assertEquals(0xFF, c ushr 24)
        listOf(c shr 16 and 0xFF, c shr 8 and 0xFF, c and 0xFF).forEach { assertTrue(it >= 0xE0) }
    }

    @Test
    fun `soft edges blend toward the background by confidence`() = runTest {
        segmenter.mask = centerMask.copyOf().also { it[0] = 0.5f }

        val pixel = remover.process(photo).pixels[0]

        val bg = BackgroundRemover.BACKGROUND_COLOR
        assertEquals(Math.round(((wall shr 16 and 0xFF) + (bg shr 16 and 0xFF)) / 2f), pixel shr 16 and 0xFF)
        assertEquals(0xFF, pixel ushr 24)
    }

    @Test
    fun `setting off - original is returned without segmenting`() = runTest {
        settings.setEnabled(false)
        segmenter.mask = centerMask

        assertSame(photo, remover.process(photo))
        assertEquals(0, segmenter.calls)
    }

    @Test
    fun `segmentation error - original is returned`() = runTest {
        segmenter.error = IllegalStateException("model not downloaded")

        assertSame(photo, remover.process(photo))
    }

    @Test
    fun `no subject found - original is returned`() = runTest {
        segmenter.mask = null

        assertSame(photo, remover.process(photo))
    }

    @Test
    fun `segmentation that never finishes times out to the original`() = runTest {
        segmenter.hang = true

        assertSame(photo, remover.process(photo))
    }

    @Test
    fun `mask with the wrong size - original is returned`() = runTest {
        segmenter.mask = FloatArray(9) { 1f }

        assertSame(photo, remover.process(photo))
    }

    @Test
    fun `subject smaller than the minimum ratio - original is returned`() = runTest {
        // 1 of 100 pixels (1%) is under MIN_FOREGROUND_RATIO (5%)
        val big = ArgbImage(10, 10, IntArray(100) { wall })
        segmenter.mask = FloatArray(100) { i -> if (i == 55) 1f else 0f }

        assertSame(big, remover.process(big))
    }

    @Test
    fun `subject at the minimum ratio is processed`() {
        val big = ArgbImage(10, 10, IntArray(100) { wall })
        val mask = FloatArray(100) { i -> if (i < 5) 1f else 0f }

        val result = BackgroundRemover.composite(big, mask)

        assertEquals(BackgroundRemover.BACKGROUND_COLOR, result!!.pixels[99])
    }

    @Test
    fun `image larger than the size limit is not segmented`() = runTest {
        val edge = ImageCompressor.MAX_EDGE + 1
        val large = ArgbImage(edge, ImageCompressor.MAX_EDGE, IntArray(edge * ImageCompressor.MAX_EDGE))
        segmenter.mask = FloatArray(large.pixels.size) { 1f }

        assertSame(large, remover.process(large))
        assertEquals(0, segmenter.calls)
    }

    @Test
    fun `composite rejects a mismatched mask`() {
        assertNull(BackgroundRemover.composite(photo, FloatArray(3)))
    }

    private companion object {
        val CENTER = setOf(5, 6, 9, 10)
    }
}

private class FakeBackgroundRemovalSettings : BackgroundRemovalSettings {
    private val state = MutableStateFlow(true)
    override val enabled: StateFlow<Boolean> = state
    override fun setEnabled(enabled: Boolean) { state.value = enabled }
}

private class FakeSegmenter : SubjectSegmenter {
    var mask: FloatArray? = null
    var error: Exception? = null
    var hang = false
    var calls = 0

    override suspend fun foregroundMask(image: ArgbImage): FloatArray? {
        calls++
        error?.let { throw it }
        if (hang) awaitCancellation()
        return mask
    }
}
