package com.hhst.youtubelite.tv.playlist

import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEnumeration
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.grid.TvPaging

data class TvPlaylistHeader(
    val title: String,
    val owner: String,
    val channelId: String,
    val count: String,
    val artwork: String,
)

data class TvPlaylistRow(val entryId: String, val video: TvVideo)

sealed interface TvPlaylistStatus {
    data object Loading : TvPlaylistStatus
    data object Failed : TvPlaylistStatus
    data object Unavailable : TvPlaylistStatus
    data class Ready(
        val header: TvPlaylistHeader,
        val rows: List<TvPlaylistRow>,
        val paging: TvPaging = TvPaging(),
        /** Entries loaded so far while Play all / Shuffle completes the list; null when idle. */
        val enumerating: Int? = null,
    ) : TvPlaylistStatus
}

/** Maps the unchanged phone [PlaylistUiState] onto the TV page, and playlist entries onto the player queue. */
object TvPlaylistModel {
    fun video(entry: PlaylistEntry) = TvVideo(
        id = entry.videoId, title = entry.title, channel = entry.channel,
        metadata = listOf(entry.views, entry.age).filter(String::isNotBlank).joinToString(" · "),
        artwork = entry.artwork, duration = entry.duration,
    )

    fun status(state: PlaylistUiState): TvPlaylistStatus = when (val content = state.content) {
        PlaylistContent.Loading -> TvPlaylistStatus.Loading
        PlaylistContent.Failed -> TvPlaylistStatus.Failed
        PlaylistContent.Unavailable -> TvPlaylistStatus.Unavailable
        is PlaylistContent.Ready -> {
            val summary = content.page.summary
            TvPlaylistStatus.Ready(
                header = TvPlaylistHeader(summary.title, summary.owner, summary.channelId, summary.countLabel.orEmpty(),
                    summary.artwork.ifBlank { content.page.entries.firstOrNull()?.artwork.orEmpty() }),
                rows = content.page.entries.distinctBy { it.id }.map { TvPlaylistRow(it.id, video(it)) },
                // A failed append is retried from the footer; the ViewModel never pauses playlist paging.
                paging = TvPaging(hasMore = content.page.nextKey != null, append = state.append),
                enumerating = (state.enumeration as? PlaylistEnumeration.Running)?.loaded,
            )
        }
    }

    /** The playback queue for the page: entries in order, unplayable ids dropped by the player. */
    fun queue(entries: List<PlaylistEntry>): List<QueueItem> =
        entries.distinctBy { it.videoId }.map { QueueItem(it.videoId, title = it.title, author = it.channel, thumbnailUrl = it.artwork) }

    /** "Play all"/"Shuffle" hand over the full id list; keep the entry metadata for those ids. */
    fun queue(entries: List<PlaylistEntry>, videoIds: List<String>, shuffle: Boolean): List<QueueItem> {
        val byId = entries.associateBy { it.videoId }
        val ordered = videoIds.distinct().map { id -> byId[id]?.let { queue(listOf(it)).single() } ?: QueueItem(id) }
        return if (shuffle) ordered.shuffled() else ordered
    }
}
