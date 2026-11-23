package com.fashionapp.data.image

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** FR-21: ML Kit subject segmentation. Runs on the device; the photo is not sent anywhere for this step. */
@Singleton
class MlKitSubjectSegmenter @Inject constructor() : SubjectSegmenter {

    private val client by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build()
        )
    }

    override suspend fun foregroundMask(image: ArgbImage): FloatArray? {
        val bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
        val result = client.process(InputImage.fromBitmap(bitmap, 0)).await()
        val buffer = result.foregroundConfidenceMask ?: return null
        buffer.rewind()
        return FloatArray(buffer.remaining()).also { buffer.get(it) }
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
