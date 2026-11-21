package com.hhst.youtubelite.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.hhst.youtubelite.downloader.core.DownloadShareParser
import com.hhst.youtubelite.navigation.ContentLink
import com.hhst.youtubelite.tv.watch.TvWatchActivity

/** Where an external YouTube link opens on TV. */
sealed interface TvRoute {
    data class Watch(val videoId: String, val startSeconds: Long? = null) : TvRoute
    /** Destinations without a TV screen yet land on Home. */
    data object Home : TvRoute

    companion object {
        fun of(link: ContentLink?): TvRoute = when (link) {
            is ContentLink.Video -> Watch(link.id, link.seconds)
            // A single short plays in the TV player until the TV Shorts screen exists.
            is ContentLink.Short -> link.id?.let { Watch(it) } ?: Home
            else -> Home
        }
    }
}

/**
 * Exported VIEW/SEND entry for youtube.com and youtu.be links. It replaces the phone
 * ContentLinkActivity filters on this branch so links open the TV watch screen.
 */
class TvLinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND) {
            DownloadShareParser.extractUrl(intent.dataString ?: intent.getStringExtra(Intent.EXTRA_TEXT))
        } else null
        when (val route = TvRoute.of(url?.let(ContentLink::parse))) {
            is TvRoute.Watch -> {
                val watch = TvWatchActivity.intent(this, route.videoId, route.startSeconds)
                // Back from a link opened over another app returns to TV Home, not out of the app.
                if (isTaskRoot) startActivities(arrayOf(TvMainActivity.intent(this), watch)) else startActivity(watch)
            }
            TvRoute.Home -> startActivity(TvMainActivity.intent(this))
        }
        finish()
    }

    companion object {
        fun intent(context: Context, url: String): Intent =
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).setClass(context, TvLinkActivity::class.java)
    }
}
