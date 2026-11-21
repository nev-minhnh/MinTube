package com.hhst.youtubelite.tv.playlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
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
import com.hhst.youtubelite.player.PlayerViewModel
import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.playlist.domain.PlaylistAction
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.domain.PlaylistNavigationRequest
import com.hhst.youtubelite.playlist.presentation.PlaylistViewModel
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.channel.TvChannelActivity
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.observeAccountChanges
import com.hhst.youtubelite.ui.renderNotice
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Full-screen TV playlist over the unchanged phone [PlaylistViewModel] (built as PlaylistActivity
 * builds it). Opening a row, Play all and Shuffle hand the playlist to the shared player's
 * playlist queue ([PlayerViewModel.startPlaylist], as on phone), so playback continues through
 * the playlist in [TvWatchActivity].
 */
class TvPlaylistActivity : TvActivity() {
    private val player: PlayerViewModel by inject()
    lateinit var viewModel: PlaylistViewModel
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val playlistId = intent.getStringExtra(EXTRA_PLAYLIST_ID)
        if (playlistId.isNullOrBlank()) {
            finish()
            return
        }
        val sources = org.koin.java.KoinJavaComponent.getKoin().get<ContentRepositories>()
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass == PlaylistViewModel::class.java)
                return PlaylistViewModel(sources.playlist, extras.createSavedStateHandle(), playlistId, saveRepository = sources.saves) as T
            }
        })[PlaylistViewModel::class.java]
        syncSession()
        observeAccountChanges(::syncSession)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.actionNotices.collect { renderNotice(it) }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationRequests.collect(::navigate)
            }
        }
        setTvContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val status = remember(state) { TvPlaylistModel.status(state) }
            TvPlaylistScreen(
                status = status,
                onPlayAll = { viewModel.onAction(PlaylistAction.PlayAll) },
                onShuffle = { viewModel.onAction(PlaylistAction.Shuffle) },
                onOpenOwner = { viewModel.onAction(PlaylistAction.OpenOwner) },
                onOpen = { row -> viewModel.onAction(PlaylistAction.OpenVideo(row.entryId)) },
                onLoadMore = { viewModel.onAction(PlaylistAction.LoadNext) },
                onRetryMore = { viewModel.onAction(PlaylistAction.LoadNext) },
                onRetry = { viewModel.onAction(PlaylistAction.Retry) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) syncSession()
    }

    private fun syncSession() {
        MediaSession.snapshot().let { viewModel.updateSession(it.signedIn, it.generation) }
    }

    private fun entries() = (viewModel.uiState.value.content as? PlaylistContent.Ready)?.page?.entries.orEmpty()

    private fun navigate(request: PlaylistNavigationRequest) {
        when (request) {
            is PlaylistNavigationRequest.Watch -> play(TvPlaylistModel.queue(entries()), request.videoId)
            is PlaylistNavigationRequest.Queue -> {
                val queue = TvPlaylistModel.queue(entries(), request.videoIds, request.shuffle)
                queue.firstOrNull()?.let { play(queue, it.videoId) }
            }
            is PlaylistNavigationRequest.Channel -> if (request.channelId.isNotBlank()) {
                startActivity(TvChannelActivity.intent(this, request.channelId))
            }
            // The TV page issues no search, share, save, download, menu or verification requests.
            else -> Unit
        }
    }

    /** The playlist becomes the player's queue, so the watch screen advances through it. */
    private fun play(queue: List<QueueItem>, startId: String) {
        (application as App).startMediaRuntime()
        if (queue.isNotEmpty()) player.startPlaylist(queue, startId = startId)
        startActivity(TvWatchActivity.intent(this, startId))
    }

    companion object {
        const val EXTRA_PLAYLIST_ID = "tv_playlist_id"

        fun intent(context: Context, playlistId: String): Intent =
            Intent(context, TvPlaylistActivity::class.java).putExtra(EXTRA_PLAYLIST_ID, playlistId)
    }
}
