package com.fashionapp.data.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

/**
 * Compresses an image before upload. Wraps [ImageCompressor] (android.graphics) behind an interface
 * so repositories and ViewModels can be unit tested on the JVM with a fake.
 */
fun interface UploadImageCompressor {
    /** [open] must return a new stream on every call. Throws when the image cannot be read. */
    suspend fun compress(open: () -> InputStream?): ByteArray

    /** For images already in memory (product images downloaded from a shop). */
    suspend fun compress(bytes: ByteArray): ByteArray = compress { bytes.inputStream() }
}

/**
 * Scales the photo (long edge 1024px), removes the background when enabled (FR-21), then encodes JPEG.
 * Background removal returns the scaled original on any failure, so only an unreadable image throws.
 */
class JpegUploadImageCompressor @Inject constructor(
    private val backgroundRemover: BackgroundRemover
) : UploadImageCompressor {
    override suspend fun compress(open: () -> InputStream?): ByteArray = withContext(Dispatchers.Default) {
        val scaled = ImageCompressor.decode(open)
        val image = ImageCompressor.toArgbImage(scaled)
        val processed = backgroundRemover.process(image)
        ImageCompressor.encode(if (processed === image) scaled else ImageCompressor.toBitmap(processed))
    }
}
