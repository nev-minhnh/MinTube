package com.hhst.youtubelite.tv.routes

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * Stand-in for a TV screen another agent builds (see [TvChannelRoute], [TvPlaylistRoute],
 * [TvSignInRoute]). One focused "Go back" action; Back leaves as well.
 */
class TvPlaceholderActivity : TvActivity() {
    enum class Kind { Channel, Playlist, SignIn }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = Kind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: Kind.Channel
        setTvContent {
            val label = stringResource(when (kind) {
                Kind.Channel -> R.string.tv_route_channel
                Kind.Playlist -> R.string.ui_playlist
                Kind.SignIn -> R.string.ui_sign_in
            })
            Box(Modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical),
                contentAlignment = Alignment.Center) {
                TvStateMessage(
                    title = stringResource(R.string.tv_coming_soon_title, label),
                    message = stringResource(R.string.tv_coming_soon_message),
                    icon = when (kind) {
                        Kind.Channel -> AppIcon.Person
                        Kind.Playlist -> AppIcon.Playlist
                        Kind.SignIn -> AppIcon.Person
                    },
                    actionLabel = stringResource(R.string.tv_go_back),
                    onAction = ::finish,
                    actionModifier = Modifier.testTag(TAG_PLACEHOLDER_BACK),
                    requestFocus = true,
                )
            }
        }
    }

    companion object {
        const val EXTRA_KIND = "tv_placeholder_kind"
        const val EXTRA_ID = "tv_placeholder_id"
        const val TAG_PLACEHOLDER_BACK = "tv-placeholder-back"

        fun intent(context: Context, kind: Kind, id: String?): Intent =
            Intent(context, TvPlaceholderActivity::class.java).putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_ID, id)
    }
}
