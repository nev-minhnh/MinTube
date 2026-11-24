package com.hhst.youtubelite.data.media

import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.withMissingDisplayFrom
import com.liskovsoft.youtubeapi.videoinfo.V2.loadPlayerDetails
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Display fields read from a video's player details, formatted exactly as search results show them.
 * [published] is the full ISO timestamp so the card keeps the time of day ("9 hours ago").
 */
data class VideoDisplay(
    val author: String = "",
    val channelId: String = "",
    /** "115,704 views", or blank when the count is unknown. */
    val views: String = "",
    val published: String = "",
    /** "m:ss" / "h:mm:ss"; blank for live streams. */
    val duration: String = "",
    val live: Boolean = false,
)

/** UI-free source of repaired display fields; shared by search and every feed. */
interface VideoDisplayRepair {
    /** Null on any failure; callers do not retry within one screen session. */
    suspend fun details(videoId: String): VideoDisplay?
}

/** The one anonymous player-details read behind search metadata and feed repair. */
internal suspend fun MediaClient.readVideoDisplay(videoId: String): VideoDisplay =
    readBackgroundIdempotent { context, http ->
        val details = loadPlayerDetails(videoId, context.anonymous(), http).requireValue()
        val seconds = details.durationSeconds?.takeIf { it > 0 && !details.live }
        VideoDisplay(
            author = details.author.orEmpty(),
            channelId = details.channelId.orEmpty(),
            views = details.viewCount?.takeIf { it >= 0 }?.let {
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.US).format(it) + " views"
            }.orEmpty(),
            // The full timestamp: a date alone turns "9 hours ago" into "1 day ago" after midnight.
            published = details.published.orEmpty(),
            duration = seconds?.let {
                if (it >= 3600) "%d:%02d:%02d".format(java.util.Locale.US, it / 3600, it / 60 % 60, it % 60)
                else "%d:%02d".format(java.util.Locale.US, it / 60, it % 60)
            }.orEmpty(),
            live = details.live,
        )
    }

/** LRU-cached, concurrency-capped wrapper around a raw [read]; any failure yields null and is not cached. */
class CachedVideoDisplayRepair(
    private val capacity: Int = 500,
    maxConcurrent: Int = 4,
    private val read: suspend (String) -> VideoDisplay?,
) : VideoDisplayRepair {
    constructor(client: MediaClient) : this(read = { client.readVideoDisplay(it) })

    private val slots = Semaphore(maxConcurrent)
    private val lock = Mutex()
    private val cache = object : LinkedHashMap<String, VideoDisplay>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, VideoDisplay>) = size > capacity
    }

    override suspend fun details(videoId: String): VideoDisplay? {
        lock.withLock { cache[videoId] }?.let { return it }
        return slots.withPermit {
            lock.withLock { cache[videoId] }?.let { return@withPermit it }
            try {
                read(videoId)?.also { value -> lock.withLock { cache[videoId] = value } }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** A row needing repair: views or publish time missing, and neither live nor a Short. */
fun FeedItem.Video.needsDisplayRepair(): Boolean = !live && !short && id.isNotBlank() &&
    (metadata.isBlank() || published.isBlank() && age.isNullOrBlank())

/** Fills only blank fields; a repaired row never loses or overwrites anything it already had. */
fun FeedItem.Video.withDisplay(display: VideoDisplay): FeedItem.Video = withMissingDisplayFrom(
    copy(author = display.author, metadata = display.views, channelId = display.channelId,
        published = display.published, duration = if (live) "" else display.duration,
        title = "", artwork = "", age = null, avatar = null))

/** Repair merge for the Watch related list: fills only blank fields. */
fun com.hhst.youtubelite.watch.domain.WatchRelated.needsDisplayRepair() = badge != "LIVE" && id.isNotBlank() &&
    (views.isBlank() || age.isBlank() || channel.isBlank())

fun com.hhst.youtubelite.watch.domain.WatchRelated.withDisplay(display: VideoDisplay) = copy(
    channel = channel.ifBlank { display.author }, channelId = channelId.ifBlank { display.channelId },
    views = views.ifBlank { display.views }, age = age.ifBlank { display.published },
    duration = duration.ifBlank { if (badge == "LIVE") "" else display.duration })

fun com.hhst.youtubelite.channel.domain.ChannelVideo.needsDisplayRepair() = !live && id.isNotBlank() && (views.isBlank() || age.isBlank())

fun com.hhst.youtubelite.channel.domain.ChannelVideo.withDisplay(display: VideoDisplay) = copy(
    views = views.ifBlank { display.views }, age = age.ifBlank { display.published },
    duration = duration.ifBlank { display.duration })

fun com.hhst.youtubelite.playlist.domain.PlaylistEntry.needsDisplayRepair() = duration != "LIVE" && videoId.isNotBlank() &&
    (views.isBlank() || age.isBlank() || channel.isBlank())

fun com.hhst.youtubelite.playlist.domain.PlaylistEntry.withDisplay(display: VideoDisplay) = copy(
    channel = channel.ifBlank { display.author }, channelId = channelId.ifBlank { display.channelId },
    views = views.ifBlank { display.views }, age = age.ifBlank { display.published },
    duration = duration.ifBlank { display.duration })

/**
 * Background repair for one screen's feed. Rows are repaired in the order given, never blocking
 * the first paint; [reset] cancels everything in flight (refresh / account change). A video whose
 * read failed is not retried until [reset] with `forgetFailures` (account change).
 */
class FeedDisplayRepairer(private val repair: VideoDisplayRepair?, private val scope: CoroutineScope) {
    private var parent: Job = Job(scope.coroutineContext[Job])
    private val inFlight = HashSet<String>()
    private val failed = HashSet<String>()

    fun reset(forgetFailures: Boolean = false) {
        parent.cancel()
        parent = Job(scope.coroutineContext[Job])
        inFlight.clear()
        if (forgetFailures) failed.clear()
    }

    /** [apply] runs on the caller's scope dispatcher with each successful read. */
    fun repair(rows: List<FeedItem.Video>, apply: (String, VideoDisplay) -> Unit) =
        repairIds(rows.filter { it.needsDisplayRepair() }.map { it.id }, apply)

    /** Same for any row model: the caller has already chosen the ids that need repair, in display order. */
    fun repairIds(ids: List<String>, apply: (String, VideoDisplay) -> Unit) {
        val source = repair ?: return
        val job = parent
        ids.filter { it.isNotBlank() }.distinct()
            .filter { it !in failed && inFlight.add(it) }
            .forEach { id ->
                scope.launch(job, start = CoroutineStart.DEFAULT) {
                    try {
                        val display = source.details(id)
                        if (job !== parent) return@launch
                        if (display == null) failed += id else apply(id, display)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        if (job === parent) failed += id
                    } finally {
                        if (job === parent) inFlight.remove(id)
                    }
                }
            }
    }
}
