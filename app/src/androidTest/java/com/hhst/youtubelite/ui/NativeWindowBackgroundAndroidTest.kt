package com.hhst.youtubelite.ui

import android.graphics.drawable.ColorDrawable
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.settings.SettingsActivity
import com.hhst.youtubelite.ui.design.MintColors
import com.hhst.youtubelite.ui.theme.AppAppearance
import com.hhst.youtubelite.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeWindowBackgroundAndroidTest {
    @Test fun browseWindowsMatchTheAppAppearanceOnEntryChangesAndRecreation() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val original = AppAppearance.mode
        try {
            for (mode in listOf(ThemeMode.Dark, ThemeMode.Light)) {
                instrumentation.runOnMainSync { AppAppearance.update(mode) }
                ActivityScenario.launch<SettingsActivity>(SettingsActivity.intent(context)).use { host ->
                    fun assertBackground(expected: Int) {
                        host.onActivity {
                            val background = it.window.decorView.background
                            assertTrue(background is ColorDrawable)
                            assertEquals(expected, (background as ColorDrawable).color)
                        }
                    }
                    // The window shows the Signal page background of the current appearance.
                    val dark = MintColors.DarkBackground.toInt()
                    val light = MintColors.LightBackground.toInt()
                    assertBackground(if (mode == ThemeMode.Dark) dark else light)
                    instrumentation.runOnMainSync {
                        AppAppearance.update(if (mode == ThemeMode.Dark) ThemeMode.Light else ThemeMode.Dark)
                    }
                    val changed = if (mode == ThemeMode.Dark) light else dark
                    assertBackground(changed)
                    host.recreate()
                    assertBackground(changed)
                }
            }
        } finally { instrumentation.runOnMainSync { AppAppearance.update(original) } }
    }
}
