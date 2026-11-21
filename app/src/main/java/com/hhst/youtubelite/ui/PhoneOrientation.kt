package com.hhst.youtubelite.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import com.hhst.youtubelite.ui.design.DeviceClass

/**
 * Phones (`smallestScreenWidthDp < 600`) run every screen portrait, whatever the system auto-rotate
 * setting; tablets rotate freely. Set per Activity in `onCreate` instead of in the manifest, so
 * tablets keep rotating (Android 16 also ignores manifest locks on large screens). Video fullscreen
 * still rotates: `PlayerWindowHost` requests landscape on entry and returns to [baseOrientation] on exit.
 */
object PhoneOrientation {
    /**
     * Device class from the largest window the display offers, not the current window: a tablet in
     * split screen stays a tablet, a foldable follows its fold state.
     */
    fun isPhone(activity: Activity): Boolean = !isTelevision(activity) && DeviceClass.isPhone(deviceSmallestWidthDp(activity))

    /**
     * A 1080p TV reports a 540 dp smallest width, but a TV cannot rotate: a portrait lock there
     * letterboxes the window into a 9:16 strip. TVs keep their landscape screen.
     */
    private fun isTelevision(activity: Activity): Boolean =
        activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
            android.content.res.Configuration.UI_MODE_TYPE_TELEVISION

    private fun deviceSmallestWidthDp(activity: Activity): Int {
        val density = activity.resources.displayMetrics.density.takeIf { it > 0f } ?: return 0
        val (width, height) = if (android.os.Build.VERSION.SDK_INT >= 30) {
            activity.windowManager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        } else {
            val size = android.graphics.Point()
            @Suppress("DEPRECATION") activity.windowManager.defaultDisplay.getRealSize(size)
            size.x to size.y
        }
        return (minOf(width, height) / density).toInt()
    }

    /** The orientation an Activity returns to when nothing (fullscreen) holds another one. */
    fun baseOrientation(activity: Activity): Int =
        if (isPhone(activity)) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

    /**
     * Locks a phone Activity to portrait, and clears a portrait lock a tablet may have kept from a
     * previous instance. Translucent and floating windows follow the Activity beneath them (and
     * API 26 throws if they request an orientation), so they are left alone.
     */
    fun lockOnPhone(activity: Activity) {
        if (translucentOrFloating(activity)) return
        val target = baseOrientation(activity)
        val current = activity.requestedOrientation
        if (target == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && current != target ||
            target == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED && current == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
            activity.requestedOrientation = target
    }

    private fun translucentOrFloating(activity: Activity): Boolean {
        val value = android.util.TypedValue()
        val theme = activity.theme
        return theme.resolveAttribute(android.R.attr.windowIsTranslucent, value, true) && value.data != 0 ||
            theme.resolveAttribute(android.R.attr.windowIsFloating, value, true) && value.data != 0
    }
}
