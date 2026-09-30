package com.fashionapp.ui.common

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

// TakePicture()에 넘길 촬영 결과 저장용 URI — res/xml/file_paths.xml의 cache "camera/" 경로와 짝
fun createCameraImageUri(context: Context): Uri {
    val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(cameraDir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
