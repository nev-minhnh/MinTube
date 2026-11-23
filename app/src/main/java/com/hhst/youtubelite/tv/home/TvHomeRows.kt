package com.hhst.youtubelite.tv.home

import androidx.annotation.StringRes
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.HomeCatalog
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.home.domain.HomeUiState
import com.hhst.youtubelite.tv.design.TvVideo

/** A row title: server text (a chip name) or an app string. */
sealed interface TvRowTitle {
    data class Text(val text: String) : TvRowTitle
    data class Resource(@param:StringRes val id: Int) : TvRowTitle
}

enum class TvRowKind { Main, Shorts, Chip }

sealed interface TvRowState {
    data object Loading : TvRowState
    data object Failed : TvRowState
    data object Empty : TvRowState
    data class Ready(
        val videos: List<TvVideo>,
        val append: AppendState = AppendState.Idle,
        /** A continuation exists: the row can page further. */
        val hasMore: Boolean = false,
        /** Automatic paging stopped after empty pages; the row offers "Load more". */
        val pagingPaused: Boolean = false,
    ) : TvRowState
}

data class TvHomeRow(
    val key: String,
    val kind: TvRowKind,
    val title: TvRowTitle,
    val state: TvRowState,
    /** The Home chip the row reads (null for the main feed and its Shorts). */
    val chip: String? = null,
)

sealed interface TvHomeStatus {
    data object Loading : TvHomeStatus
    data object Failed : TvHomeStatus
    /** Blank strings fall back to the localized empty-Home copy. */
    data class Empty(val title: String, val message: String) : TvHomeStatus
    data object Ready : TvHomeStatus
}

data class TvHomeUiState(val status: TvHomeStatus, val rows: List<TvHomeRow> = emptyList())

/**
 * Maps the phone Home ViewModels onto TV rows, without changing them: the main feed (the "All"
 * chip, or Trending when signed out) is the first row, its Shorts shelves are the Shorts row, and
 * every other Home chip becomes a row read by its own HomeViewModel.
 */
object TvHomeRows {
    const val MAIN = "main"
    const val SHORTS = "shorts"
    fun chipKey(chip: String) = "chip:$chip"

    fun video(item: FeedItem.Video, short: Boolean = item.short): TvVideo = TvVideo(
        // The phone card shows author · metadata (views and age already joined by the data layer).
        id = item.id, title = item.title, channel = item.author, metadata = item.metadata,
        artwork = item.artwork, duration = item.duration, live = item.live, progress = item.progress, short = short,
        channelId = item.channelId, published = item.age.orEmpty(),
    )

    fun videos(page: HomePage): List<TvVideo> = page.items.filterIsInstance<FeedItem.Video>()
        .filterNot { it.short }.distinctBy { it.id }.map { video(it) }

    fun shorts(page: HomePage): List<TvVideo> = page.items.flatMap { item ->
        when (item) {
            is FeedItem.Shorts -> item.videos
            is FeedItem.Video -> if (item.short) listOf(item) else emptyList()
            else -> emptyList()
        }
    }.distinctBy { it.id }.map { video(it, short = true) }

    fun rowState(content: HomeContent?, pick: (HomePage) -> List<TvVideo>): TvRowState = when (content) {
        null, HomeContent.Loading -> TvRowState.Loading
        HomeContent.Failed -> TvRowState.Failed
        is HomeContent.Empty -> if (content.refreshing) TvRowState.Loading else TvRowState.Empty
        is HomeContent.Feed -> {
            val videos = pick(content.page)
            if (videos.isEmpty() && content.page.nextKey == null && content.append != AppendState.Loading) TvRowState.Empty
            else TvRowState.Ready(videos, content.append, content.page.nextKey != null, content.automaticPagingPaused)
        }
    }

    /** Signed-out Home is the discovery catalog: Trending first, then its charts. */
    fun catalog(main: HomeUiState): Boolean =
        (main.content as? HomeContent.Feed)?.page?.catalog != null || main.chips == HomeCatalog.filters

    /** Chips other than the main feed's, each one row. */
    fun chips(main: HomeUiState): List<String> = main.chips.filter { it != main.selectedChip }.distinct()

    fun mainTitle(main: HomeUiState, trendingInVietnam: Boolean): TvRowTitle =
        if (catalog(main)) trendingTitle(trendingInVietnam) else TvRowTitle.Resource(R.string.tv_row_recommended)

    private fun trendingTitle(trendingInVietnam: Boolean) =
        TvRowTitle.Resource(if (trendingInVietnam) R.string.locale_trending_vietnam else R.string.tv_row_trending)

    fun chipTitle(chip: String, catalog: Boolean, trendingInVietnam: Boolean): TvRowTitle =
        if (!catalog) TvRowTitle.Text(chip) else when (HomeCatalog.forFilter(chip)) {
            HomeCatalog.Popular -> trendingTitle(trendingInVietnam)
            HomeCatalog.MusicCharts -> TvRowTitle.Resource(R.string.home_music_charts)
            HomeCatalog.Gaming -> TvRowTitle.Resource(R.string.home_popular_gaming)
            HomeCatalog.Sports -> TvRowTitle.Resource(R.string.home_popular_sports)
            HomeCatalog.News -> TvRowTitle.Resource(R.string.home_top_news)
            null -> TvRowTitle.Text(chip)
        }

    /**
     * [chipStates] holds the state of every chip row created so far (absent = not composed yet,
     * shown as Loading). Chip rows that came back empty are dropped; failed rows keep a Retry.
     */
    fun build(
        main: HomeUiState,
        chipStates: Map<String, HomeUiState>,
        trendingInVietnam: Boolean,
    ): TvHomeUiState {
        val status = when (val content = main.content) {
            HomeContent.Loading -> TvHomeStatus.Loading
            HomeContent.Failed -> TvHomeStatus.Failed
            is HomeContent.Empty -> if (content.refreshing) TvHomeStatus.Loading else TvHomeStatus.Empty(content.title, content.message)
            is HomeContent.Feed -> TvHomeStatus.Ready
        }
        if (status != TvHomeStatus.Ready) return TvHomeUiState(status)
        val catalog = catalog(main)
        val rows = buildList {
            add(TvHomeRow(MAIN, TvRowKind.Main, mainTitle(main, trendingInVietnam), rowState(main.content, ::videos)))
            if (!main.hideShorts) {
                val shorts = rowState(main.content, ::shorts)
                // The Shorts row pages with the main feed; it only appears once a shelf arrived.
                if (shorts is TvRowState.Ready && shorts.videos.isNotEmpty()) {
                    add(TvHomeRow(SHORTS, TvRowKind.Shorts, TvRowTitle.Resource(R.string.tv_row_shorts),
                        shorts.copy(hasMore = false, pagingPaused = false)))
                }
            }
            chips(main).forEach { chip ->
                val state = rowState(chipStates[chip]?.content, ::videos)
                if (state != TvRowState.Empty) {
                    add(TvHomeRow(chipKey(chip), TvRowKind.Chip, chipTitle(chip, catalog, trendingInVietnam), state, chip))
                }
            }
        }
        return TvHomeUiState(status, rows)
    }
}
