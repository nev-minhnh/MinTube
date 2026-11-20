package com.hhst.youtubelite.player.surface

import androidx.annotation.VisibleForTesting
import com.hhst.youtubelite.core.PreferenceStore

/**
 * The in-app mini-player has one fixed placement (bottom-start corner of the content area) and the
 * default size, so nothing about it is persisted. Older versions saved a dragged offset and a pinched
 * width; those keys are dropped the first time the store is used so they can never park the window
 * mid-screen.
 */
class MiniPlayerStore(
    private val kv: PreferenceStore,
) {
    init {
        clearLegacyPlacement()
    }

    fun clearLegacyPlacement() {
        val present = kv.keys().toSet()
        LEGACY_KEYS.filter(present::contains).forEach(kv::remove)
    }

    fun hasLegacyPlacement(): Boolean = kv.keys().any(LEGACY_KEYS::contains)

    /** Simulates a placement saved by a version that still let the user drag and resize the mini. */
    @VisibleForTesting
    fun writeLegacyPlacement(widthDp: Int, translationXDp: Float, translationYDp: Float) {
        kv.putInt(KEY_WIDTH_DP, widthDp)
        kv.putFloat(KEY_TX_DP, translationXDp)
        kv.putFloat(KEY_TY_DP, translationYDp)
    }

    private companion object {
        const val KEY_WIDTH_DP = "mini_player_width_dp"
        const val KEY_TX_DP = "mini_player_translation_x_dp"
        const val KEY_TY_DP = "mini_player_translation_y_dp"
        val LEGACY_KEYS = setOf(KEY_WIDTH_DP, KEY_TX_DP, KEY_TY_DP)
    }
}
