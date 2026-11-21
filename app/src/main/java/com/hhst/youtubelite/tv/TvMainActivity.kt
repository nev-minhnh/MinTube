package com.hhst.youtubelite.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.hhst.youtubelite.tv.home.TvHomeController
import com.hhst.youtubelite.tv.library.TvLibraryController
import com.hhst.youtubelite.tv.search.TvSearchController
import com.hhst.youtubelite.tv.settings.TvSettingsController
import com.hhst.youtubelite.tv.shell.TvDestination
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.rememberTvShellState
import com.hhst.youtubelite.tv.subscriptions.TvSubscriptionsController

/**
 * LEANBACK_LAUNCHER entry of MinTube TV: the shell (rail + content) hosting the top-level
 * destinations. A destination plugs in by adding its composable to [Destination]; full-screen
 * pages (watch, channel, playlist) are their own [TvActivity].
 */
class TvMainActivity : TvActivity() {
    private lateinit var home: TvHomeController
    private lateinit var search: TvSearchController
    private lateinit var settings: TvSettingsController
    // Created on first visit: their ViewModels start reading when built.
    private var subscriptions: TvSubscriptionsController? = null
    private var library: TvLibraryController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = TvHomeController(this)
        search = TvSearchController(this)
        settings = TvSettingsController(this)
        setTvContent {
            val shell = rememberTvShellState()
            TvShell(shell, onExit = ::finish) { destination -> Destination(destination) }
        }
    }

    @Composable
    private fun Destination(destination: TvDestination) {
        when (destination) {
            TvDestination.Home -> home.Content()
            TvDestination.Search -> search.Content()
            TvDestination.Subscriptions -> (subscriptions ?: TvSubscriptionsController(this).also { subscriptions = it }).Content()
            TvDestination.Library -> (library ?: TvLibraryController(this).also { library = it }).Content()
            TvDestination.Settings -> settings.Content()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::home.isInitialized) home.onResume()
        if (::search.isInitialized) search.onResume()
        if (::settings.isInitialized) settings.onResume()
        subscriptions?.onResume()
        library?.onResume()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, TvMainActivity::class.java)
    }
}
