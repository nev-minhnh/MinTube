package com.hhst.youtubelite.tv.watch

import com.hhst.youtubelite.core.time.publishedAgeText
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.App
import com.hhst.youtubelite.core.AppNotice
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.PlaybackSample
import com.hhst.youtubelite.data.media.PlaybackWatchReporting
import com.hhst.youtubelite.data.media.toReportingVideo
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.player.GestureUi
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.player.engine.LoopMode
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.tv.routes.TvSignInRoute
import com.hhst.youtubelite.tv.verify.TvVerification
import com.hhst.youtubelite.ui.noticeText
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.ui.observeAccountChanges
import com.hhst.youtubelite.watch.domain.WatchAction
import com.hhst.youtubelite.watch.domain.WatchContent
import com.hhst.youtubelite.watch.domain.WatchNavigationRequest
import com.hhst.youtubelite.watch.domain.WatchRelated
import com.hhst.youtubelite.watch.domain.WatchVideoMenuAction
import com.hhst.youtubelite.watch.presentation.WatchPlayer
import com.hhst.youtubelite.watch.presentation.WatchViewModel
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject

/**
 * Fullscreen TV playback. One engine and one surface: the process-scoped [PlayerViewModel]
 * drives the phone's PlayerSurface (through [WatchPlayer] in its chrome-free presentation) and
 * [TvPlayerScreen] draws the remote overlay on top. [WatchViewModel] supplies the title block
 * and the "Up next" row. No PiP: this is not a PlaybackActivity.
 */
class TvWatchActivity : TvActivity() {
    private val playerViewModel: PlayerViewModel by inject()
    private val prefs: ExtensionManager by inject()
    private lateinit var watchViewModel: WatchViewModel
    /** Loop mode to restore after "Cancel" on the autoplay card (that cancel pauses at the end). */
    private var loopBeforeCancel: LoopMode? = null
    private var cancelledVideo: String? = null
    private var signedIn by mutableStateOf(MediaSession.snapshot().signedIn)
    private var signInPrompt by mutableStateOf(false)
    private var notice by mutableStateOf<String?>(null)
    /** A solved traffic check grants the page read one retry, as on phone. */
    private val verification = registerForActivityResult(TvVerification.Contract) { challenge ->
        if (challenge != null) watchViewModel.verificationCompleted(challenge)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val videoId = intent.getStringExtra(EXTRA_VIDEO_ID) ?: playerViewModel.uiState.value.videoId.orEmpty()
        val sources = org.koin.java.KoinJavaComponent.getKoin().get<ContentRepositories>()
        watchViewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass == WatchViewModel::class.java)
                return WatchViewModel(sources.watch, extras.createSavedStateHandle(), videoId,
                    voteRepository = sources.votes, videoActions = sources.videoActions, saveRepository = sources.saves,
                    loadOnStart = false, avatars = null, displayRepair = sources.videoDisplay) as T
            }
        })[WatchViewModel::class.java]
        observeAccountChanges {
            signedIn = MediaSession.snapshot().signedIn
            watchViewModel.refreshSession()
        }
        (application as App).startMediaRuntime()
        com.hhst.youtubelite.player.PlaybackSession.register(playerViewModel)
        playerViewModel.openVideo(videoId, if (savedInstanceState == null) intent.startSeconds() else null)
        bindWatchPage(videoId)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                watchViewModel.navigationRequests.collect { request ->
                    when (request) {
                        is WatchNavigationRequest.Watch -> openVideo(request.videoId)
                        is WatchNavigationRequest.SeekTo -> playerViewModel.onSeek(request.seconds * 1000)
                        is WatchNavigationRequest.OpenVerification -> verification.launch(request.challengeUrl)
                        // The TV overlay issues no other page requests yet. No `else`: a new type must be
                        // considered here.
                        is WatchNavigationRequest.Search, WatchNavigationRequest.VoiceSearch, WatchNavigationRequest.PageMenu,
                        is WatchNavigationRequest.Channel, is WatchNavigationRequest.Comments, is WatchNavigationRequest.OpenLink,
                        is WatchNavigationRequest.Like, is WatchNavigationRequest.Dislike,
                        is WatchNavigationRequest.Subscribe, is WatchNavigationRequest.SaveToWatchLater,
                        is WatchNavigationRequest.SaveToPlaylist, is WatchNavigationRequest.CreatePlaylist,
                        is WatchNavigationRequest.Feedback, is WatchNavigationRequest.Share, is WatchNavigationRequest.Download,
                        is WatchNavigationRequest.Queue, is WatchNavigationRequest.OpenWith -> Unit
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Phone notices open the phone sign-in or a touch dialog; on TV they become the
                // sign-in panel or a short line in the overlay.
                watchViewModel.actionNotices.collect { item ->
                    when (item) {
                        AppNotice.SignIn -> signInPrompt = true
                        is AppNotice.LocalizedMessage -> notice = getString(item.resource, *item.arguments.toTypedArray())
                        is AppNotice.Message -> notice = noticeText(item.text)
                        is AppNotice.Failure -> notice = noticeText(item.text, failure = true)
                    }
                }
            }
        }
        val actions = object : TvWatchActions {
            override fun like() = watchViewModel.onAction(WatchAction.Like)
            override fun dislike() = watchViewModel.onAction(WatchAction.Dislike)
            override fun subscribe() = watchViewModel.onAction(WatchAction.Subscribe)
            override fun watchLater() {
                // The view model's Watch-later write lives behind its item menu: open it for the
                // playing video and pick Watch later, exactly as the phone's menu does.
                val id = watchViewModel.uiState.value.videoId
                if (id.isBlank()) return
                watchViewModel.onAction(WatchAction.OpenItemMenu(id))
                watchViewModel.onAction(WatchAction.MenuAction(WatchVideoMenuAction.WatchLater))
            }
            override fun verify() = watchViewModel.onAction(WatchAction.OpenVerification)
            override fun signIn() = startActivity(TvSignInRoute.intent(this@TvWatchActivity))
            override fun dismissSignInPrompt() {
                signInPrompt = false
            }
            override fun dismissNotice() {
                notice = null
            }
        }
        val callbacks = PlayerViewModelTvCallbacks(
            playerViewModel,
            onOpenVideo = ::openVideo,
            onCancelAutoplay = ::cancelAutoplay,
            onLoadMoreRelated = { watchViewModel.onAction(WatchAction.LoadRelated) },
            onExit = ::exit,
        )
        setTvContent {
            val player by playerViewModel.uiState.collectAsStateWithLifecycle()
            val page by watchViewModel.uiState.collectAsStateWithLifecycle()
            val seekTarget = remember {
                derivedStateOf { (playerViewModel.gestureState.value as? GestureUi.Scrub)?.targetMs }
            }
            val ready = (page.content as? WatchContent.Ready)?.page?.takeIf { it.video.id == player.videoId }
            val upNext = remember(ready) { ready?.related.orEmpty().filter { it.id.isNotBlank() }.map(WatchRelated::toTvVideo) }
            val info = TvWatchInfo(
                title = ready?.video?.title ?: player.title,
                channel = ready?.video?.channel ?: player.author.orEmpty(),
                detail = listOfNotNull(ready?.video?.views?.takeIf(String::isNotBlank)?.let { com.hhst.youtubelite.core.format.viewsText(it) }, ready?.video?.age?.takeIf(String::isNotBlank)?.let { publishedAgeText(it) })
                    .joinToString(" · "),
            )
            // Playlist playback (Play all / Shuffle): the card follows the playlist, not related videos.
            val inPlaylist = playerViewModel.playlistOwns(player.videoId)
            val playlistNext = if (inPlaylist) playerViewModel.playlistNextAfter(player.videoId)?.toTvVideo() else null
            TvPlayerScreen(
                player = player,
                position = playerViewModel.positionState,
                buffered = playerViewModel.bufferedPositionState,
                seekTarget = seekTarget,
                info = info,
                upNext = upNext,
                autoplayEnabled = TvAutoplay.enabled(prefs.isEnabled(PreferenceKeys.ENABLE_AUTOPLAY),
                    queueNext = player.loopMode == LoopMode.QUEUE_NEXT, queueEnabled = player.queueEnabled, inPlaylist = inPlaylist),
                inPlaylist = inPlaylist,
                playlistNext = playlistNext,
                callbacks = callbacks,
                extras = TvWatchExtras(
                    details = ready?.video?.let { video ->
                        TvWatchDetails(
                            title = video.title, channel = video.channel, date = video.age, views = video.views,
                            description = video.description,
                            likes = video.likeCount?.takeIf { prefs.isEnabled(PreferenceKeys.ENABLE_SHOW_LIKES) }.orEmpty(),
                            rating = video.rating, subscribed = video.subscribed, canSubscribe = video.channelId.isNotBlank(),
                        )
                    },
                    signedIn = signedIn,
                    pending = page.actionPending,
                    verification = page.verification != null,
                    signInPrompt = signInPrompt,
                    notice = notice,
                ),
                actions = actions,
                video = {
                    // pip = true is PlayerSurface's chrome-free presentation: the one SurfaceView,
                    // subtitles and failure dialog, without touch controls or gestures.
                    WatchPlayer(Modifier.fillMaxSize(), onBack = ::exit, pip = true, showPipLoadingIndicator = false)
                },
            )
        }
    }

    /** Page metadata, related videos and watch reporting follow the playing video, as on phone. */
    private fun bindWatchPage(videoId: String) {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Let the first frame claim the network before optional page reads.
                withTimeoutOrNull(3_000) {
                    playerViewModel.uiState.first { it.videoId == videoId && (it.firstFrameRendered || it.error != null && !it.loading) }
                }
                watchViewModel.startLoading()
                awaitCancellation()
            }
        }
        val reporting = org.koin.java.KoinJavaComponent.getKoin().get<PlaybackWatchReporting>()
        // As the phone PlaybackActivity does: the reporting loop samples this playback, which records
        // watch progress (signed in) and the on-device history (signed out).
        val reportingPlayer = playerViewModel
        reporting.attach {
            val state = reportingPlayer.uiState.value
            PlaybackSample(state.videoId, reportingPlayer.positionState.value, state.durationMs, state.isPlaying,
                state.discontinuityEpoch)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                watchViewModel.uiState.map { (it.content as? WatchContent.Ready)?.page?.video }
                    .distinctUntilChanged()
                    .collect { video -> if (video != null) reporting.publish(video.toReportingVideo()) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The phone's failure dialog (report text, Copy all) is a touch surface; on TV the
                // overlay shows the error with a focused Retry instead.
                playerViewModel.uiState.map { it.failureDialogVisible }.distinctUntilChanged().collect { visible ->
                    if (visible) playerViewModel.dismissPlaybackFailure()
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playerViewModel.uiState.map { it.videoId }.distinctUntilChanged().collect { id ->
                    if (id != null && id != watchViewModel.uiState.value.videoId) watchViewModel.selectVideo(id)
                    if (id != cancelledVideo) restoreLoopMode()
                }
            }
        }
    }

    private fun openVideo(id: String) {
        restoreLoopMode()
        playerViewModel.openVideo(id)
    }

    /**
     * The engine auto-advances at the end of a video when autoplay is on. Cancelling the card
     * pauses at the end of this video only: the loop mode is restored when the video changes or
     * this screen stops.
     */
    private fun cancelAutoplay() {
        if (loopBeforeCancel == null) loopBeforeCancel = playerViewModel.uiState.value.loopMode
        cancelledVideo = playerViewModel.uiState.value.videoId
        playerViewModel.onLoopMode(LoopMode.PAUSE_AT_END)
    }

    private fun restoreLoopMode() {
        val previous = loopBeforeCancel ?: return
        loopBeforeCancel = null
        cancelledVideo = null
        if (playerViewModel.uiState.value.loopMode == LoopMode.PAUSE_AT_END) playerViewModel.onLoopMode(previous)
    }

    /** Leaving the watch screen stops playback: a TV has no mini player and no PiP here. */
    private fun exit() {
        if (isFinishing) return
        restoreLoopMode()
        stopPlayback()
        finish()
    }

    private fun stopPlayback() {
        val state = playerViewModel.uiState.value
        playerViewModel.onLeaveWatch()
        // Background play keeps audio running after onLeaveWatch; on TV leaving means stop.
        if (!state.casting && playerViewModel.uiState.value.userWantsPlay) playerViewModel.onPlayPause()
    }

    override fun onResume() {
        super.onResume()
        signedIn = MediaSession.snapshot().signedIn
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val id = intent.getStringExtra(EXTRA_VIDEO_ID) ?: return
        restoreLoopMode()
        playerViewModel.openVideo(id, intent.startSeconds())
    }

    override fun onStop() {
        super.onStop()
        // Home or another app on top: pause (TV guideline); the video stays loaded for OK to resume.
        if (!isChangingConfigurations && !isFinishing) {
            restoreLoopMode()
            val state = playerViewModel.uiState.value
            if (!state.casting && state.userWantsPlay) playerViewModel.onPlayPause()
        }
    }

    companion object {
        const val EXTRA_VIDEO_ID = "tv_watch_video_id"
        const val EXTRA_START_SECONDS = "tv_watch_start_seconds"

        fun intent(context: Context, videoId: String, startSeconds: Long? = null): Intent =
            Intent(context, TvWatchActivity::class.java)
                .putExtra(EXTRA_VIDEO_ID, videoId)
                .apply { startSeconds?.let { putExtra(EXTRA_START_SECONDS, it) } }

        internal fun Intent.startSeconds(): Long? =
            if (hasExtra(EXTRA_START_SECONDS)) getLongExtra(EXTRA_START_SECONDS, 0).takeIf { it >= 0 } else null
    }
}

internal fun QueueItem.toTvVideo() = TvVideo(
    id = videoId, title = title, channel = author.orEmpty(), artwork = thumbnailUrl.orEmpty(),
)

internal fun WatchRelated.toTvVideo() = TvVideo(
    id = id, title = title, channel = channel,
    metadata = views, published = age,
    artwork = artwork, duration = duration, progress = progress,
)
