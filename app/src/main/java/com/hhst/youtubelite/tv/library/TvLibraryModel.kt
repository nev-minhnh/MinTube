package com.hhst.youtubelite.tv.library

import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.library.domain.CollectionContent
import com.hhst.youtubelite.library.domain.CollectionUiState
import com.hhst.youtubelite.library.domain.HistoryContent
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.library.domain.HistoryUiState
import com.hhst.youtubelite.library.domain.LibraryContent
import com.hhst.youtubelite.library.domain.LibraryUiState
import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.tv.design.TvVideo

/** Library rows in display order (phone You page order); Liked is account-only, Queue only when it has items. */
enum class TvLibraryShelf(val key: String) {
    History("history"), WatchLater("watch-later"), Playlists("playlists"), Liked("liked"), Queue("queue");

    companion object {
        fun of(key: String): TvLibraryShelf? = entries.firstOrNull { it.key == key }
    }
}

sealed interface TvLibraryStatus {
    data object Loading : TvLibraryStatus
    data object SignedOut : TvLibraryStatus
    data object Failed : TvLibraryStatus
    data object Ready : TvLibraryStatus
}

data class TvLibraryUiState(val status: TvLibraryStatus, val shelves: List<TvShelf> = emptyList())

/** Status of a full-screen library page. */
enum class TvPageStatus { Loading, SignedOut, Failed, Empty, Ready }

data class TvPageState(
    val status: TvPageStatus,
    val items: List<TvVideo> = emptyList(),
    val footer: TvGridFooter = TvGridFooter.None,
    val canPage: Boolean = false,
)

/**
 * Maps the unchanged phone library ViewModels onto TV rows and pages. Pure: unit-tested.
 * Videos keep their video id as card key; a playlist card's id is the playlist id.
 */
object TvLibraryModel {
    /** Cards per row before "View all". */
    const val ROW_MAX = 20

    fun video(entry: HistoryEntry) = TvVideo(
        id = entry.videoId, title = entry.title, channel = entry.author, metadata = entry.views,
        published = entry.published, artwork = entry.artwork, duration = entry.duration, progress = entry.progress,
    )

    fun video(entry: PlaylistEntry) = TvVideo(
        id = entry.videoId, title = entry.title, channel = entry.channel,
        metadata = entry.views, published = entry.age,
        artwork = entry.artwork, duration = entry.duration,
    )

    fun playlist(summary: PlaylistSummary) = TvVideo(
        id = summary.id, title = summary.title, channel = summary.owner, metadata = summary.countLabel.orEmpty(),
        artwork = summary.artwork,
    )

    fun video(item: QueueItem): TvVideo? {
        val id = item.videoId.ifBlank { com.hhst.youtubelite.extractor.VideoId.parse(item.url).orEmpty() }
        if (id.isBlank()) return null
        return TvVideo(id = id, title = item.title, channel = item.author.orEmpty(), artwork = item.thumbnailUrl.orEmpty())
    }

    private fun ready(videos: List<TvVideo>): TvShelfState {
        val distinct = videos.filter { it.id.isNotBlank() }.distinctBy(TvVideo::id)
        return if (distinct.isEmpty()) TvShelfState.Empty() else TvShelfState.Ready(distinct.take(ROW_MAX))
    }

    fun playlistShelf(state: PlaylistUiState?): TvShelfState = when (val content = state?.content) {
        null, PlaylistContent.Loading -> TvShelfState.Loading
        PlaylistContent.Failed, PlaylistContent.Unavailable -> TvShelfState.Failed
        is PlaylistContent.Ready -> ready(content.page.entries.map(::video))
    }

    /**
     * [signedIn] adds Liked (no device lane on phone either). [title] resolves a row's label.
     * A failed optional shelf keeps its own Retry; the page fails only when the You read fails.
     */
    fun build(
        you: LibraryUiState,
        watchLater: PlaylistUiState?,
        liked: PlaylistUiState?,
        queue: List<QueueItem>,
        signedIn: Boolean,
        title: (TvLibraryShelf) -> String,
    ): TvLibraryUiState {
        val page = when (val content = you.content) {
            LibraryContent.Loading -> return TvLibraryUiState(TvLibraryStatus.Loading)
            is LibraryContent.SignedOut -> return TvLibraryUiState(TvLibraryStatus.SignedOut)
            LibraryContent.Failed -> return TvLibraryUiState(TvLibraryStatus.Failed)
            is LibraryContent.Ready -> content.page
        }
        val shelves = buildList {
            fun add(shelf: TvLibraryShelf, state: TvShelfState) = add(TvShelf(shelf.key, title(shelf), state))
            add(TvLibraryShelf.History, when {
                you.historyRetrying -> TvShelfState.Loading
                page.historyFailed -> TvShelfState.Failed
                else -> ready(page.history.map(::video))
            })
            add(TvLibraryShelf.WatchLater, playlistShelf(watchLater))
            add(TvLibraryShelf.Playlists, when {
                you.playlistsRetrying -> TvShelfState.Loading
                page.playlistsFailed -> TvShelfState.Failed
                else -> ready(page.playlists.map(::playlist))
            })
            if (signedIn) add(TvLibraryShelf.Liked, playlistShelf(liked))
            val queued = queue.mapNotNull(::video)
            if (queued.isNotEmpty()) add(TvLibraryShelf.Queue, ready(queued))
        }
        return TvLibraryUiState(TvLibraryStatus.Ready, shelves)
    }

    private fun footer(append: AppendState) = when (append) {
        AppendState.Loading -> TvGridFooter.Loading
        AppendState.Failed -> TvGridFooter.Retry
        AppendState.Idle -> TvGridFooter.None
    }

    private fun page(items: List<TvVideo>, append: AppendState, hasMore: Boolean): TvPageState {
        val distinct = items.filter { it.id.isNotBlank() }.distinctBy(TvVideo::id)
        if (distinct.isEmpty() && !hasMore && append != AppendState.Loading) return TvPageState(TvPageStatus.Empty)
        // A continuation with nothing shown yet would leave no focusable card: offer Load more.
        val footer = footer(append).takeIf { it != TvGridFooter.None }
            ?: if (distinct.isEmpty() && hasMore) TvGridFooter.LoadMore else TvGridFooter.None
        return TvPageState(TvPageStatus.Ready, distinct, footer, hasMore && append == AppendState.Idle)
    }

    fun history(state: HistoryUiState): TvPageState = when (val content = state.content) {
        HistoryContent.Loading -> TvPageState(TvPageStatus.Loading)
        is HistoryContent.SignedOut -> TvPageState(TvPageStatus.SignedOut)
        HistoryContent.Failed -> TvPageState(TvPageStatus.Failed)
        is HistoryContent.Empty -> TvPageState(TvPageStatus.Empty)
        is HistoryContent.Ready -> page(content.page.entries.map(::video), state.append, content.page.nextKey != null)
    }

    fun playlists(state: CollectionUiState): TvPageState = when (val content = state.content) {
        CollectionContent.Loading -> TvPageState(TvPageStatus.Loading)
        is CollectionContent.SignedOut -> TvPageState(TvPageStatus.SignedOut)
        CollectionContent.Failed -> TvPageState(TvPageStatus.Failed)
        is CollectionContent.Empty -> TvPageState(TvPageStatus.Empty)
        is CollectionContent.Ready -> page(content.playlists.map(::playlist), state.append, content.nextKey != null)
    }

    fun playlist(state: PlaylistUiState): TvPageState = when (val content = state.content) {
        PlaylistContent.Loading -> TvPageState(TvPageStatus.Loading)
        PlaylistContent.Failed, PlaylistContent.Unavailable -> TvPageState(TvPageStatus.Failed)
        is PlaylistContent.Ready -> page(content.page.entries.map(::video), state.append, content.page.nextKey != null)
    }

    fun queue(items: List<QueueItem>): TvPageState = page(items.mapNotNull(::video), AppendState.Idle, hasMore = false)
}
