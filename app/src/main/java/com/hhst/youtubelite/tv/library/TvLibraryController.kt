package com.hhst.youtubelite.tv.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.R
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.LocalLibraryStore
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.library.data.LibraryScenario
import com.hhst.youtubelite.library.domain.LibraryAction
import com.hhst.youtubelite.library.presentation.YouViewModel
import com.hhst.youtubelite.player.QueueRepository
import com.hhst.youtubelite.playlist.domain.PlaylistAction
import com.hhst.youtubelite.playlist.presentation.PlaylistViewModel
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.routes.TvPlaylistRoute
import com.hhst.youtubelite.tv.routes.TvSignInRoute
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.observeAccountChanges
import org.koin.java.KoinJavaComponent.getKoin

/**
 * The Watch later and Liked rows: one unchanged [PlaylistViewModel] each, in a private store
 * cleared when the account changes so no row keeps another identity's list.
 */
class TvLibraryPlaylistModels : ViewModel() {
    private var store = ViewModelStore()
    private var generation: Long? = null

    fun get(id: String, sessionGeneration: Long, create: (SavedStateHandle) -> PlaylistViewModel): PlaylistViewModel {
        if (generation != sessionGeneration) {
            store.clear()
            store = ViewModelStore()
            generation = sessionGeneration
        }
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                create(SavedStateHandle(mapOf("playlistId" to id))) as T
        }
        return ViewModelProvider.create(store, factory)["playlist:$id", PlaylistViewModel::class]
    }

    override fun onCleared() = store.clear()
}

/**
 * Wires TV Library to the phone library data: [YouViewModel] (history and playlists shelves, as
 * YouActivity builds it), Watch later / Liked through [PlaylistViewModel], and the play queue.
 * Owned by [com.hhst.youtubelite.tv.TvMainActivity]; [Content] is the Library destination.
 */
class TvLibraryController(private val activity: ComponentActivity) {
    private val sources = getKoin().get<ContentRepositories>()
    private val queue = getKoin().get<QueueRepository>()
    private val playlistModels = ViewModelProvider(activity)[TvLibraryPlaylistModels::class.java]
    private var watchLater by mutableStateOf<PlaylistViewModel?>(null)
    private var liked by mutableStateOf<PlaylistViewModel?>(null)
    private var signedIn by mutableStateOf(MediaSession.signedIn())

    val you: YouViewModel = ViewModelProvider(activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == YouViewModel::class.java)
            return YouViewModel(sources.library, extras.createSavedStateHandle(), LibraryScenario.Content,
                MediaSession.signedIn(), sources.saves) as T
        }
    })["tv-library-you", YouViewModel::class.java]

    init {
        activity.observeAccountChanges(::refreshSession)
        refreshSession()
    }

    fun onResume() = refreshSession()

    /** Watch later is the device list when signed out on the local lane, the account's WL otherwise. */
    private fun watchLaterId(signedIn: Boolean) =
        if (!signedIn && sources.library.supportsLocal) LocalLibraryStore.WATCH_LATER else "WL"

    private fun refreshSession() {
        val session = MediaSession.snapshot()
        signedIn = session.signedIn
        you.updateSession(session.signedIn, session.generation)
        val available = session.signedIn || sources.library.supportsLocal
        watchLater = if (available) playlist(watchLaterId(session.signedIn), session.signedIn, session.generation) else null
        liked = if (session.signedIn) playlist(LIKED, true, session.generation) else null
    }

    private fun playlist(id: String, signedIn: Boolean, generation: Long): PlaylistViewModel =
        playlistModels.get(id, generation) { handle -> PlaylistViewModel(sources.playlist, handle, id, saveRepository = sources.saves) }
            .also { it.updateSession(signedIn, generation) }

    private fun open(shelf: TvLibraryShelf, video: TvVideo) {
        activity.startActivity(when (shelf) {
            TvLibraryShelf.Playlists -> TvPlaylistRoute.intent(activity, video.id)
            TvLibraryShelf.History, TvLibraryShelf.WatchLater, TvLibraryShelf.Liked, TvLibraryShelf.Queue ->
                TvWatchActivity.intent(activity, video.id)
        })
    }

    private fun viewAll(shelf: TvLibraryShelf) {
        activity.startActivity(when (shelf) {
            TvLibraryShelf.History -> TvLibraryPageActivity.intent(activity, TvLibraryPageActivity.Kind.History)
            TvLibraryShelf.Playlists -> TvLibraryPageActivity.intent(activity, TvLibraryPageActivity.Kind.Playlists)
            TvLibraryShelf.WatchLater -> TvLibraryPageActivity.intent(activity, TvLibraryPageActivity.Kind.Playlist,
                watchLaterId(signedIn))
            TvLibraryShelf.Liked -> TvLibraryPageActivity.intent(activity, TvLibraryPageActivity.Kind.Playlist, LIKED)
            TvLibraryShelf.Queue -> TvLibraryPageActivity.intent(activity, TvLibraryPageActivity.Kind.Queue)
        })
    }

    private fun retry(shelf: TvLibraryShelf) {
        when (shelf) {
            TvLibraryShelf.History -> you.onAction(LibraryAction.RetryHistoryShelf)
            TvLibraryShelf.Playlists -> you.onAction(LibraryAction.RetryPlaylistsShelf)
            TvLibraryShelf.WatchLater -> watchLater?.onAction(PlaylistAction.Retry)
            TvLibraryShelf.Liked -> liked?.onAction(PlaylistAction.Retry)
            TvLibraryShelf.Queue -> Unit
        }
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val youState by you.uiState.collectAsStateWithLifecycle()
        val watchLaterState = watchLater?.uiState?.collectAsStateWithLifecycle()?.value
        val likedState = liked?.uiState?.collectAsStateWithLifecycle()?.value
        val queueState by queue.state.collectAsStateWithLifecycle()
        val titles = TvLibraryShelf.entries.associateWith { stringResource(it.title) }
        val state = remember(youState, watchLaterState, likedState, queueState.items, signedIn, titles) {
            TvLibraryModel.build(youState, watchLaterState, likedState, queueState.items, signedIn) { titles.getValue(it) }
        }
        TvLibraryScreen(
            state = state,
            onOpen = ::open,
            onViewAll = ::viewAll,
            onRetryShelf = ::retry,
            onRetry = { you.onAction(LibraryAction.Retry) },
            onSignIn = { activity.startActivity(TvSignInRoute.intent(activity)) },
            modifier = modifier,
        )
    }

    private companion object {
        const val LIKED = "LL"
    }
}

/** Row and page titles. */
val TvLibraryShelf.title: Int
    get() = when (this) {
        TvLibraryShelf.History -> R.string.tv_library_history
        TvLibraryShelf.WatchLater -> R.string.ui_watch_later
        TvLibraryShelf.Playlists -> R.string.ui_playlists
        TvLibraryShelf.Liked -> R.string.ui_liked_videos
        TvLibraryShelf.Queue -> R.string.queue
    }
