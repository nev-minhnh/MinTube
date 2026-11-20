package com.hhst.youtubelite.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.provider.Settings
import android.view.OrientationEventListener
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
    fun isPhone(activity: Activity): Boolean = DeviceClass.isPhone(deviceSmallestWidthDp(activity))

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

    fun systemAutoRotate(context: Context): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1

    /**
     * Physical tilt for a portrait-locked phone watch page. The window no longer rotates on its own,
     * so the sensor (not the configuration) reports landscape; the player decides whether that
     * enters or leaves fullscreen (`PlayerViewModel.onPhysicalOrientation`).
     */
    class Tilt(private val context: Context, private val onTilt: (degrees: Int, systemAutoRotate: Boolean) -> Unit) {
        private val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                onTilt(orientation, systemAutoRotate(context))
            }
        }

        fun start() {
            if (listener.canDetectOrientation()) listener.enable()
        }

        fun stop() = listener.disable()
    }
}
