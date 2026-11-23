package com.fashionapp.data.image

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/** Opaque ARGB pixels in row-major order. Plain arrays so the background logic runs in JVM unit tests. */
class ArgbImage(val width: Int, val height: Int, val pixels: IntArray) {
    init {
        require(width > 0 && height > 0) { "empty image" }
        require(pixels.size == width * height) { "pixel count does not match size" }
    }
}

/**
 * Separates the subject (the garment) from the background on the device.
 * Returns a foreground confidence (0..1) per pixel, or null when nothing was found.
 */
fun interface SubjectSegmenter {
    suspend fun foregroundMask(image: ArgbImage): FloatArray?
}

/**
 * FR-21: replaces the photo background with a light solid color before upload.
 * Any problem (setting off, image too large, segmentation error or timeout, wrong mask size,
 * subject too small) returns the original image unchanged, so registration never fails because of this step.
 */
@Singleton
class BackgroundRemover @Inject constructor(
    private val settings: BackgroundRemovalSettings,
    private val segmenter: SubjectSegmenter
) {
    suspend fun process(image: ArgbImage): ArgbImage {
        if (!settings.enabled.value) return image
        if (image.width.toLong() * image.height > MAX_PIXELS) return image
        val mask = segment(image) ?: return image
        return composite(image, mask) ?: image
    }

    private suspend fun segment(image: ArgbImage): FloatArray? = try {
        withTimeout(TIMEOUT_MS) { segmenter.foregroundMask(image) }
    } catch (e: TimeoutCancellationException) {
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    companion object {
        /** Uploads are scaled to [ImageCompressor.MAX_EDGE] first; anything bigger is not segmented. */
        const val MAX_PIXELS = ImageCompressor.MAX_EDGE.toLong() * ImageCompressor.MAX_EDGE

        /** Subjects covering less of the photo than this are treated as a failed segmentation. */
        const val MIN_FOREGROUND_RATIO = 0.05

        /** Confidence at or above this counts as subject when measuring the subject size. */
        const val FOREGROUND_THRESHOLD = 0.5f

        /** First use may wait for the on-device model; after this the original is used. */
        const val TIMEOUT_MS = 8_000L

        /** Light neutral gray: a tinted background could shift the AI color classification. */
        const val BACKGROUND_COLOR: Int = 0xFFF2F2F0.toInt()

        /** Blends each pixel toward [BACKGROUND_COLOR] by its confidence. Null when the mask is unusable. */
        fun composite(image: ArgbImage, mask: FloatArray): ArgbImage? {
            if (mask.size != image.pixels.size) return null
            val foreground = mask.count { it >= FOREGROUND_THRESHOLD }
            if (foreground < image.pixels.size * MIN_FOREGROUND_RATIO) return null
            val out = IntArray(image.pixels.size) { i -> blend(image.pixels[i], mask[i].coerceIn(0f, 1f)) }
            return ArgbImage(image.width, image.height, out)
        }

        private fun blend(pixel: Int, alpha: Float): Int {
            if (alpha >= 1f) return pixel or OPAQUE
            if (alpha <= 0f) return BACKGROUND_COLOR
            val r = mix(pixel shr 16 and 0xFF, BACKGROUND_COLOR shr 16 and 0xFF, alpha)
            val g = mix(pixel shr 8 and 0xFF, BACKGROUND_COLOR shr 8 and 0xFF, alpha)
            val b = mix(pixel and 0xFF, BACKGROUND_COLOR and 0xFF, alpha)
            return OPAQUE or (r shl 16) or (g shl 8) or b
        }

        private fun mix(fg: Int, bg: Int, alpha: Float): Int = Math.round(fg * alpha + bg * (1f - alpha))

        private const val OPAQUE: Int = 0xFF shl 24
    }
}
