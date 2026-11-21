package com.hhst.youtubelite.tv.library

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.R
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.LocalLibraryStore
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.library.data.LibraryScenario
import com.hhst.youtubelite.library.domain.HistoryMenuAction
import com.hhst.youtubelite.library.domain.LibraryAction
import com.hhst.youtubelite.library.domain.LibraryOverlay
import com.hhst.youtubelite.library.presentation.CollectionViewModel
import com.hhst.youtubelite.library.presentation.HistoryViewModel
import com.hhst.youtubelite.player.QueueRepository
import com.hhst.youtubelite.playlist.domain.PlaylistAction
import com.hhst.youtubelite.playlist.domain.PlaylistContent
import com.hhst.youtubelite.playlist.presentation.PlaylistViewModel
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.routes.TvPlaylistRoute
import com.hhst.youtubelite.tv.routes.TvSignInRoute
import com.hhst.youtubelite.tv.routes.renderTvNotice
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.observeAccountChanges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin

/**
 * "View all" for a library row: History (remove an entry, pause/resume recording), Playlists,
 * one playlist as a grid (Watch later, Liked), or the play queue. Reuses the phone ViewModels as
 * HistoryActivity, PlaylistsActivity and PlaylistActivity build them.
 */
class TvLibraryPageActivity : TvActivity() {
    enum class Kind { History, Playlists, Playlist, Queue }

    private val sources by lazy { getKoin().get<ContentRepositories>() }
    private lateinit var kind: Kind
    private var history: HistoryViewModel? = null
    private var collection: CollectionViewModel? = null
    private var playlist: PlaylistViewModel? = null

    /** Device lane only: whether history recording is paused. Null on the account lane, which exposes no state. */
    private var devicePaused by mutableStateOf<Boolean?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = Kind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: Kind.History
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val signedIn = MediaSession.signedIn()
        when (kind) {
            Kind.History -> history = model { HistoryViewModel(sources.library, it, LibraryScenario.Content, signedIn, sources.saves) }
                .also { vm -> collectNotices { vm.actionNotices.collect { notice -> renderTvNotice(notice); refreshDevicePaused() } } }
            Kind.Playlists -> collection = model { CollectionViewModel(sources.library, it, LibraryScenario.Content, signedIn) }
            Kind.Playlist -> playlist = model { PlaylistViewModel(sources.playlist, it, id, saveRepository = sources.saves) }
                .also { vm -> collectNotices { vm.actionNotices.collect(::renderTvNotice) } }
            Kind.Queue -> Unit
        }
        observeAccountChanges(::syncSession)
        setTvContent { Page(id) }
    }

    private inline fun <reified T : ViewModel> model(crossinline create: (androidx.lifecycle.SavedStateHandle) -> T): T =
        ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <V : ViewModel> create(modelClass: Class<V>, extras: CreationExtras): V = create(extras.createSavedStateHandle()) as V
        })[T::class.java]

    private fun collectNotices(block: suspend () -> Unit) {
        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { block() } }
    }

    override fun onResume() {
        super.onResume()
        syncSession()
        refreshDevicePaused()
    }

    private fun syncSession() {
        val session = MediaSession.snapshot()
        history?.updateSession(session.signedIn, session.generation)
        collection?.updateSession(session.signedIn, session.generation)
        playlist?.updateSession(session.signedIn, session.generation)
    }

    private fun refreshDevicePaused() {
        if (kind != Kind.History) return
        if (MediaSession.signedIn()) { devicePaused = null; return }
        lifecycleScope.launch {
            // The first read may load the store from disk.
            val paused = withContext(Dispatchers.IO) { sources.localLibrary.paused() }
            if (!MediaSession.signedIn()) devicePaused = paused
        }
    }

    private fun watch(video: TvVideo) = startActivity(TvWatchActivity.intent(this, video.id))
    private fun signIn() = startActivity(TvSignInRoute.intent(this))

    @Composable
    private fun Page(id: String) {
        when (kind) {
            Kind.History -> HistoryPage(history!!)
            Kind.Playlists -> {
                val vm = collection!!
                val state by vm.uiState.collectAsStateWithLifecycle()
                TvLibraryPage(
                    title = stringResource(R.string.ui_playlists),
                    state = TvLibraryModel.playlists(state),
                    onOpen = { startActivity(TvPlaylistRoute.intent(this, it.id)) },
                    onRetry = { vm.onAction(LibraryAction.Retry) },
                    onSignIn = ::signIn,
                    onLoadMore = { vm.onAction(LibraryAction.LoadCollectionNext) },
                    onRetryMore = { vm.onAction(LibraryAction.LoadCollectionNext) },
                )
            }
            Kind.Playlist -> {
                val vm = playlist!!
                val state by vm.uiState.collectAsStateWithLifecycle()
                val title = (state.content as? PlaylistContent.Ready)?.page?.summary?.title?.takeIf(String::isNotBlank)
                    ?: stringResource(when (id) {
                        "LL" -> R.string.ui_liked_videos
                        "WL", LocalLibraryStore.WATCH_LATER -> R.string.ui_watch_later
                        else -> R.string.ui_playlist
                    })
                TvLibraryPage(
                    title = title,
                    state = TvLibraryModel.playlist(state),
                    onOpen = ::watch,
                    onRetry = { vm.onAction(PlaylistAction.Retry) },
                    onSignIn = ::signIn,
                    onLoadMore = { vm.onAction(PlaylistAction.LoadNext) },
                    onRetryMore = { vm.onAction(PlaylistAction.LoadNext) },
                )
            }
            Kind.Queue -> {
                val queue = getKoin().get<QueueRepository>()
                val state by queue.state.collectAsStateWithLifecycle()
                TvLibraryPage(
                    title = stringResource(R.string.queue),
                    state = TvLibraryModel.queue(state.items),
                    onOpen = ::watch,
                    onRetry = ::finish,
                    onSignIn = ::signIn,
                    emptyMessage = stringResource(R.string.tv_library_queue_empty),
                )
            }
        }
    }

    @Composable
    private fun HistoryPage(vm: HistoryViewModel) {
        val state by vm.uiState.collectAsStateWithLifecycle()
        val page = TvLibraryModel.history(state)
        TvLibraryPage(
            title = stringResource(R.string.tv_library_history),
            detail = if (page.status == TvPageStatus.Ready && state.live) stringResource(R.string.tv_library_hold_hint) else null,
            state = page,
            onOpen = ::watch,
            onRetry = { vm.onAction(LibraryAction.Retry) },
            onSignIn = ::signIn,
            onLoadMore = { vm.onAction(LibraryAction.LoadHistoryNext) },
            onRetryMore = { vm.onAction(LibraryAction.LoadHistoryNext) },
            // Removal is a live write (the save sheet exists only in production).
            onLongClick = if (state.live) { video -> vm.onAction(LibraryAction.OpenMenu(video.id)) } else null,
            emptyMessage = stringResource(R.string.tv_library_history_empty),
            actions = if (!state.live || page.status == TvPageStatus.SignedOut) null else ({
                val paused = devicePaused
                if (paused == null) {
                    // State unknown (account history): offer both.
                    TvButton(stringResource(R.string.ui_pause_watch_history),
                        { vm.onAction(LibraryAction.PauseHistory) }, Modifier.testTag(TAG_HISTORY_PAUSE), icon = AppIcon.Pause)
                    TvButton(stringResource(R.string.ui_resume_watch_history),
                        { vm.onAction(LibraryAction.ResumeHistory) }, Modifier.testTag(TAG_HISTORY_RESUME), icon = AppIcon.Play)
                } else {
                    // One toggle button, so focus stays on it when Pause turns into Resume.
                    TvButton(stringResource(if (paused) R.string.ui_resume_watch_history else R.string.ui_pause_watch_history),
                        { vm.onAction(if (paused) LibraryAction.ResumeHistory else LibraryAction.PauseHistory) },
                        Modifier.testTag(if (paused) TAG_HISTORY_RESUME else TAG_HISTORY_PAUSE),
                        icon = if (paused) AppIcon.Play else AppIcon.Pause)
                }
                if (state.undo != null) TvButton(stringResource(R.string.undo),
                    { vm.onAction(LibraryAction.UndoHistoryRemove) }, Modifier.testTag(TAG_HISTORY_UNDO), icon = AppIcon.History)
            }),
        )
        val menu = state.overlay as? LibraryOverlay.Menu
        if (menu != null) {
            val title = page.items.firstOrNull { it.id == menu.videoId }?.title.orEmpty()
            TvItemActionDialog(
                title = title,
                confirmLabel = stringResource(R.string.ui_remove_from_watch_history),
                onConfirm = { vm.onAction(LibraryAction.MenuSelected(HistoryMenuAction.RemoveFromHistory)) },
                onDismiss = { vm.onAction(LibraryAction.CloseOverlay) },
            )
        }
    }

    companion object {
        const val EXTRA_KIND = "tv_library_page_kind"
        const val EXTRA_ID = "tv_library_page_id"
        const val TAG_HISTORY_PAUSE = "tv-history-pause"
        const val TAG_HISTORY_RESUME = "tv-history-resume"
        const val TAG_HISTORY_UNDO = "tv-history-undo"

        fun intent(context: Context, kind: Kind, id: String? = null): Intent =
            Intent(context, TvLibraryPageActivity::class.java).putExtra(EXTRA_KIND, kind.name).putExtra(EXTRA_ID, id)
    }
}
