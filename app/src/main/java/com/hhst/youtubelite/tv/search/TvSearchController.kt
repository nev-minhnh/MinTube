package com.hhst.youtubelite.tv.search

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.search.data.SearchScenario
import com.hhst.youtubelite.search.domain.SearchAction
import com.hhst.youtubelite.search.domain.SearchContent
import com.hhst.youtubelite.search.domain.SearchNavigationRequest
import com.hhst.youtubelite.search.domain.SearchResultType
import com.hhst.youtubelite.search.presentation.SearchResultsViewModel
import com.hhst.youtubelite.tv.channel.TvChannelActivity
import com.hhst.youtubelite.tv.playlist.TvPlaylistActivity
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.components.SearchRows
import com.hhst.youtubelite.ui.components.rememberSearchRows
import com.hhst.youtubelite.ui.observeAccountChanges
import com.hhst.youtubelite.ui.renderNotice
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Wires TV Search to the phone search stack without changing it: one [SearchResultsViewModel]
 * (as SearchResultsActivity builds it) for results, filters, paging and subscribe state, and the
 * phone's [com.hhst.youtubelite.data.media.MediaSearchSuggestions] (via [rememberSearchRows]) for
 * recent searches and live suggestions. Owned by [com.hhst.youtubelite.tv.TvMainActivity]; call
 * [onResume] from the activity. Construct in onCreate (voice search registers activity results).
 */
class TvSearchController(private val activity: ComponentActivity) {
    private val sources = getKoin().get<ContentRepositories>()
    private val prefs = getKoin().get<ExtensionManager>()
    private val voice = TvVoiceSearch(activity) { text -> submit(text) }
    private var hideShorts by mutableStateOf(prefs.isEnabled(PreferenceKeys.ENABLE_HIDE_SHORTS))
    private var voiceAvailable by mutableStateOf(false)
    /** The latest suggestion rows, so a voice result records history through the same path as a click. */
    private var rows: SearchRows? = null

    val viewModel: SearchResultsViewModel = ViewModelProvider(activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == SearchResultsViewModel::class.java)
            val handle = extras.createSavedStateHandle()
            // A first visit has no query: start idle instead of searching for "". A restored query reloads.
            val scenario = if (handle.get<String>("query").isNullOrBlank()) SearchScenario.Empty else SearchScenario.Results
            return SearchResultsViewModel(sources.search, handle, "", scenario, MediaSession.signedIn(),
                sources.videoActions, sources.saves, sources.avatars) as T
        }
    })["tv-search", SearchResultsViewModel::class.java]

    private var draft by mutableStateOf(viewModel.uiState.value.query)

    init {
        activity.observeAccountChanges(::refreshSession)
        refreshSession()
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.navigationRequests.collect(::navigate)
            }
        }
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.actionNotices.collect { activity.renderNotice(it) }
            }
        }
    }

    fun onResume() = refreshSession()

    private fun refreshSession() {
        val session = MediaSession.snapshot()
        viewModel.updateSession(session.signedIn, session.generation)
        hideShorts = prefs.isEnabled(PreferenceKeys.ENABLE_HIDE_SHORTS)
        voiceAvailable = voice.available
    }

    private fun navigate(request: SearchNavigationRequest) {
        when (request) {
            is SearchNavigationRequest.Watch -> activity.startActivity(TvWatchActivity.intent(activity, request.videoId))
            // A single Short plays in the TV player until a TV Shorts screen exists (as TvRoute does).
            is SearchNavigationRequest.Shorts -> activity.startActivity(TvWatchActivity.intent(activity, request.itemId))
            is SearchNavigationRequest.Channel -> activity.startActivity(TvChannelActivity.intent(activity, request.channelId))
            is SearchNavigationRequest.Playlist -> activity.startActivity(TvPlaylistActivity.intent(activity, request.playlistId))
            // The TV screen issues no menu, share, filter-sheet or verification requests.
            else -> Unit
        }
    }

    fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        rows?.onSubmit?.invoke(query) ?: sources.suggestions.record(query)
        draft = query
        val state = viewModel.uiState.value
        // Searching the same text again retries it; the ViewModel ignores an unchanged query.
        if (query == state.query) {
            if (state.content is SearchContent.Failed || state.content is SearchContent.Empty) viewModel.onAction(SearchAction.Retry)
        } else viewModel.onAction(SearchAction.SubmitSearch(query))
    }

    private fun open(item: TvSearchItem) {
        when (item) {
            is TvSearchItem.Video -> viewModel.onAction(SearchAction.OpenResult(item.video.id))
            is TvSearchItem.Channel -> viewModel.onAction(SearchAction.OpenChannel(item.id))
            is TvSearchItem.Playlist -> viewModel.onAction(SearchAction.OpenPlaylist(item.id))
        }
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val search by viewModel.uiState.collectAsStateWithLifecycle()
        val searchRows = rememberSearchRows(sources.suggestions, draft.trim())
        SideEffect { rows = searchRows }
        // Hiding Shorts removes their filter; a selected Shorts filter falls back to All, as on phone.
        LaunchedEffect(hideShorts, search.filters.type) {
            if (hideShorts && search.filters.type == SearchResultType.Shorts) viewModel.onAction(SearchAction.SelectResultType(SearchResultType.All))
        }
        val results = remember(search) { TvSearchModel.results(search) }
        val state = TvSearchUiState(
            draft = draft,
            query = search.query,
            recent = searchRows?.history.orEmpty(),
            suggestions = searchRows?.suggestions.orEmpty(),
            filters = search.filters,
            types = TvSearchModel.types(hideShorts),
            results = results,
            voiceAvailable = voiceAvailable,
        )
        TvSearchScreen(
            state = state,
            onDraftChange = { draft = it },
            onSubmit = ::submit,
            onRemoveRecent = { entry -> searchRows?.onRemove?.invoke(entry) },
            onVoice = voice::start,
            onSelectType = { viewModel.onAction(SearchAction.SelectResultType(it)) },
            onSelectDate = { viewModel.onAction(SearchAction.SelectUploadDate(it)) },
            onOpen = ::open,
            onOpenChannel = { viewModel.onAction(SearchAction.OpenChannel(it)) },
            onLoadMore = { viewModel.onAction(SearchAction.AutomaticLoadMore) },
            onRetryMore = { viewModel.onAction(SearchAction.LoadMore) },
            onRetry = { viewModel.onAction(SearchAction.Retry) },
            onVisibleVideos = { ids -> viewModel.onAction(SearchAction.VisibleResults(search.query, search.filters, ids)) },
            modifier = modifier,
        )
    }
}
