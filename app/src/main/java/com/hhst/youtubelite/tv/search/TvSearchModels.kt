package com.hhst.youtubelite.tv.search

import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.search.domain.SearchChannel
import com.hhst.youtubelite.search.domain.SearchContent
import com.hhst.youtubelite.search.domain.SearchFilters
import com.hhst.youtubelite.search.domain.SearchPage
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.domain.SearchUiState
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.grid.TvPaging

/** One cell of the TV search results grid. [key] is unique across kinds. */
sealed interface TvSearchItem {
    val key: String

    data class Video(val video: TvVideo, val channelId: String) : TvSearchItem {
        override val key: String get() = "v:${video.id}"
    }

    data class Channel(val id: String, val name: String, val detail: String, val avatar: String) : TvSearchItem {
        override val key: String get() = "c:$id"
    }

    /** A playlist drawn as a card: [card] carries title, owner, artwork and the count badge. */
    data class Playlist(val id: String, val card: TvVideo) : TvSearchItem {
        override val key: String get() = "p:$id"
    }
}

sealed interface TvSearchStatus {
    /** No query submitted yet. */
    data object Idle : TvSearchStatus
    data object Loading : TvSearchStatus
    data object Failed : TvSearchStatus
    data class Empty(val query: String, val type: SearchResultType) : TvSearchStatus
    data object Ready : TvSearchStatus
}

data class TvSearchResults(
    val status: TvSearchStatus,
    val items: List<TvSearchItem> = emptyList(),
    val paging: TvPaging = TvPaging(),
)

/** Everything the state-hoisted [TvSearchScreen] draws. */
data class TvSearchUiState(
    val draft: String = "",
    val query: String = "",
    /** Stored history rows (all of them for a blank draft, matching ones otherwise). */
    val recent: List<String> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val filters: SearchFilters = SearchFilters(),
    val types: List<SearchResultType> = SearchResultType.entries,
    val results: TvSearchResults = TvSearchResults(TvSearchStatus.Idle),
    val voiceAvailable: Boolean = false,
)

/**
 * Maps the phone [SearchUiState] (unchanged [com.hhst.youtubelite.search.presentation.SearchResultsViewModel])
 * onto one TV grid. "All" mixes the page the way the phone list does: leading channels, the first
 * videos, the playlists shelf, then the remaining videos. Shorts only appear under their own filter,
 * so every grid keeps one card shape.
 */
object TvSearchModel {
    const val LEADING_CHANNELS = 3
    const val VIDEOS_BEFORE_PLAYLISTS = 8

    fun video(result: SearchResult, short: Boolean = false): TvVideo = TvVideo(
        id = result.id, title = result.title, channel = result.channel,
        metadata = result.views, published = result.age,
        artwork = result.artwork, duration = result.duration,
        live = result.badge?.trim()?.equals("live", ignoreCase = true) == true,
        progress = result.progress, short = short,
    )

    fun channel(channel: SearchChannel) = TvSearchItem.Channel(channel.id, channel.name,
        listOf(channel.handle, channel.subscribers).filter(String::isNotBlank).joinToString(" · "), channel.artwork)

    fun playlist(playlist: PlaylistSummary) = TvSearchItem.Playlist(playlist.id, TvVideo(
        id = playlist.id, title = playlist.title, channel = playlist.owner, artwork = playlist.artwork,
        duration = playlist.countLabel.orEmpty(),
    ))

    fun types(hideShorts: Boolean): List<SearchResultType> =
        SearchResultType.entries.filterNot { hideShorts && it == SearchResultType.Shorts }

    fun items(page: SearchPage, type: SearchResultType): List<TvSearchItem> {
        val videos = { list: List<SearchResult>, short: Boolean -> list.map { TvSearchItem.Video(video(it, short), it.channelId) } }
        val items = when (type) {
            SearchResultType.All -> {
                val all = videos(page.results, false)
                page.channels.take(LEADING_CHANNELS).map(::channel) +
                    all.take(VIDEOS_BEFORE_PLAYLISTS) + page.playlists.map(::playlist) + all.drop(VIDEOS_BEFORE_PLAYLISTS)
            }
            SearchResultType.Shorts -> videos(page.shorts, true)
            SearchResultType.Channels -> page.channels.map(::channel)
            SearchResultType.Playlists -> page.playlists.map(::playlist)
        }
        return items.distinctBy { it.key }
    }

    fun results(state: SearchUiState): TvSearchResults {
        if (state.query.isBlank()) return TvSearchResults(TvSearchStatus.Idle)
        return when (val content = state.content) {
            SearchContent.Loading -> TvSearchResults(TvSearchStatus.Loading)
            SearchContent.Failed -> TvSearchResults(TvSearchStatus.Failed)
            is SearchContent.Empty -> TvSearchResults(TvSearchStatus.Empty(content.query, state.filters.type))
            is SearchContent.Results -> {
                val items = items(content.page, state.filters.type)
                val paging = TvPaging(
                    hasMore = content.page.nextCursor != null,
                    append = when {
                        content.loadingMore -> AppendState.Loading
                        content.loadMoreFailed -> AppendState.Failed
                        else -> AppendState.Idle
                    },
                    paused = content.automaticPagingPaused,
                )
                if (items.isEmpty() && !paging.hasMore && paging.append == AppendState.Idle) {
                    TvSearchResults(TvSearchStatus.Empty(state.query, state.filters.type))
                } else TvSearchResults(TvSearchStatus.Ready, items, paging)
            }
        }
    }

    /** Video ids of the visible cells plus a short look-ahead, for the ViewModel's metadata fill. */
    fun visibleVideoIds(items: List<TvSearchItem>, first: Int, last: Int, ahead: Int = 3): List<String> {
        if (items.isEmpty() || first > last) return emptyList()
        val from = first.coerceIn(0, items.lastIndex)
        val to = (last + ahead).coerceIn(from, items.lastIndex)
        return items.subList(from, to + 1).filterIsInstance<TvSearchItem.Video>().map { it.video.id }
    }
}
