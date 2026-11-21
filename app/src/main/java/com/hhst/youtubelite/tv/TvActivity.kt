package com.hhst.youtubelite.tv

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.hhst.youtubelite.core.AppLocale
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.ui.design.MintColors

/**
 * Base for every TV screen: the persisted app language, a #0A0A0A window, no system bars,
 * and [setTvContent]. No PiP and no phone transitions; the phone hosts stay untouched.
 */
abstract class TvActivity : ComponentActivity() {
    private var appliedLanguage: String? = null

    override fun attachBaseContext(newBase: android.content.Context) {
        appliedLanguage = AppLocale.tag(newBase)
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(MintColors.DarkBackground.toInt()))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        // Below API 33 the app language is applied by wrapping the context; follow a change made elsewhere.
        if (Build.VERSION.SDK_INT < 33 && appliedLanguage != AppLocale.tag(this)) {
            recreate()
            return
        }
        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    protected fun setTvContent(content: @Composable () -> Unit) {
        setContent { TvCanvas { content() } }
    }
}
