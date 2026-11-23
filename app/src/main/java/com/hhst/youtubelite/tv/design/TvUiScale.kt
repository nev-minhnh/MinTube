package com.hhst.youtubelite.tv.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

/**
 * Whole-interface size for MinTube TV (Settings → Display → Interface size): every TV screen is drawn
 * at this fraction of its 10-foot size, text and layout alike, so a TV that renders it too large can
 * still show everything (the sign-in code included). Stored per device; 100% is the default.
 */
object TvUiScale {
    val OPTIONS = listOf(0.25f, 0.5f, 0.75f, 1f)
    private const val PREFS = "tv_display"
    private const val KEY = "ui_scale"
    private var loaded = false

    /** Current scale; Compose reads it, so a change redraws every TV screen at once. */
    var current by mutableFloatStateOf(1f)
        private set

    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        current = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY, 1f).takeIf { it in OPTIONS } ?: 1f
    }

    fun set(context: Context, scale: Float) {
        if (scale !in OPTIONS) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY, scale).apply()
        loaded = true
        current = scale
    }

    fun label(scale: Float) = "${(scale * 100).toInt()}%"
}
