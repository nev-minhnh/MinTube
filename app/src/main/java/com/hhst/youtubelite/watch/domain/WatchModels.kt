package com.hhst.youtubelite.watch.domain

import kotlin.math.floor

data class WatchVideo(
    val id: String,
    val title: String,
    val channel: String,
    val handle: String,
    val channelId: String,
    val artwork: String,
    val likes: String,
    val views: String,
    val age: String,
    val commentCount: String,
    val topComment: WatchComment,
    val description: String,
    val live: Boolean = false,
    val likeCount: String? = null,
    val dislikeCount: String? = null,
    val rating: VideoRating = VideoRating.None,
    val subscribed: Boolean = false,
    val avatar: String? = null,
    val commentsStatus: WatchCommentsStatus = WatchCommentsStatus.Ready,
    /** Playback-tracking endpoints; fixtures leave it null. */
    val tracking: com.liskovsoft.youtubeapi.videoinfo.V2.PlayerTracking? = null,
    /** Live-chat continuation; fixtures leave it null so goldens stay put. */
    val chatKey: String? = null,
    val sessionGeneration: Long? = null,
    /** A failed discovery read can be retried; it does not mean the host disabled chat. */
    val chatLookupFailed: Boolean = false,
)

data class WatchComment(val author: String, val text: String, val artwork: String)
enum class WatchCommentsStatus { Loading, Ready, Empty, Disabled, Failed }

data class WatchRelated(
    val id: String,
    val title: String,
    val channel: String,
    val channelId: String,
    val views: String,
    val age: String,
    val duration: String,
    val artwork: String,
    val badge: String? = null,
    val progress: Float? = null,
    val avatar: String? = null,
    val notInterestedToken: String? = null,
    val dontRecommendToken: String? = null,
    val feedbackGeneration: Long? = null,
    val menuEndpoint: com.hhst.youtubelite.home.domain.FeedMenuEndpoint? = null,
)

data class WatchPage(val video: WatchVideo, val related: List<WatchRelated>, val nextKey: String? = null)

data class RelatedPage(val items: List<WatchRelated>, val nextKey: String?)

interface WatchRepository {
    suspend fun watch(videoId: String): WatchPage
    /** A read that must bypass any caching layer (explicit user retry). */
    suspend fun watchFresh(videoId: String): WatchPage = watch(videoId)
    suspend fun next(videoId: String, cursor: String): RelatedPage = RelatedPage(emptyList(), null)
    suspend fun menu(video: WatchRelated): WatchRelated = video
    suspend fun feedback(video: WatchRelated, action: WatchVideoMenuAction): Unit = throw UnsupportedOperationException()
}

sealed interface WatchContent {
    data object Loading : WatchContent
    data class Ready(val page: WatchPage) : WatchContent
    data object Failed : WatchContent
}

data class WatchUiState(
    val videoId: String = "",
    val content: WatchContent = WatchContent.Loading,
    val descriptionExpanded: Boolean = false,
    val overlay: WatchOverlay = WatchOverlay.None,
    val draftQuery: String = "",
    val commentsOpen: Boolean = false,
    val actionPending: Boolean = false,
    val pendingVideoAction: WatchAction? = null,
    val relatedLoading: Boolean = false,
    val relatedFailed: Boolean = false,
    val menuLoading: Boolean = false,
    val menuFailed: Boolean = false,
    /** Production: report/recommendation rows without server tokens hide; fixtures keep recorded layouts. */
    val live: Boolean = false,
    /** Card summary painted while the page read is in flight. */
    val provisional: WatchProvisional? = null,
    /** Set when the page read came back as a `/sorry` traffic challenge. */
    val verification: com.hhst.youtubelite.core.VerificationRequired? = null,
) {
    val searchOpen: Boolean get() = overlay == WatchOverlay.Search
}

/** At most one page-level overlay is active; the comments panel owns its own menus. */
sealed interface WatchOverlay {
    data object None : WatchOverlay
    data object Search : WatchOverlay
    data class ItemMenu(val videoId: String) : WatchOverlay
    data class Save(
        val videoId: String,
        val newPlaylist: Boolean = false,
        val title: String = "",
        val description: String = "",
        /** Live mode: the ViewModel owns server choices and writes; the fixture sheet stays request-only. */
        val live: Boolean = false,
        val choices: List<com.hhst.youtubelite.saves.domain.SaveChoice>? = null,
        val choicesFailed: Boolean = false,
        val pending: Boolean = false,
    ) : WatchOverlay
    data class Feedback(val videoId: String, val kind: WatchFeedbackKind) : WatchOverlay
}

enum class WatchFeedbackKind(val title: String, val reasons: List<String>) {
    Report("Report video", listOf(
        "Spam or misleading", "Harmful content", "Something else",
    )),
    NotInterested("Tell us why", listOf(
        "I'm not interested in this video", "It's repetitive", "I've already seen it",
    )),
    DontRecommend("Tell us why", listOf(
        "I don't like this channel", "This channel is misleading", "I've already seen enough",
    )),
}

enum class WatchVideoMenuAction(val label: String) {
    Queue("Add to queue"), Download("Download"),
    WatchLater("Save to Watch later"),
    SaveToPlaylist("Save to playlist"),
    Share("Share"),
    NotInterested("Not interested"),
    DontRecommend("Don't recommend channel"),
}

fun watchVideoMenuActions(live: Boolean, video: WatchRelated? = null): List<WatchVideoMenuAction> =
    if (!live) WatchVideoMenuAction.entries
    else buildList {
        addAll(listOf(WatchVideoMenuAction.Queue, WatchVideoMenuAction.Download, WatchVideoMenuAction.WatchLater, WatchVideoMenuAction.SaveToPlaylist, WatchVideoMenuAction.Share))
        if (!video?.notInterestedToken.isNullOrBlank()) add(WatchVideoMenuAction.NotInterested)
        if (!video?.dontRecommendToken.isNullOrBlank()) add(WatchVideoMenuAction.DontRecommend)
    }

/** Typed boundaries only: every entry is recorded by the host, never simulated as a success. */
sealed interface WatchNavigationRequest {
    data class Search(val query: String) : WatchNavigationRequest
    data object VoiceSearch : WatchNavigationRequest
    data object PageMenu : WatchNavigationRequest
    data class Channel(val channelId: String) : WatchNavigationRequest
    data class Comments(val videoId: String) : WatchNavigationRequest
    data class Watch(val videoId: String, val startSeconds: Long? = null) : WatchNavigationRequest
    data class SeekTo(val seconds: Long) : WatchNavigationRequest
    data class OpenLink(val url: String) : WatchNavigationRequest
    data class OpenVerification(val challengeUrl: String) : WatchNavigationRequest
    data class Like(val videoId: String) : WatchNavigationRequest
    data class Dislike(val videoId: String) : WatchNavigationRequest
    data class Subscribe(val channelId: String) : WatchNavigationRequest
    data class Download(val videoId: String) : WatchNavigationRequest
    data class Queue(val videoId: String) : WatchNavigationRequest
    data class OpenWith(val videoId: String) : WatchNavigationRequest
    data class Share(val videoId: String) : WatchNavigationRequest
    data class SaveToWatchLater(val videoId: String) : WatchNavigationRequest
    data class SaveToPlaylist(val videoId: String, val playlistId: String, val title: String) : WatchNavigationRequest
    data class CreatePlaylist(val videoId: String, val title: String, val description: String) : WatchNavigationRequest
    data class Feedback(val videoId: String, val kind: WatchFeedbackKind, val reason: String) : WatchNavigationRequest
}

sealed interface WatchAction {
    data object VoiceSearch : WatchAction
    data object CloseSearch : WatchAction
    data class QueryChanged(val query: String) : WatchAction
    data class SubmitSearch(val query: String) : WatchAction
    data object Retry : WatchAction
    data object LoadRelated : WatchAction
    data object ToggleDescription : WatchAction
    data class SeekToTimestamp(val seconds: Long) : WatchAction
    data class OpenDescriptionLink(val url: String) : WatchAction
    data object OpenSearch : WatchAction
    data object OpenPageMenu : WatchAction
    data object Like : WatchAction
    data object Dislike : WatchAction
    data object Subscribe : WatchAction
    data object Download : WatchAction
    data object Queue : WatchAction
    data object OpenWith : WatchAction
    data object Share : WatchAction
    data object Save : WatchAction
    data class SaveDestinationSelected(val destination: com.hhst.youtubelite.saves.domain.SaveDestination) : WatchAction
    data class SaveChoiceToggled(val choice: com.hhst.youtubelite.saves.domain.SaveChoice) : WatchAction
    data object SaveChoicesRetry : WatchAction
    data object OpenNewPlaylist : WatchAction
    data class SaveDraftChanged(val title: String, val description: String) : WatchAction
    data object SubmitNewPlaylist : WatchAction
    data class FeedbackReason(val reason: String) : WatchAction
    data object Report : WatchAction
    data class OpenChannel(val channelId: String) : WatchAction
    data class OpenComments(val videoId: String) : WatchAction
    data object CloseComments : WatchAction
    data class OpenRelated(val videoId: String) : WatchAction
    data class OpenItemMenu(val videoId: String) : WatchAction
    data object RetryMenu : WatchAction
    data object CloseOverlay : WatchAction
    data object DismissVerification : WatchAction
    data object OpenVerification : WatchAction
    data class MenuAction(val action: WatchVideoMenuAction) : WatchAction
}

enum class WatchRelatedVariant { Phone, Grid, List, Stacked, SidebarCompact, SidebarWide }

/**
 * Geometry sampled from the mobile watch page (CSS px as dp); formulas pinned in
 * `docs/visual/home-measurements.md`.
 */
object WatchLayout {
    const val TopBarHeight = 0f
    const val ActionBarHeight = 48f
    /** info content (pad 12 + title 26 + gap 6 + meta 18 + pad 5) + header margin-bottom 3. */
    const val InfoBlockHeight = 70f
    const val MetadataSectionHeight = 166f
    const val CommentsSectionHeight = 113f
    const val CommentsCardHeight = 92f

    const val GridBreakpoint = 550f
    const val WideShortMinWidth = 416f
    /** Two columns in any landscape window from this width (`WindowClass.Expanded`). */
    const val SidebarBreakpoint = 840f
    /** ...and in every landscape tablet window (`smallestScreenWidthDp >= 600`), whatever its width. */
    const val TabletSmallestWidth = 600f
    /** The related column takes about a third of the window, clamped to this range. */
    const val SidebarMinWidth = 320f
    const val SidebarMaxWidth = 480f
    /** From this pane width the related rows use the large thumbnail ([WatchRelatedVariant.SidebarWide]). */
    const val SidebarWideWidth = 400f
    /** A pinned player always leaves at least this much (or half the window) for the page below it. */
    const val BodyReserve = 96f

    const val ListCoverWidth = 200f
    const val ListCoverHeight = 112f

    /** Wide-short landscape: a short single-column window whose related feed uses list rows. */
    fun wideShort(width: Float, height: Float): Boolean =
        width >= WideShortMinWidth && width * 9f >= height * 13f && width < SidebarBreakpoint

    /**
     * The player is pinned in every non-fullscreen layout: scrolling the page never moves it.
     * Short windows keep the body reachable by shrinking the player instead ([playerHeight]).
     */
    @Suppress("UNUSED_PARAMETER")
    fun pinnedPlayer(width: Float, height: Float): Boolean = true

    /** Two columns: a landscape window that is a tablet, or at least [SidebarBreakpoint] wide. */
    fun sidebar(width: Float, height: Float, smallestWidth: Float = 0f): Boolean =
        width > height && (width >= SidebarBreakpoint || smallestWidth >= TabletSmallestWidth)

    fun sidebarWidth(width: Float): Float = (width / 3f).coerceIn(SidebarMinWidth, SidebarMaxWidth)

    /** Main column = viewport minus the sidebar; the player is fixed over this width only. */
    fun columnWidth(width: Float, height: Float, smallestWidth: Float = 0f): Float =
        if (sidebar(width, height, smallestWidth)) width - sidebarWidth(width) else width

    /** The column's 16:9, capped so the pinned player leaves [BodyReserve] (or half a tiny window) below it. */
    fun playerHeight(width: Float, height: Float, smallestWidth: Float = 0f): Float =
        minOf(columnWidth(width, height, smallestWidth) * 9f / 16f, maxOf(height - BodyReserve, height / 2f))

    /** `cols = floor((width − 16) / 250)` → 2 for 550–750, 3 for ≥780. */
    fun gridColumns(width: Float): Int = floor((width - 16f) / 250f).toInt().coerceAtLeast(1)

    /** Cells are flush: cell = width/cols − 8, so the right inset is 8 × (cols − 1). */
    fun gridCell(width: Float): Float = width / gridColumns(width) - 8f

    fun relatedVariant(width: Float, height: Float, smallestWidth: Float = 0f): WatchRelatedVariant = when {
        sidebar(width, height, smallestWidth) ->
            if (sidebarWidth(width) >= SidebarWideWidth) WatchRelatedVariant.SidebarWide
            else WatchRelatedVariant.SidebarCompact
        wideShort(width, height) -> WatchRelatedVariant.List
        width < GridBreakpoint -> WatchRelatedVariant.Phone
        width <= height -> WatchRelatedVariant.Grid
        else -> WatchRelatedVariant.Stacked
    }

    /** Full-bleed phone cover keeps the sampled +13 height; every other cover is exact 16:9. */
    fun phoneCoverHeight(width: Float): Float = width * 9f / 16f + 13f
}
