package com.fashionapp.ui.mypage

import com.fashionapp.data.image.BackgroundRemovalSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageSettingsViewModelTest {

    private val settings = object : BackgroundRemovalSettings {
        val state = MutableStateFlow(true)
        override val enabled: StateFlow<Boolean> = state
        override fun setEnabled(enabled: Boolean) { state.value = enabled }
    }

    @Test
    fun `background removal is on by default and can be switched off and on`() {
        val vm = ImageSettingsViewModel(settings)
        assertTrue(vm.backgroundRemoval.value)

        vm.setBackgroundRemoval(false)
        assertFalse(vm.backgroundRemoval.value)
        assertFalse(settings.state.value)

        vm.setBackgroundRemoval(true)
        assertTrue(settings.state.value)
    }

    @Test
    fun `description follows the switch`() {
        assertEquals("옷 사진 배경 지우기", ImageSettingsViewModel.LABEL)
        assertEquals("옷만 남기고 배경을 지워서 올려요. 사진은 기기 안에서 처리돼요.", ImageSettingsViewModel.description(true))
        assertEquals("찍은 사진을 그대로 올려요.", ImageSettingsViewModel.description(false))
    }
}
