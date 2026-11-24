package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.home.domain.*
import com.hhst.youtubelite.search.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.withPermit
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.liskovsoft.youtubeapi.videoinfo.V2.MediaItem
import com.liskovsoft.youtubeapi.videoinfo.V2.HomeSection
import com.liskovsoft.youtubeapi.videoinfo.V2.MenuActionKind
import com.liskovsoft.youtubeapi.videoinfo.V2.loadHome
import com.liskovsoft.youtubeapi.videoinfo.V2.loadSearch
import com.liskovsoft.youtubeapi.videoinfo.V2.sendFeedback
import com.liskovsoft.youtubeapi.videoinfo.V2.loadMenu
import com.liskovsoft.youtubeapi.videoinfo.V2.MenuEndpoint
import com.liskovsoft.youtubeapi.videoinfo.V2.ledgerScope

// Blank halves are dropped so a missing part cannot leave an orphaned separator; a missing
// avatar is null (not "") — that is the signal the shared avatar directory enriches from.
internal fun MediaItem.feedVideo() = FeedItem.Video(id, title.orEmpty(), author.orEmpty(),
    viewCount.orEmpty(), thumbnail.orEmpty(),
    duration.orEmpty(), channelId.orEmpty(), live, progressPercent?.div(100f), published = published.orEmpty(), avatar = authorThumbnail?.takeIf(String::isNotBlank),
    notInterestedToken = notInterestedToken(),
    dontRecommendToken = dontRecommendToken(),
    menuEndpoint = menuEndpoint?.let { FeedMenuEndpoint(it.panelId, it.params) }, short = kind == "short",
    channelLookup = menuEndpoint?.let { FeedMenuEndpoint(it.panelId, it.params) })

internal fun MediaItem.notInterestedToken(): String? =
    actions.firstOrNull { it.kind == MenuActionKind.NotInterested }?.kindToken
internal fun MediaItem.dontRecommendToken(): String? =
    actions.firstOrNull { it.kind == MenuActionKind.DontRecommendChannel }?.kindToken

internal fun MediaItem.playlistSummary() = PlaylistSummary(id.removePrefix("VL"), title.orEmpty(), author.orEmpty(),
    channelId.orEmpty(), null, thumbnail.orEmpty(), "", null, videoCountText = videoCountText)

private val mixId = Regex("^RD[A-Za-z0-9_-]{11}$")

/**
 * Playlist rows for Explore. A radio/mix row can arrive with only its id (the parser had nothing else
 * to read); the screen hides rows without a title, and a "RD<videoId>" mix borrows that video's thumbnail.
 */
internal fun List<MediaItem>.displayablePlaylists(): List<PlaylistSummary> = filter { it.kind == "playlist" }
    .map { it.playlistSummary() }
    .map { if (it.artwork.isBlank() && mixId.matches(it.id)) it.copy(artwork = "https://i.ytimg.com/vi/${it.id.drop(2)}/hqdefault.jpg") else it }

/**
 * LRU of resolved get_panel capabilities, partitioned by identity (account, on-behalf channel,
 * generation) and card, 60s TTL; a lookup purges other identities so stale-session tokens
 * can never be served to the current one.
 */
internal class DeferredMenuCache(private val now: () -> Long = System::currentTimeMillis) {
    data class Key(val account: String?, val channel: String?, val generation: Long, val videoId: String,
        val endpoint: FeedMenuEndpoint)
    data class Capabilities(val at: Long, val notInterested: String?, val dontRecommend: String?)
    private val entries = LinkedHashMap<Key, Capabilities>(32, 0.75f, true)

    fun get(key: Key): Capabilities? = synchronized(entries) {
        entries.entries.removeAll { it.key.account != key.account || it.key.channel != key.channel ||
            it.key.generation != key.generation || now() - it.value.at !in 0..60_000 }
        entries[key]
    }

    fun put(key: Key, capabilities: Capabilities) = synchronized(entries) {
        entries[key] = capabilities
        while (entries.size > 32) entries.remove(entries.keys.first())
    }
}

class MediaHomeRepository(private val client: MediaClient,
    private val now: () -> Long = System::currentTimeMillis) : HomeRepository {
    private val menus = DeferredMenuCache(now)
    private val discovery = HomeDiscovery(englishRead = { section, key, generation ->
        discoveryRead(section, key, generation, ContentLocale.ENGLISH)
    }) { section, key, generation -> discoveryRead(section, key, generation, null) }

    /** Signed-out directory shelves: the session's hl/gl, or English when [language] pins it. */
    private suspend fun discoveryRead(section: HomeSection, key: String?, generation: Long, language: String?) =
        client.readIdempotent { context, http ->
            if (context.generation != generation)
                throw kotlinx.coroutines.CancellationException("Session changed")
            val anonymous = context.anonymous()
            loadHome(section, if (language == null) anonymous else anonymous.copy(language = language), key, http).requireValue()
        }
    override val filters = listOf("All", "Music", "Gaming", "Live", "News", "Sports")
    override suspend fun loadHome(filter: String, continuation: String?): HomePage {
        val catalog = HomeCatalog.forFilter(filter)
        val context = client.read { context, _ -> context }
        if (HomeDiscovery.owns(continuation)) {
            if (context.signedIn && catalog != HomeCatalog.MusicCharts || catalog == null)
                throw kotlinx.coroutines.CancellationException("Session changed")
            return discovery.load(context.generation, catalog, continuation)
        }
        if (filter == HomeCatalog.MusicCharts.filter || !context.signedIn && catalog != null && catalog != HomeCatalog.Popular)
            return discovery.load(context.generation, requireNotNull(catalog), continuation)
        val (loadedContext, page) = client.readIdempotent { context, http ->
            val section = when (filter) {
                "All" -> HomeSection.HOME
                "Music" -> HomeSection.MUSIC
                "Live" -> HomeSection.LIVE
                "Gaming" -> HomeSection.GAMING
                "News" -> HomeSection.NEWS
                "Sports" -> HomeSection.SPORTS
                else -> throw MediaException(com.liskovsoft.youtubeapi.videoinfo.V2.RequestError(
                    com.liskovsoft.youtubeapi.videoinfo.V2.RequestFailure.UNSUPPORTED, "filter_unavailable"))
            }
            val homeContext = if (continuation == null) anonymousHomeContext(context, http,
                MediaSession.anonymousHomeIdentityVerified(context)) else context
            val page = loadHome(section, homeContext, continuation, http).requireValue()
            if (!context.signedIn) {
                val cookie = homeContext.visitorCookie
                if (cookie != null) MediaSession.rememberVisitorIdentity(homeContext.visitor, cookie, context.generation)
                else MediaSession.rememberVisitor(page.visitor, context.generation)
            }
            val feed = homeFeed(page.items).map { item ->
                if (item is FeedItem.Video && context.signedIn) item.copy(
                    dontRecommendToken = page.items.firstOrNull { it.id == item.id }?.dontRecommendToken(),
                    feedbackGeneration = context.generation,
                // Signed out: no feedback capability; channelLookup stays so avatars can still resolve.
                ) else if (item is FeedItem.Video) item.copy(notInterestedToken = null, dontRecommendToken = null,
                    menuEndpoint = null) else item
            }
            context to HomePage(feed, page.messages, page.nextKey)
        }
        return if (!loadedContext.signedIn && filter == "All" && continuation == null && page.items.isEmpty() && page.nextKey == null)
            discovery.load(loadedContext.generation) else page
    }

    override suspend fun menu(video: FeedItem.Video): FeedItem.Video {
        val endpoint = video.menuEndpoint ?: video.feedbackGeneration?.let { recommendationMenuEndpoint(video.id) } ?: return video
        return client.readIdempotent { context, http ->
            if (!context.signedIn || video.feedbackGeneration != context.generation)
                throw kotlinx.coroutines.CancellationException("Session changed")
            val key = DeferredMenuCache.Key(context.accountId, context.onBehalfOfUser, context.generation, video.id, endpoint)
            val cached = menus.get(key)
            val capabilities = cached ?: loadMenu(MenuEndpoint(endpoint.panelId, endpoint.params), context, http)
                .requireValue().let { actions ->
                    DeferredMenuCache.Capabilities(now(), actions.firstOrNull { it.kind == MenuActionKind.NotInterested }?.kindToken,
                        actions.firstOrNull { it.kind == MenuActionKind.DontRecommendChannel }?.kindToken)
                }.also { loaded -> menus.put(key, loaded) }
            video.copy(notInterestedToken = capabilities.notInterested, dontRecommendToken = capabilities.dontRecommend,
                feedbackGeneration = context.generation)
        }
    }

    override suspend fun feedback(video: FeedItem.Video, action: FeedMenuAction) {
        client.write { context, http ->
            if (!context.signedIn) throw com.hhst.youtubelite.watch.domain.SignInRequiredException()
            val token = when (action) {
                FeedMenuAction.NotInterested -> video.notInterestedToken
                FeedMenuAction.DontRecommend -> video.dontRecommendToken
                else -> null
            }
            if (token.isNullOrBlank() || video.feedbackGeneration != context.generation)
                throw com.hhst.youtubelite.watch.domain.UnconfirmedActionException()
            sendFeedback(token, context, http).requireValue()
        }
    }
}

/**
 * Server order, shelf by shelf: consecutive shorts fold into one inline rail identified by
 * its first video so rails keep identity across refreshes and appended rails cannot collide.
 * Mix/Post containers appear only when the parse produced one — never fabricated.
 */
internal fun homeFeed(items: List<MediaItem>): List<FeedItem> {
    val feed = ArrayList<FeedItem>()
    var rail = ArrayList<FeedItem.Video>()
    fun flushRail() {
        if (rail.isNotEmpty()) {
            feed += FeedItem.Shorts("shorts-" + rail.first().id, rail.toList())
            rail = ArrayList()
        }
    }
    for (item in items) when (item.kind) {
        "short" -> rail += item.feedVideo()
        "video" -> {
            flushRail()
            feed += item.feedVideo()
        }
        else -> Unit
    }
    flushRail()
    return feed
}

class MediaSearchRepository(private val client: MediaClient,
    private val now: () -> Long = System::currentTimeMillis) : SearchRepository {
    private val menus = DeferredMenuCache(now)
    private data class FilterIdentity(val ledger: String, val oauth: Boolean, val visitor: String?,
        val language: String, val region: String, val cookies: String?)
    private data class FilterPanel(val identity: FilterIdentity, val query: String, val applied: SearchFilters,
        val filters: List<com.liskovsoft.youtubeapi.videoinfo.V2.SearchFilter>, val at: Long)
    // Only navigation capabilities from the latest completed page; results are always fetched.
    @Volatile private var filterPanel: FilterPanel? = null

    override suspend fun metadata(result: SearchResult): SearchResult {
        val details = client.readVideoDisplay(result.id)
        return result.copy(channel = result.channel.ifBlank { details.author },
            channelId = result.channelId.ifBlank { details.channelId },
            views = result.views.ifBlank { details.views },
            duration = result.duration.ifBlank { details.duration },
            age = result.age.ifBlank { details.published })
    }
    override suspend fun search(query: String, filters: SearchFilters): SearchPage = uploadFiltered(client.read { context, http ->
        val identity = filterIdentity(context)
        val direct = filterPanel?.takeIf { it.identity == identity && it.query == query && now() - it.at in 0..60_000 }
            ?.transition(filters)
        var page = loadSearch(query, context, direct, http = http).requireValue()
        var anonymousFallback = page.anonymousFallback
        if (direct == null) {
            // Cold search / reset / unproven transition: each response carries cumulative params for the next option.
            val steps = listOfNotNull(
                filters.uploaded.label()?.let { Triple(it, SearchFilterParams.UPLOAD_DATE, filters.uploaded.code()) },
                filters.type.label()?.let { Triple(it, SearchFilterParams.TYPE, filters.type.code()) })
            var offered = true
            for ((label, field, code) in steps) {
                // English labels first; any other hl is recognized by the option's params.
                val filter = page.filters.firstOrNull { it.matches(label) }
                    ?: page.filters.firstOrNull { !it.selected && SearchFilterParams.value(it.params, field) == code }
                if (filter == null) { offered = false; break }
                page = loadSearch(query, context, filter.params, http = http).requireValue()
                anonymousFallback = anonymousFallback || page.anonymousFallback
            }
            // Signed-in responses can come without a filter panel: send YouTube's own params for the
            // whole selection instead of failing. If even that fails, the unfiltered page stays and the
            // client-side upload-date filter below still applies.
            if (!offered) SearchFilterParams.build(filters.uploaded.code(), filters.type.code())?.let { params ->
                runCatching { loadSearch(query, context, params, http = http).requireValue() }.getOrNull()?.let {
                    page = it
                    anonymousFallback = anonymousFallback || it.anonymousFallback
                }
            }
        }
        filterPanel = if (anonymousFallback) null else FilterPanel(identity, query, filters, page.filters, now())
        page.toSearchPage(query, context)
    }, query, filters)

    /**
     * With an upload date selected, a page is shown only once filtered: rows whose publish time is missing
     * get it first (several at a time), rows older than the limit are dropped, and a page left empty
     * follows its continuation (at most 3 more), so nothing appears and then vanishes.
     */
    private suspend fun uploadFiltered(first: SearchPage, query: String, filters: SearchFilters): SearchPage {
        if (filters.uploaded.maxAgeHours == null) return first
        val at = java.time.Instant.ofEpochMilli(now())
        var page = withAges(first).filteredByUpload(filters.uploaded, at)
        var extra = 0
        while (extra < 3 && page.results.isEmpty() && page.shorts.isEmpty() && page.playlists.isEmpty() &&
            page.channels.isEmpty() && page.nextCursor != null) {
            val cursor = page.nextCursor!!
            val raw = client.read { context, http ->
                loadSearch(query, context, continuation = cursor, http = http).requireValue().toSearchPage(query, context)
            }
            page = withAges(raw).filteredByUpload(filters.uploaded, at).copy(query = page.query)
            extra++
        }
        return page
    }

    private suspend fun withAges(page: SearchPage): SearchPage = kotlinx.coroutines.coroutineScope {
        val gate = kotlinx.coroutines.sync.Semaphore(6)
        val at = java.time.Instant.ofEpochMilli(now())
        suspend fun List<SearchResult>.complete(): List<SearchResult> = map { row ->
            async {
                if (row.badge == "LIVE" || com.hhst.youtubelite.core.time.PublishedAges.parse(row.age, at) != null) row
                else gate.withPermit {
                    try { metadata(row).let { it.copy(uploadedDays = uploadedDaysOf(it.age, at)) } }
                    catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) { row } // Unknown age stays visible, as before.
                }
            }
        }.awaitAll()
        page.copy(results = page.results.complete(), shorts = page.shorts.complete())
    }

    private fun filterIdentity(context: com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext): FilterIdentity {
        val web = context.webSession
        val cookies = when {
            context.accessToken != null -> null
            web?.signedIn == true -> web.cookies + "\u0000" + web.accountIndex
            else -> context.visitorCookie
        }
        val digest = cookies?.let { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) } }
        return FilterIdentity(context.ledgerScope(),
            context.accessToken != null, context.visitor, context.language, context.region, digest)
    }

    private fun FilterPanel.transition(target: SearchFilters): String? {
        val typeChanged = applied.type != target.type
        val dateChanged = applied.uploaded != target.uploaded
        if (typeChanged == dateChanged) return null
        val group: String
        val label: String
        val retainedGroup: String
        val retainedLabel: String?
        if (typeChanged) {
            group = "Type"
            label = target.type.label() ?: return null // Reset endpoints need their own proof.
            retainedGroup = "Upload date"
            retainedLabel = applied.uploaded.label()
        } else {
            group = "Upload date"
            label = target.uploaded.label() ?: return null
            retainedGroup = "Type"
            retainedLabel = applied.type.label()
        }
        val retained = filters.filter { it.group.equals(retainedGroup, true) }
        if (retained.isEmpty()) return null
        val selected = retained.filter { it.selected }
        // A returned endpoint belongs to its current filter state. Prove the other
        // group is still selected before using its cumulative navigation params.
        if (retainedLabel != null && (selected.size != 1 || !selected.single().matches(retainedLabel))) return null
        if (retainedLabel == null && selected.any { !it.title.equals("All", true) && !it.title.equals("Anytime", true) }) return null
        return filters.firstOrNull { it.group.equals(group, true) && !it.selected && it.matches(label) }
            ?.params?.takeIf(String::isNotBlank)
    }

    private fun com.liskovsoft.youtubeapi.videoinfo.V2.SearchFilter.matches(label: String) =
        title.equals(label, true) || title.equals(label + "s", true)
    private fun SearchUploadDate.label(): String? = when (this) {
        SearchUploadDate.Today -> "Today"
        SearchUploadDate.ThisWeek -> "This week"
        SearchUploadDate.ThisMonth -> "This month"
        SearchUploadDate.ThisYear -> "This year"
        SearchUploadDate.Anytime -> null
    }
    private fun SearchUploadDate.code(): Long? = when (this) {
        SearchUploadDate.Today -> 2
        SearchUploadDate.ThisWeek -> 3
        SearchUploadDate.ThisMonth -> 4
        SearchUploadDate.ThisYear -> 5
        SearchUploadDate.Anytime -> null
    }
    private fun SearchResultType.code(): Long? = when (this) {
        SearchResultType.Channels -> 2
        SearchResultType.Playlists -> 3
        SearchResultType.Shorts -> 9
        SearchResultType.All -> null
    }
    private fun SearchResultType.label(): String? = when (this) {
        SearchResultType.Channels -> "Channel"
        SearchResultType.Playlists -> "Playlist"
        SearchResultType.Shorts -> "Shorts"
        SearchResultType.All -> null
    }

    override suspend fun next(query: String, filters: SearchFilters, cursor: String): SearchPage = uploadFiltered(client.read { context, http ->
        loadSearch(query, context, continuation = cursor, http = http).requireValue().toSearchPage(query, context)
    }, query, filters)

    override suspend fun menu(result: SearchResult): SearchResult {
        val endpoint = result.menuEndpoint ?: return result
        return client.readIdempotent { context, http ->
            if (!context.signedIn || result.feedbackGeneration != context.generation)
                throw kotlinx.coroutines.CancellationException("Session changed")
            val key = DeferredMenuCache.Key(context.accountId, context.onBehalfOfUser, context.generation, result.id, endpoint)
            val capabilities = menus.get(key) ?: loadMenu(MenuEndpoint(endpoint.panelId, endpoint.params), context, http)
                .requireValue().let { actions ->
                    DeferredMenuCache.Capabilities(now(), actions.firstOrNull { it.kind == MenuActionKind.NotInterested }?.kindToken,
                        actions.firstOrNull { it.kind == MenuActionKind.DontRecommendChannel }?.kindToken)
                }.also { loaded -> menus.put(key, loaded) }
            result.copy(notInterestedToken = capabilities.notInterested, dontRecommendToken = capabilities.dontRecommend,
                feedbackGeneration = context.generation)
        }
    }

    override suspend fun feedback(result: SearchResult, action: SearchVideoMenuAction) {
        client.write { context, http ->
            if (!context.signedIn) throw com.hhst.youtubelite.watch.domain.SignInRequiredException()
            val token = when (action) {
                SearchVideoMenuAction.NotInterested -> result.notInterestedToken
                SearchVideoMenuAction.DontRecommend -> result.dontRecommendToken
                else -> null
            }
            if (token.isNullOrBlank() || result.feedbackGeneration != context.generation)
                throw com.hhst.youtubelite.watch.domain.UnconfirmedActionException()
            sendFeedback(token, context, http).requireValue()
        }
    }
}

private fun com.liskovsoft.youtubeapi.videoinfo.V2.SearchPage.toSearchPage(query: String,
    context: com.liskovsoft.youtubeapi.videoinfo.V2.RequestContext, nowMillis: Long = System.currentTimeMillis()): SearchPage {
    // Anonymous sessions never carry feedback capabilities, so a forged menu action
    // cannot even be attempted from a stale card.
    val signedIn = context.signedIn
    fun MediaItem.result() = SearchResult(id, title.orEmpty(), author.orEmpty(), channelId.orEmpty(), viewCount.orEmpty(), published.orEmpty(),
        duration.orEmpty(), thumbnail.orEmpty(), if (live) "LIVE" else null, progressPercent?.div(100f),
        uploadedDays = uploadedDaysOf(published.orEmpty(), java.time.Instant.ofEpochMilli(nowMillis)), avatar = authorThumbnail?.takeIf(String::isNotBlank),
        notInterestedToken = notInterestedToken()?.takeIf { signedIn },
        dontRecommendToken = dontRecommendToken()?.takeIf { signedIn },
        menuEndpoint = menuEndpoint?.takeIf { signedIn }?.let { FeedMenuEndpoint(it.panelId, it.params) },
        feedbackGeneration = context.generation.takeIf { signedIn })
    return SearchPage(query, items.filter { it.kind == "video" }.map { it.result() },
        items.filter { it.kind == "playlist" }.map(MediaItem::playlistSummary),
        items.filter { it.kind == "channel" }.map { SearchChannel(it.id, it.title.orEmpty(), "", "", it.thumbnail.orEmpty()) },
        items.filter { it.kind == "short" }.map { it.result() }, nextKey)
}
