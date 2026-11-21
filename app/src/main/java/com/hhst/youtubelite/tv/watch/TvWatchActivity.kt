package com.hhst.youtubelite.tv.watch

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.watch.presentation.WatchPlayer
import org.koin.android.ext.android.inject

/** Fullscreen TV playback on the shared player engine. */
class TvWatchActivity : TvActivity() {
    private val playerViewModel: PlayerViewModel by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID).orEmpty()
        (application as com.hhst.youtubelite.App).startMediaRuntime()
        playerViewModel.openVideo(videoId, if (savedInstanceState == null) intent.startSeconds() else null)
        setTvContent {
            BackHandler { finish() }
            WatchPlayer(Modifier.fillMaxSize(), onBack = ::finish, pip = true, showPipLoadingIndicator = false)
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "tv_watch_video_id"
        const val EXTRA_START_SECONDS = "tv_watch_start_seconds"

        fun intent(context: Context, videoId: String, startSeconds: Long? = null): Intent =
            Intent(context, TvWatchActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .apply { startSeconds?.let { putExtra(EXTRA_START_SECONDS, it) } }

        internal fun Intent.startSeconds(): Long? =
            if (hasExtra(EXTRA_START_SECONDS)) getLongExtra(EXTRA_START_SECONDS, 0).takeIf { it >= 0 } else null
    }
}
