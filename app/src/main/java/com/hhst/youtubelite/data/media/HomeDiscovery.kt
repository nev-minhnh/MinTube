package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.HomePage
import com.hhst.youtubelite.home.domain.HomeCatalog
import com.liskovsoft.youtubeapi.videoinfo.V2.BrowsePage
import com.liskovsoft.youtubeapi.videoinfo.V2.HomeSection
import com.liskovsoft.youtubeapi.videoinfo.V2.MediaGroup
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestError
import com.liskovsoft.youtubeapi.videoinfo.V2.RequestFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [read] uses the session's content language. Directory shelves carry no stable id, only
 * a localized title, so they are found by their English or Vietnamese title; for any other
 * language, or when YouTube renames a shelf, that source falls back to [englishRead]
 * (`hl=en`) and keeps English for its continuations.
 */
internal class HomeDiscovery(
    private val englishRead: (suspend (HomeSection, String?, Long) -> BrowsePage)? = null,
    private val read: suspend (HomeSection, String?, Long) -> BrowsePage,
) {
    @Serializable
    private data class Pending(val kind: HomeCatalog, val key: String? = null, val english: Boolean = false)
    @Serializable
    private data class Cursor(val generation: Long, val catalog: HomeCatalog, val sources: List<Pending>)
    private data class Source(val pending: Pending, val group: MediaGroup?, val failure: Exception? = null)

    suspend fun load(generation: Long, catalog: HomeCatalog = HomeCatalog.Popular, continuation: String? = null): HomePage = coroutineScope {
        val pending = if (continuation == null) sources(catalog).map(::Pending)
        else decode(continuation, generation, catalog).sources
        val results = pending.map { source -> async {
            try {
                loadSource(source, generation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Source(source, null, error)
            }
        } }.map { it.await() }
        if (results.all { it.failure != null }) throw requireNotNull(results.first().failure)

        val groups = results.map { source ->
            source.group?.items.orEmpty().mapNotNull { item ->
                when {
                    source.pending.kind == HomeCatalog.MusicCharts && item.kind == "playlist" && !item.title.isNullOrBlank() ->
                        FeedItem.Playlist(item.playlistSummary())
                    source.pending.kind != HomeCatalog.MusicCharts && item.kind == "video" ->
                        item.feedVideo().copy(notInterestedToken = null, dontRecommendToken = null, menuEndpoint = null)
                    else -> null
                }
            }
        }
        val items = LinkedHashMap<String, FeedItem>()
        for (index in 0 until (groups.maxOfOrNull { it.size } ?: 0)) {
            groups.forEach { group -> group.getOrNull(index)?.let { items.putIfAbsent(it.id, it) } }
        }
        val next = results.mapNotNull { source ->
            when {
                source.failure != null -> source.pending
                source.group?.nextKey != null && source.group.nextKey != source.pending.key ->
                    source.pending.copy(key = source.group.nextKey)
                else -> null
            }
        }
        val failed = results.any { it.failure != null }
        if (items.isEmpty() && failed) throw requireNotNull(results.first { it.failure != null }.failure)
        HomePage(items.values.toList(), nextKey = next.takeIf { it.isNotEmpty() }?.let {
            PREFIX + Json.encodeToString(Cursor(generation, catalog, it))
        }, catalog = catalog, partialFailure = failed)
    }

    private suspend fun loadSource(source: Pending, generation: Long): Source {
        val english = englishRead
        if (source.english && english != null) return Source(source, loadShelf(source, generation, english))
        try {
            return Source(source, loadShelf(source, generation, read))
        } catch (missing: MediaException) {
            if (english == null || source.key != null || missing.failure.code != CATALOG_SHELF_MISSING) throw missing
        }
        val fallback = source.copy(english = true)
        return Source(fallback, loadShelf(fallback, generation, english))
    }

    private suspend fun loadShelf(source: Pending, generation: Long,
                                  read: suspend (HomeSection, String?, Long) -> BrowsePage): MediaGroup {
        val section = when (source.kind) {
            HomeCatalog.Popular -> error("Popular has multiple sources")
            HomeCatalog.MusicCharts -> HomeSection.MUSIC
            HomeCatalog.Gaming -> HomeSection.GAMING
            HomeCatalog.Sports -> HomeSection.SPORTS
            HomeCatalog.News -> HomeSection.NEWS
        }
        val page = read(section, source.key, generation)
        if (source.key != null) {
            val group = page.groups.singleOrNull()
            return group ?: MediaGroup(null, page.items, page.nextKey)
        }
        // Filter labels are localized by the UI; only the shelf lookup reads server titles.
        val titles = shelfTitles(source.kind)
        page.groups.firstOrNull { it.title in titles }?.let { return it }
        if (source.kind == HomeCatalog.MusicCharts && page.nextKey != null) {
            val next = read(section, page.nextKey, generation)
            next.groups.firstOrNull { it.title in titles }?.let { return it }
        }
        throw MediaException(RequestError(RequestFailure.PARSE, CATALOG_SHELF_MISSING))
    }

    /** English (`hl=en`) and Vietnamese (`hl=vi`) titles of each directory shelf. */
    private fun shelfTitles(kind: HomeCatalog): Set<String> = when (kind) {
        HomeCatalog.Popular -> error("Popular has multiple sources")
        HomeCatalog.MusicCharts -> setOf("Top charts", "Bảng xếp hạng hàng đầu")
        HomeCatalog.Gaming -> setOf("Trending videos", "Video thịnh hành")
        HomeCatalog.Sports -> setOf("Trending", "Thịnh hành")
        HomeCatalog.News -> setOf("Top news", "Tin tức hàng đầu")
    }

    private fun sources(catalog: HomeCatalog): List<HomeCatalog> = when (catalog) {
        HomeCatalog.Popular -> listOf(HomeCatalog.Gaming, HomeCatalog.Sports, HomeCatalog.News)
        else -> listOf(catalog)
    }

    private fun decode(value: String, generation: Long, catalog: HomeCatalog): Cursor {
        val cursor = runCatching {
            require(value.length <= 131_072 && value.startsWith(PREFIX))
            Json.decodeFromString<Cursor>(value.removePrefix(PREFIX)).also {
                require(it.catalog == catalog && it.sources.isNotEmpty() && it.sources.size <= sources(catalog).size)
                require(it.sources.all { source -> source.kind in sources(catalog) })
                require(it.sources.distinctBy(Pending::kind).size == it.sources.size)
            }
        }.getOrElse { throw MediaException(RequestError(RequestFailure.INVALID, "invalid_catalog_cursor")) }
        if (cursor.generation != generation) throw CancellationException("Session changed")
        return cursor
    }

    companion object {
        private const val PREFIX = "discovery:"
        private const val CATALOG_SHELF_MISSING = "catalog_shelf_missing"
        fun owns(continuation: String?) = continuation?.startsWith(PREFIX) == true
    }
}
