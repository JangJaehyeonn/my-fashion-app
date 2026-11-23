package com.fashionapp.data.image

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** FR-21: whether uploads get their background removed. On by default; switched in 마이 > 설정. */
interface BackgroundRemovalSettings {
    val enabled: StateFlow<Boolean>
    fun setEnabled(enabled: Boolean)
}

@Singleton
class BackgroundRemovalPrefs @Inject constructor(
    @ApplicationContext context: Context
) : BackgroundRemovalSettings {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    override val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    override fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _enabled.value = enabled
    }

    private companion object {
        const val PREFS = "image_settings"
        const val KEY_ENABLED = "background_removal_enabled"
    }
}
