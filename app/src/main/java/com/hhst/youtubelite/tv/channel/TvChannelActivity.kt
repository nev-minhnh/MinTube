package com.hhst.youtubelite.tv.channel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelNavigationRequest
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.presentation.ChannelViewModel
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.playlist.TvPlaylistActivity
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.observeAccountChanges
import com.hhst.youtubelite.ui.renderNotice
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * Full-screen TV channel page over the unchanged phone [ChannelViewModel] (built as
 * ChannelActivity builds it). Subscribe runs the ViewModel's write and its notices, including
 * the sign-in notice, through [renderNotice]. Videos, Shorts and live rows open
 * [TvWatchActivity]; playlists open [TvPlaylistActivity].
 */
class TvChannelActivity : TvActivity() {
    private val prefs: ExtensionManager by inject()
    lateinit var viewModel: ChannelViewModel
        private set
    private var hideShorts by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val channelId = intent.getStringExtra(EXTRA_CHANNEL_ID)
        if (channelId.isNullOrBlank()) {
            // No identity: rendering a default channel would fabricate content.
            finish()
            return
        }
        val sources = org.koin.java.KoinJavaComponent.getKoin().get<ContentRepositories>()
        viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                require(modelClass == ChannelViewModel::class.java)
                return ChannelViewModel(sources.channel, extras.createSavedStateHandle(), channelId,
                    videoActions = sources.videoActions) as T
            }
        })[ChannelViewModel::class.java]
        hideShorts = prefs.isEnabled(PreferenceKeys.ENABLE_HIDE_SHORTS)
        // The TV page has no Home shelves, Posts or in-channel search: it opens on Videos.
        if (viewModel.uiState.value.selectedTab !in TvChannelModel.GridTabs) viewModel.onAction(ChannelAction.SelectTab(ChannelTab.Videos))
        viewModel.updateSignedIn(MediaSession.snapshot().signedIn)
        observeAccountChanges {
            viewModel.updateSignedIn(MediaSession.snapshot().signedIn)
            viewModel.refreshSession()
        }
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
            val status = remember(state, hideShorts) { TvChannelModel.status(state, hideShorts) }
            TvChannelScreen(
                status = status,
                onSelectTab = { viewModel.onAction(ChannelAction.SelectTab(it)) },
                onSubscribe = { viewModel.onAction(ChannelAction.Subscribe) },
                onOpen = ::open,
                onLoadMore = { tab -> viewModel.onAction(loadNext(tab)) },
                onRetryTab = { tab -> viewModel.onAction(ChannelAction.RetrySection(tab)) },
                onRetry = { viewModel.onAction(ChannelAction.Retry) },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            hideShorts = prefs.isEnabled(PreferenceKeys.ENABLE_HIDE_SHORTS)
            viewModel.updateSignedIn(MediaSession.snapshot().signedIn)
        }
    }

    private fun open(card: TvChannelCard) {
        viewModel.onAction(when (card.kind) {
            TvChannelCardKind.Video -> ChannelAction.OpenVideo(card.video.id)
            TvChannelCardKind.Short -> ChannelAction.OpenShort(card.video.id)
            TvChannelCardKind.Live -> ChannelAction.OpenLive(card.video.id)
            TvChannelCardKind.Playlist -> ChannelAction.OpenPlaylist(card.video.id)
        })
    }

    private fun navigate(request: ChannelNavigationRequest) {
        when (request) {
            is ChannelNavigationRequest.Watch -> startActivity(TvWatchActivity.intent(this, request.videoId))
            is ChannelNavigationRequest.LiveStream -> startActivity(TvWatchActivity.intent(this, request.videoId))
            // A single Short plays in the TV player until a TV Shorts screen exists.
            is ChannelNavigationRequest.Short -> startActivity(TvWatchActivity.intent(this, request.shortId))
            is ChannelNavigationRequest.Playlist -> startActivity(TvPlaylistActivity.intent(this, request.playlistId))
            // The TV page issues no menu, share, post, link, search or verification requests.
            else -> Unit
        }
    }

    companion object {
        const val EXTRA_CHANNEL_ID = "tv_channel_id"

        fun intent(context: Context, channelId: String): Intent =
            Intent(context, TvChannelActivity::class.java).putExtra(EXTRA_CHANNEL_ID, channelId)

        fun loadNext(tab: ChannelTab): ChannelAction = when (tab) {
            ChannelTab.Shorts -> ChannelAction.LoadShortsNext
            ChannelTab.Live -> ChannelAction.LoadLiveNext
            ChannelTab.Playlists -> ChannelAction.LoadPlaylistsNext
            else -> ChannelAction.LoadVideosNext
        }
    }
}
