package com.fashionapp.data.repository

import com.fashionapp.data.datastore.OnboardingStore
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-22: decides whether the first-run onboarding shows. It shows while the closet has fewer than
 * [MIN_CLOSET_SIZE] pieces, unless the user chose "나중에 하기" today (local day of the injected clock).
 */
@Singleton
class OnboardingGate @Inject constructor(
    private val clothesRepository: ClothesRepository,
    private val store: OnboardingStore,
    private val clock: Clock
) {
    /** true = show, false = do not show, null = the closet could not be loaded (decide later). */
    suspend fun shouldShow(): Boolean? {
        if (store.dismissedDate == today()) return false
        val count = clothesRepository.getMyClothes().getOrNull()?.size ?: return null
        return count < MIN_CLOSET_SIZE
    }

    /** "나중에 하기": do not show again until tomorrow. */
    fun dismissToday() {
        store.dismissedDate = today()
    }

    private fun today(): String = LocalDate.now(clock).toString()

    companion object {
        /** Same minimum as the purchase check (ShoppingHomeState.MIN_CLOSET_SIZE, backend PurchaseCheckService). */
        const val MIN_CLOSET_SIZE = 5
    }
}
