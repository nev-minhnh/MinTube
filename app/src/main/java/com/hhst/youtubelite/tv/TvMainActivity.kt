package com.hhst.youtubelite.tv

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import com.hhst.youtubelite.tv.home.TvHomeController
import com.hhst.youtubelite.tv.search.TvSearchController
import com.hhst.youtubelite.tv.shell.TvComingSoon
import com.hhst.youtubelite.tv.shell.TvDestination
import com.hhst.youtubelite.tv.shell.TvShell
import com.hhst.youtubelite.tv.shell.TvShellState
import com.hhst.youtubelite.tv.shell.rememberTvShellState

/**
 * LEANBACK_LAUNCHER entry of MinTube TV: the shell (rail + content) hosting the top-level
 * destinations. A destination plugs in by adding its composable to [Destination]; full-screen
 * pages (watch, channel, playlist) are their own [TvActivity].
 */
class TvMainActivity : TvActivity() {
    private lateinit var home: TvHomeController
    private lateinit var search: TvSearchController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = TvHomeController(this)
        search = TvSearchController(this)
        setTvContent {
            val shell = rememberTvShellState()
            TvShell(shell, onExit = ::finish) { destination -> Destination(destination, shell) }
        }
    }

    @Composable
    private fun Destination(destination: TvDestination, shell: TvShellState) {
        when (destination) {
            TvDestination.Home -> home.Content()
            TvDestination.Search -> search.Content()
            TvDestination.Subscriptions,
            TvDestination.Library, TvDestination.Settings ->
                TvComingSoon(destination, onHome = { shell.select(TvDestination.Home) })
        }
    }

    override fun onResume() {
        super.onResume()
        if (::home.isInitialized) home.onResume()
        if (::search.isInitialized) search.onResume()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, TvMainActivity::class.java)
    }
}
