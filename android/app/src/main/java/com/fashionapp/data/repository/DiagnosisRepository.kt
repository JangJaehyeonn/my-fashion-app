package com.fashionapp.data.repository

import com.fashionapp.data.api.DiagnosisApi
import com.fashionapp.data.model.StyleDiagnosis
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosisRepository @Inject constructor(
    private val diagnosisApi: DiagnosisApi
) {
    suspend fun diagnose(image: MultipartBody.Part): Result<StyleDiagnosis> = runCatching {
        diagnosisApi.diagnose(image).data ?: error("진단 실패")
    }

    suspend fun getDiagnoses(): Result<List<StyleDiagnosis>> = runCatching {
        diagnosisApi.getDiagnoses().data ?: emptyList()
    }

    suspend fun getDiagnosis(id: String): Result<StyleDiagnosis> = runCatching {
        diagnosisApi.getDiagnosis(id).data ?: error("진단 조회 실패")
    }
}
