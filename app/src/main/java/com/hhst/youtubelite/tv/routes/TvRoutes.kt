package com.hhst.youtubelite.tv.routes

import android.content.Context
import android.content.Intent

/*
 * Entry points into TV screens other agents own. Each factory opens [TvPlaceholderActivity] on this
 * branch; at merge time the coordinator replaces the body with the real Activity's intent
 * (TV-A: channel page and playlist detail; TV-C: sign-in). Callers never change.
 */

/** TV channel page (TV-A). */
object TvChannelRoute {
    fun intent(context: Context, channelId: String): Intent =
        TvPlaceholderActivity.intent(context, TvPlaceholderActivity.Kind.Channel, channelId)
}

/** TV playlist detail (TV-A). Also used for "WL" and "LL" ids. */
object TvPlaylistRoute {
    fun intent(context: Context, playlistId: String): Intent =
        TvPlaceholderActivity.intent(context, TvPlaceholderActivity.Kind.Playlist, playlistId)
}

/** TV sign-in, device code (TV-C). */
object TvSignInRoute {
    fun intent(context: Context): Intent =
        TvPlaceholderActivity.intent(context, TvPlaceholderActivity.Kind.SignIn, null)
}
