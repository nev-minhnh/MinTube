package com.hhst.youtubelite.core

import android.app.Activity
import android.app.Application
import android.content.res.Resources
import android.os.Bundle
import androidx.core.os.ConfigurationCompat
import com.hhst.youtubelite.data.media.ContentLocale
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import java.util.Locale

/**
 * Keeps [MediaSession]'s request locale equal to the two content switches, the app
 * language and the device locale. The switches notify through [ExtensionManager]; the app
 * language (system-managed on API 33+) and the device locale have no listener here, so
 * every activity start and resume re-resolves. Unchanged values are a no-op.
 */
object ContentLocaleSync {
    fun install(app: Application, preferences: ExtensionManager) {
        val sync = { sync(app, preferences) }
        sync()
        preferences.addOnChangedListener { key ->
            if (key == "*" || key == PreferenceKeys.CONTENT_LANGUAGE_ENGLISH ||
                key == PreferenceKeys.TRENDING_REGION_VIETNAM || key == PreferenceKeys.APP_LANGUAGE) sync()
        }
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = sync()
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = sync()
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    private fun sync(app: Application, preferences: ExtensionManager) {
        val system = ConfigurationCompat.getLocales(Resources.getSystem().configuration)[0] ?: Locale.getDefault()
        MediaSession.setContentLocale(ContentLocale.resolve(
            englishContent = preferences.isEnabled(PreferenceKeys.CONTENT_LANGUAGE_ENGLISH),
            vietnamRegion = preferences.isEnabled(PreferenceKeys.TRENDING_REGION_VIETNAM),
            appLanguageTag = AppLocale.tag(app),
            system = system,
        ))
    }
}
