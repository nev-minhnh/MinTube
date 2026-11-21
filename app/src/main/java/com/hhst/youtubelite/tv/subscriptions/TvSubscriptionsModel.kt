package com.hhst.youtubelite.tv.subscriptions

import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.subscriptions.domain.SubscribedChannel
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsChannels
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsContent
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsUiState
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.home.TvHomeRows
import com.hhst.youtubelite.tv.library.TvGridFooter

sealed interface TvSubscriptionsStatus {
    data object Loading : TvSubscriptionsStatus
    data object SignedOut : TvSubscriptionsStatus
    data object Failed : TvSubscriptionsStatus
    data object Ready : TvSubscriptionsStatus
}

data class TvChannel(val id: String, val name: String, val avatar: String, val hasNew: Boolean = false)

sealed interface TvChannelStrip {
    data object Loading : TvChannelStrip
    data object Failed : TvChannelStrip
    /** [canPage]: the directory has more and no read is in flight. */
    data class Ready(val channels: List<TvChannel>, val canPage: Boolean = false) : TvChannelStrip
}

data class TvSubscriptionsUiState(
    val status: TvSubscriptionsStatus,
    val channels: TvChannelStrip = TvChannelStrip.Loading,
    val videos: List<TvVideo> = emptyList(),
    /** The feed has nothing to show and nothing more to read. */
    val feedEmpty: Boolean = false,
    val footer: TvGridFooter = TvGridFooter.None,
    val canPage: Boolean = false,
)

/** Maps the unchanged [com.hhst.youtubelite.subscriptions.presentation.SubscriptionsViewModel] state onto the TV screen. Pure. */
object TvSubscriptionsModel {
    fun channel(channel: SubscribedChannel) = TvChannel(channel.channelId, channel.name, channel.avatar, channel.hasNewContent)

    fun channels(state: SubscriptionsChannels): TvChannelStrip = when (state) {
        SubscriptionsChannels.Loading -> TvChannelStrip.Loading
        SubscriptionsChannels.Failed -> TvChannelStrip.Failed
        is SubscriptionsChannels.Ready -> TvChannelStrip.Ready(
            state.channels.filter { it.channelId.isNotBlank() }.distinctBy { it.channelId }.map(::channel),
            canPage = state.nextKey != null && state.append == AppendState.Idle,
        )
    }

    fun build(state: SubscriptionsUiState): TvSubscriptionsUiState {
        val feed = when (val content = state.content) {
            SubscriptionsContent.Loading -> return TvSubscriptionsUiState(TvSubscriptionsStatus.Loading)
            is SubscriptionsContent.SignedOut -> return TvSubscriptionsUiState(TvSubscriptionsStatus.SignedOut)
            SubscriptionsContent.Failed -> return TvSubscriptionsUiState(TvSubscriptionsStatus.Failed, channels(state.channels))
            is SubscriptionsContent.Feed -> content
        }
        // Shorts in the feed play as normal videos in a uniform 16:9 grid.
        val videos = feed.page.items.filter { it.id.isNotBlank() }.distinctBy { it.id }.map { TvHomeRows.video(it, short = false) }
        val hasMore = feed.page.nextKey != null
        val footer = when {
            feed.append == AppendState.Loading -> TvGridFooter.Loading
            feed.append == AppendState.Failed -> TvGridFooter.Retry
            // Automatic reads past empty filtered pages stopped: the user asks for the next page.
            hasMore && videos.isEmpty() -> TvGridFooter.LoadMore
            else -> TvGridFooter.None
        }
        return TvSubscriptionsUiState(
            status = TvSubscriptionsStatus.Ready,
            channels = channels(state.channels),
            videos = videos,
            feedEmpty = videos.isEmpty() && !hasMore && feed.append != AppendState.Loading,
            footer = footer,
            canPage = hasMore && feed.append == AppendState.Idle && videos.isNotEmpty(),
        )
    }
}
