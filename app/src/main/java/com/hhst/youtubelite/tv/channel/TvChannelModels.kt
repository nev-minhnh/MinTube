package com.hhst.youtubelite.tv.channel

import com.hhst.youtubelite.channel.domain.ChannelContent
import com.hhst.youtubelite.channel.domain.ChannelLiveVideo
import com.hhst.youtubelite.channel.domain.ChannelProfile
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelShort
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.domain.ChannelUiState
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.grid.TvPaging

/** What one channel tab grid shows. Cards open by [TvChannelCard.kind]. */
sealed interface TvChannelGrid {
    data object Loading : TvChannelGrid
    data object Failed : TvChannelGrid
    data object Empty : TvChannelGrid
    data class Ready(val cards: List<TvChannelCard>, val paging: TvPaging = TvPaging()) : TvChannelGrid
}

enum class TvChannelCardKind { Video, Short, Live, Playlist }

data class TvChannelCard(val kind: TvChannelCardKind, val video: TvVideo) {
    val key: String get() = "${kind.name}:${video.id}"
}

data class TvChannelHeader(
    val name: String,
    val handle: String,
    val subscribers: String,
    val avatar: String,
    val banner: String?,
    /** Null when the page does not say (signed out, modern header). */
    val subscribed: Boolean?,
    val subscribePending: Boolean,
) {
    val subtitle: String get() = listOf(handle, subscribers).filter(String::isNotBlank).joinToString(" · ")
}

sealed interface TvChannelStatus {
    data object Loading : TvChannelStatus
    data object Failed : TvChannelStatus
    data class Ready(val header: TvChannelHeader, val tabs: List<ChannelTab>, val selected: ChannelTab, val grid: TvChannelGrid) :
        TvChannelStatus
}

/**
 * Maps the unchanged phone [ChannelUiState] onto the TV page. Tabs are the grid tabs the TV
 * renders: Videos always, Shorts unless hidden, Live, and Playlists unless the landing read
 * already proved there are none. Home (shelves), Posts and in-channel Search stay phone-only.
 */
object TvChannelModel {
    val GridTabs = listOf(ChannelTab.Videos, ChannelTab.Shorts, ChannelTab.Live, ChannelTab.Playlists)

    fun tabs(state: ChannelUiState, hideShorts: Boolean): List<ChannelTab> = GridTabs.filter { tab ->
        when (tab) {
            ChannelTab.Shorts -> !hideShorts
            ChannelTab.Playlists -> state.playlists != ChannelSection.Empty
            else -> true
        }
    }

    /** The tab the TV shows: the ViewModel's tab when the TV renders it, else Videos. */
    fun selected(state: ChannelUiState, tabs: List<ChannelTab>): ChannelTab =
        state.selectedTab.takeIf { it in tabs } ?: ChannelTab.Videos

    fun video(video: ChannelVideo, channel: String) = TvVideo(
        id = video.id, title = video.title, channel = channel,
        metadata = video.views, published = video.age,
        artwork = video.artwork, duration = video.duration, live = video.live,
    )

    fun short(short: ChannelShort, channel: String = "") = TvVideo(id = short.id, title = short.title, channel = channel,
        metadata = short.views, artwork = short.artwork, short = true)

    fun live(video: ChannelLiveVideo, channel: String = "") = TvVideo(
        id = video.id, title = video.title, channel = channel,
        metadata = video.views, published = video.time,
        artwork = video.artwork, duration = video.duration, live = video.liveNow,
    )

    fun playlist(playlist: PlaylistSummary) = TvVideo(id = playlist.id, title = playlist.title, channel = playlist.owner,
        artwork = playlist.artwork, duration = playlist.countLabel.orEmpty())

    fun header(profile: ChannelProfile, pending: Boolean) = TvChannelHeader(profile.name, profile.handle, profile.subscribers,
        profile.avatar, profile.banner?.takeIf(String::isNotBlank), profile.subscribed, pending)

    private fun paging(nextKey: String?, append: AppendState) = TvPaging(hasMore = nextKey != null, append = append)

    private fun <T> section(section: ChannelSection<T>, ready: (T) -> TvChannelGrid): TvChannelGrid = when (section) {
        ChannelSection.Loading -> TvChannelGrid.Loading
        ChannelSection.Failed -> TvChannelGrid.Failed
        ChannelSection.Empty -> TvChannelGrid.Empty
        is ChannelSection.Ready -> ready(section.value)
    }

    private fun ready(cards: List<TvChannelCard>, paging: TvPaging): TvChannelGrid =
        if (cards.isEmpty() && !paging.hasMore && paging.append == AppendState.Idle) TvChannelGrid.Empty
        else TvChannelGrid.Ready(cards.distinctBy { it.key }, paging)

    fun grid(state: ChannelUiState, tab: ChannelTab): TvChannelGrid {
        val page = (state.content as? ChannelContent.Ready)?.page ?: return TvChannelGrid.Loading
        return when (tab) {
            ChannelTab.Shorts -> section(state.shorts) { shorts ->
                ready(shorts.map { TvChannelCard(TvChannelCardKind.Short, short(it, page.profile.name)) }, paging(state.shortsNextKey, state.shortsAppend))
            }
            ChannelTab.Live -> section(state.live) { live ->
                ready(live.all.map { TvChannelCard(TvChannelCardKind.Live, live(it, page.profile.name)) }, paging(state.liveNextKey, state.liveAppend))
            }
            ChannelTab.Playlists -> section(state.playlists) { playlists ->
                ready(playlists.map { TvChannelCard(TvChannelCardKind.Playlist, playlist(it)) },
                    paging(state.playlistsNextKey, state.playlistsAppend))
            }
            else -> section(state.videosTab) {
                ready(page.videos.map { TvChannelCard(TvChannelCardKind.Video, video(it, page.profile.name)) },
                    paging(page.videosNextKey, state.videosAppend))
            }
        }
    }

    fun status(state: ChannelUiState, hideShorts: Boolean): TvChannelStatus = when (val content = state.content) {
        ChannelContent.Loading -> TvChannelStatus.Loading
        ChannelContent.Failed -> TvChannelStatus.Failed
        is ChannelContent.Ready -> {
            val tabs = tabs(state, hideShorts)
            val selected = selected(state, tabs)
            TvChannelStatus.Ready(header(content.page.profile, state.subscribePending), tabs, selected, grid(state, selected))
        }
    }
}
