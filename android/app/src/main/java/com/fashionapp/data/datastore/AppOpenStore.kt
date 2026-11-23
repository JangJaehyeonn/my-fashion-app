package com.fashionapp.data.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** FR-20: the last day ("YYYY-MM-DD") APP_OPENED reached the server, so it is sent at most once a day. */
interface AppOpenStore {
    var lastSentDate: String?
}

@Singleton
class AppOpenPrefs @Inject constructor(
    @ApplicationContext context: Context
) : AppOpenStore {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var lastSentDate: String?
        get() = prefs.getString(KEY_LAST_SENT, null)
        set(value) = prefs.edit().putString(KEY_LAST_SENT, value).apply()

    private companion object {
        const val PREFS = "app_open"
        const val KEY_LAST_SENT = "last_sent_date"
    }
}
