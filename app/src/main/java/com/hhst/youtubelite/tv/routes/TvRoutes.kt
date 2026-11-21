package com.hhst.youtubelite.tv.routes

import android.content.Context
import android.content.Intent
import com.hhst.youtubelite.tv.account.TvSignInActivity
import com.hhst.youtubelite.tv.channel.TvChannelActivity
import com.hhst.youtubelite.tv.playlist.TvPlaylistActivity

/* Entry points into full-screen TV pages, so callers never depend on the Activity classes. */

/** TV channel page. */
object TvChannelRoute {
    fun intent(context: Context, channelId: String): Intent = TvChannelActivity.intent(context, channelId)
}

/** TV playlist detail. Also used for "WL" and "LL" ids. */
object TvPlaylistRoute {
    fun intent(context: Context, playlistId: String): Intent = TvPlaylistActivity.intent(context, playlistId)
}

/** TV device-code sign-in. Every signed-out TV action goes here, never to the phone SignInActivity. */
object TvSignInRoute {
    fun intent(context: Context): Intent = Intent(context, TvSignInActivity::class.java)
}
