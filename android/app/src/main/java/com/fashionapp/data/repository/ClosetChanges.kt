package com.fashionapp.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tells the closet tab that clothes were added outside it (FR-18: "살래요" registers the photo).
 * The closet tab keeps its ViewModel across tab switches, so it reloads when [version] changes.
 * [boughtIds] are pieces bought through "살래요" in this app session; the closet shows them with a NEW badge (FR-19).
 */
@Singleton
class ClosetChanges @Inject constructor() {
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    private val _boughtIds = MutableStateFlow<Set<String>>(emptySet())
    val boughtIds: StateFlow<Set<String>> = _boughtIds.asStateFlow()

    fun notifyChanged() = _version.update { it + 1 }

    /** A piece was just bought: remember it for the NEW badge, then reload the closet. */
    fun notifyBought(clothesId: String?) {
        if (clothesId != null) _boughtIds.update { it + clothesId }
        notifyChanged()
    }
}
