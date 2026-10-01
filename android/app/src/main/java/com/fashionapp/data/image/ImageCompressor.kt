package com.fashionapp.data.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlin.math.max

/**
 * 업로드 전에 이미지를 긴 변 [MAX_EDGE]px 이하 JPEG(품질 [JPEG_QUALITY])로 줄인다.
 * 카메라 원본(수 MB)을 그대로 올리면 모바일 회선에서 업로드가 가장 오래 걸리고 S3/AI 서버 전송도 커지기 때문.
 * 옷 분류(gpt-4o-mini Vision `detail: low`)와 가상 피팅에는 이 정도 해상도로 충분하다.
 *
 * 재인코딩하면 EXIF가 사라져 사진이 옆으로 누울 수 있으므로 EXIF 회전을 비트맵에 직접 반영한다.
 */
object ImageCompressor {
    const val MAX_EDGE = 1024
    const val JPEG_QUALITY = 80

    /** [open]은 호출될 때마다 새 스트림을 돌려줘야 한다 (크기 확인 · 디코딩 · EXIF 읽기에 각각 한 번씩 연다). */
    fun compress(open: () -> InputStream?): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (open() ?: error("파일을 열 수 없어요")).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("이미지를 읽을 수 없어요")

        // 메모리를 아끼려고 2의 거듭제곱으로 먼저 줄여 디코딩하고, 정확한 크기는 아래 Matrix로 맞춘다
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(max(bounds.outWidth, bounds.outHeight))
        }
        val decoded = (open() ?: error("파일을 열 수 없어요")).use { BitmapFactory.decodeStream(it, null, decodeOptions) }
            ?: error("이미지를 읽을 수 없어요")

        val orientation = open()?.use {
            runCatching {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        val scale = MAX_EDGE.toFloat() / max(decoded.width, decoded.height)
        if (scale < 1f) matrix.postScale(scale, scale)
        applyExifOrientation(matrix, orientation)

        val transformed = if (matrix.isIdentity) decoded
        else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // JPEG는 투명도가 없어서 PNG의 투명 영역이 검게 나오지 않도록 흰 배경에 합성
        val flattened = if (transformed.hasAlpha()) flattenOnWhite(transformed) else transformed

        return ByteArrayOutputStream().use { out ->
            flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
    }

    /** 이미 메모리에 있는 이미지(쇼핑몰에서 내려받은 옷 이미지 등)용 */
    fun compress(bytes: ByteArray): ByteArray = compress { ByteArrayInputStream(bytes) }

    // 긴 변이 MAX_EDGE 이상으로 남는 가장 큰 2의 거듭제곱 (디코딩 후 정확히 MAX_EDGE로 축소)
    private fun sampleSize(longestEdge: Int): Int {
        var sample = 1
        while (longestEdge / (sample * 2) >= MAX_EDGE) sample *= 2
        return sample
    }

    private fun applyExifOrientation(matrix: Matrix, orientation: Int) {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postRotate(270f); matrix.postScale(-1f, 1f) }
        }
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, 0f, 0f, null)
        }
        return result
    }
}
