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
}

class JpegUploadImageCompressor @Inject constructor() : UploadImageCompressor {
    override suspend fun compress(open: () -> InputStream?): ByteArray =
        withContext(Dispatchers.Default) { ImageCompressor.compress(open) }
}
