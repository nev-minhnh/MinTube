package com.hhst.youtubelite.tv.shorts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.App
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.data.media.PlaybackSample
import com.hhst.youtubelite.data.media.PlaybackWatchReporting
import com.hhst.youtubelite.data.media.toReportingVideo
import com.hhst.youtubelite.player.PlaybackSession
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.player.engine.LoopMode
import com.hhst.youtubelite.shorts.data.ShortsMediaPreloader
import com.hhst.youtubelite.shorts.data.ShortsScenario
import com.hhst.youtubelite.shorts.domain.ShortsAction
import com.hhst.youtubelite.shorts.domain.ShortsContent
import com.hhst.youtubelite.shorts.domain.ShortsUiState
import com.hhst.youtubelite.shorts.presentation.ShortsViewModel
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.routes.renderTvNotice
import com.hhst.youtubelite.ui.observeAccountChanges
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.watch.presentation.WatchPlayer
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Full-screen TV Shorts: the unchanged [ShortsViewModel] feed (as ShortsActivity builds it) on the
 * one playback engine ([PlayerViewModel] in Shorts mode). Up/Down change Short, OK plays or pauses,
 * Left/Right seek 5 s, Back leaves and stops playback.
 */
class TvShortsActivity : TvActivity() {
    private val player: PlayerViewModel by inject()
    private lateinit var shorts: ShortsViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sources = getKoin().get<ContentRepositories>()
        (application as App).startMediaRuntime()
        PlaybackSession.register(player)
        val reporting = getKoin().get<PlaybackWatchReporting>()
        reporting.attach {
            val state = player.uiState.value
            PlaybackSample(state.videoId, player.positionState.value, state.durationMs, state.isPlaying, state.discontinuityEpoch)
        }
        val start = intent.getStringExtra(EXTRA_START_ITEM)
        shorts = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass == ShortsViewModel::class.java)
                return ShortsViewModel(sources.shorts(start, null), extras.createSavedStateHandle(), ShortsScenario.Content, start,
                    sources.videoActions, getKoin().get<ShortsMediaPreloader>(), sources.saves) as T
            }
        })[ShortsViewModel::class.java]
        observeAccountChanges { shorts.updateSession(MediaSession.snapshot().generation) }
        collectWhileStarted { shorts.actionNotices.collect(::renderTvNotice) }
        // Watch reporting follows the hydrated Short on screen, as on phone.
        collectWhileStarted {
            shorts.uiState.map { s -> s.currentItemId?.let { s.details[it] } }.distinctUntilChanged()
                .collect { detail -> if (detail != null) reporting.publish(detail.toReportingVideo()) }
        }
        // The phone failure dialog is a touch surface; the TV panel shows a focused Retry instead.
        collectWhileStarted {
            player.uiState.map { it.failureDialogVisible }.distinctUntilChanged().collect { visible ->
                if (visible) player.dismissPlaybackFailure()
            }
        }
        val callbacks = object : TvShortsCallbacks {
            override fun playPause() = player.onPlayPause()
            override fun play() { if (!player.uiState.value.userWantsPlay) player.onPlayPause() }
            override fun pause() { if (player.uiState.value.userWantsPlay) player.onPlayPause() }
            override fun seekBy(deltaMs: Long) {
                val state = player.uiState.value
                if (state.videoId != shorts.uiState.value.currentItemId) return
                player.onSeek(tvShortsSeekTarget(player.positionState.value, deltaMs, state.durationMs))
            }
            override fun next() = move(forward = true)
            override fun previous() = move(forward = false)
            override fun like() { shorts.uiState.value.currentItemId?.let { shorts.onAction(ShortsAction.Like(it)) } }
            override fun dislike() { shorts.uiState.value.currentItemId?.let { shorts.onAction(ShortsAction.Dislike(it)) } }
            override fun retry() = shorts.onAction(ShortsAction.Retry)
            override fun retryPlayback() = player.onRetry()
        }
        setTvContent {
            DisposableEffect(lifecycle) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME -> player.onShortsOpened()
                        Lifecycle.Event.ON_PAUSE -> player.onShortsStopped()
                        else -> Unit
                    }
                }
                lifecycle.addObserver(observer)
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) player.onShortsOpened()
                onDispose {
                    lifecycle.removeObserver(observer)
                    if (!isChangingConfigurations) player.onShortsClosed()
                }
            }
            val state by shorts.uiState.collectAsStateWithLifecycle()
            val playerState by player.uiState.collectAsStateWithLifecycle()
            val ids = remember(state.content) { ids(state) }
            // The feed settles on the seed (or its first Short); a feed without the current id restarts at its head.
            LaunchedEffect(ids, state.currentItemId) {
                if (ids.isNotEmpty() && state.currentItemId !in ids) shorts.onAction(ShortsAction.SelectItem(ids.first()))
            }
            LaunchedEffect(state.currentItemId) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    state.currentItemId?.let(player::openShort)
                    awaitCancellation()
                }
            }
            LaunchedEffect(playerState.videoId, playerState.firstFrameRendered, state.currentItemId) {
                if (playerState.firstFrameRendered && playerState.videoId == state.currentItemId) {
                    playerState.videoId?.let(shorts::onPlaybackReady)
                }
            }
            val resumed by lifecycle.currentStateFlow.collectAsState()
            LaunchedEffect(playerState.ended, playerState.prepared, playerState.videoId, state.currentItemId, playerState.loopMode,
                ids.size, resumed) {
                if (resumed.isAtLeast(Lifecycle.State.RESUMED) && playerState.prepared && playerState.ended &&
                    playerState.loopMode == LoopMode.QUEUE_NEXT) {
                    playerState.videoId?.let(shorts::onPlaybackEnded)
                }
            }
            val current = state.currentItemId
            val onScreen = playerState.videoId != null && playerState.videoId == current
            val index = ids.indexOf(current)
            TvShortsScreen(
                status = when (state.content) {
                    ShortsContent.Loading -> TvShortsStatus.Loading
                    ShortsContent.Failed -> TvShortsStatus.Failed
                    ShortsContent.Empty -> TvShortsStatus.Empty
                    is ShortsContent.Ready -> TvShortsStatus.Ready
                },
                info = info(state),
                playback = TvShortPlayback(
                    playing = onScreen && playerState.isPlaying,
                    paused = onScreen && (playerState.shortPausedByUser || playerState.ended) && playerState.firstFrameRendered,
                    loading = !onScreen || !playerState.prepared || playerState.loading,
                    failed = onScreen && playerState.error != null,
                    durationMs = if (onScreen) playerState.durationMs else 0L,
                    videoReady = onScreen && playerState.hasRenderedVideoFrame,
                ),
                position = player.positionState,
                hasPrevious = index > 0,
                hasNext = index in 0 until ids.lastIndex || state.appendLoading,
                canRate = state.live,
                callbacks = callbacks,
                video = {
                    // Only the Short the engine loaded renders; any other keeps its cover.
                    if (onScreen) WatchPlayer(Modifier.fillMaxSize(), onBack = ::finish, pip = true, showPipLoadingIndicator = false)
                },
            )
        }
    }

    private fun collectWhileStarted(block: suspend () -> Unit) {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { block() } }
    }

    private fun ids(state: ShortsUiState): List<String> =
        (state.content as? ShortsContent.Ready)?.page?.items?.map { it.id }.orEmpty()

    private fun info(state: ShortsUiState): TvShortInfo? {
        val id = state.currentItemId ?: return null
        val item = (state.content as? ShortsContent.Ready)?.page?.items?.firstOrNull { it.id == id } ?: return null
        val detail = state.details[id]
        val shown = detail?.item?.takeIf { it.id == id }?.let { full ->
            item.copy(title = item.title.ifBlank { full.title }, author = item.author.ifBlank { full.author },
                views = item.views.ifBlank { full.views }, artwork = item.artwork.ifBlank { full.artwork })
        } ?: item
        return TvShortInfo(
            id = id, title = shown.title, channel = shown.author, detail = shown.views, artwork = shown.artwork,
            rating = state.ratings[id] ?: detail?.rating ?: VideoRating.None,
            ratingPending = id in state.ratingsPending,
        )
    }

    /** Up/Down: the neighbouring Short; nearing the end reads the next page (or retries a failed one). */
    private fun move(forward: Boolean) {
        val state = shorts.uiState.value
        val ids = ids(state)
        val target = tvShortsNeighbour(ids, state.currentItemId, forward)
        if (target != null) shorts.onAction(ShortsAction.SelectItem(target))
        if (forward && ids.indexOf(target ?: state.currentItemId) >= ids.size - LOAD_AHEAD) {
            shorts.onAction(if (state.appendFailed) ShortsAction.RetryMore else ShortsAction.LoadMore)
        }
    }

    override fun onResume() {
        super.onResume()
        // The Shorts feed and ratings are identity-scoped server-side.
        shorts.updateSession(MediaSession.snapshot().generation)
    }

    companion object {
        const val EXTRA_START_ITEM = "tv_shorts_start_item"
        private const val LOAD_AHEAD = 3

        fun intent(context: Context, startItemId: String?): Intent =
            Intent(context, TvShortsActivity::class.java).apply { startItemId?.let { putExtra(EXTRA_START_ITEM, it) } }
    }
}
