package com.fashionapp.ui.mypage

import androidx.lifecycle.ViewModel
import com.fashionapp.data.image.BackgroundRemovalSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** FR-21: 마이 > 설정의 "옷 사진 배경 지우기" 스위치. */
@HiltViewModel
class ImageSettingsViewModel @Inject constructor(
    private val settings: BackgroundRemovalSettings
) : ViewModel() {

    val backgroundRemoval: StateFlow<Boolean> = settings.enabled

    fun setBackgroundRemoval(enabled: Boolean) = settings.setEnabled(enabled)

    companion object {
        const val LABEL = "옷 사진 배경 지우기"

        fun description(enabled: Boolean): String =
            if (enabled) "옷만 남기고 배경을 지워서 올려요. 사진은 기기 안에서 처리돼요."
            else "찍은 사진을 그대로 올려요."
    }
}
