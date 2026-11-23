package com.fashionapp.data.repository

import javax.inject.Inject
import javax.inject.Singleton

/**
 * FR-18 "입어보기": hands the purchase check photo to the fitting screen as its garment image.
 * The fitting screen takes it once when it is shown.
 */
@Singleton
class FittingGarmentHandoff @Inject constructor() {
    private var pending: HandoffGarment? = null

    fun put(garment: HandoffGarment) {
        pending = garment
    }

    fun take(): HandoffGarment? = pending.also { pending = null }
}

/** A garment photo (JPEG bytes) and its description for the try-on request. */
class HandoffGarment(val jpeg: ByteArray, val description: String)
