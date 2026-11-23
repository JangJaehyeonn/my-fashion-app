package com.fashionapp.data.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** FR-22: the last day ("YYYY-MM-DD") the user chose "나중에 하기" on the onboarding screen. */
interface OnboardingStore {
    var dismissedDate: String?
}

@Singleton
class OnboardingPrefs @Inject constructor(
    @ApplicationContext context: Context
) : OnboardingStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var dismissedDate: String?
        get() = prefs.getString(KEY_DISMISSED, null)
        set(value) = prefs.edit().putString(KEY_DISMISSED, value).apply()

    private companion object {
        const val PREFS = "onboarding"
        const val KEY_DISMISSED = "dismissed_date"
    }
}
