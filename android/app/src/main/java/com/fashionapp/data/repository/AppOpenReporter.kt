package com.fashionapp.data.repository

import com.fashionapp.data.api.EventApi
import com.fashionapp.data.datastore.AppOpenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-20: sends APP_OPENED (FR-14) at most once per local day. The day is stored only after the server accepted it,
 * so a failed send is retried the next time the main screen opens.
 */
@Singleton
class AppOpenReporter @Inject constructor(
    private val eventApi: EventApi,
    private val store: AppOpenStore,
    private val clock: Clock
) {
    private val mutex = Mutex()

    /** Returns true when APP_OPENED was sent now. */
    suspend fun reportIfNeeded(): Boolean = mutex.withLock {
        val today = LocalDate.now(clock).toString()
        if (store.lastSentDate == today) return@withLock false
        val sent = runCatching { eventApi.appOpened() }.isSuccess
        if (sent) store.lastSentDate = today
        sent
    }
}
