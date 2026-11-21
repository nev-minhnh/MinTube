package com.hhst.youtubelite.tv.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeCatalog
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomeUiState
import com.hhst.youtubelite.home.presentation.HomeViewModel
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.routes.TvChannelRoute
import com.hhst.youtubelite.tv.shell.LocalTvShell
import com.hhst.youtubelite.tv.verify.TvVerification
import com.hhst.youtubelite.tv.verify.TvVerificationNotice
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.observeAccountChanges
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.java.KoinJavaComponent.getKoin

private const val REFRESH_START_MS = 2_000L

/**
 * Chip rows: one unchanged [HomeViewModel] per Home chip, created when its row is first shown
 * (vertical paging) and owned by a private store that is cleared when the account changes, so
 * no row keeps another identity's feed.
 */
class TvHomeChipModels : ViewModel() {
    private var store = ViewModelStore()
    private var generation: Long? = null

    fun get(chip: String, catalog: Boolean, sessionGeneration: Long, create: (SavedStateHandle) -> HomeViewModel): HomeViewModel {
        if (generation != sessionGeneration) reset(sessionGeneration)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                create(SavedStateHandle(mapOf("chip" to chip, "catalog" to catalog))) as T
        }
        return ViewModelProvider.create(store, factory)["chip:$catalog:$chip", HomeViewModel::class]
    }

    fun reset(sessionGeneration: Long?) {
        store.clear()
        store = ViewModelStore()
        generation = sessionGeneration
    }

    override fun onCleared() = store.clear()
}

/**
 * Wires TV Home to the phone Home data: the main [HomeViewModel] (as HomeActivity builds it) plus
 * [TvHomeChipModels]. Owned by [com.hhst.youtubelite.tv.TvMainActivity]; [Content] is the Home
 * destination of the shell. Call [onResume] from the activity.
 */
class TvHomeController(private val activity: ComponentActivity) {
    private val sources = getKoin().get<ContentRepositories>()
    private val prefs = getKoin().get<ExtensionManager>()
    private val chipModels = ViewModelProvider(activity)[TvHomeChipModels::class.java]
    private val chipStates = mutableStateMapOf<String, HomeViewModel>()
    private var trendingInVietnam by mutableStateOf(prefs.isEnabled(PreferenceKeys.TRENDING_REGION_VIETNAM))
    private var warmed: Pair<Long, String>? = null
    /** Rows, scroll and focus survive leaving Home for another rail destination. */
    private val view = TvHomeViewState()
    /** Last rail Refresh handled, and the focus request for the first card of its new rows. */
    private var handledRefresh = 0
    private var focusFirst by mutableIntStateOf(0)

    val main: HomeViewModel = ViewModelProvider(activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == HomeViewModel::class.java)
            return HomeViewModel(
                sources.home, extras.createSavedStateHandle(), loadOnStart = true,
                saveRepository = sources.saves, snapshots = sources.snapshots,
                accountKey = ::snapshotKey,
                warm = ::warm,
            ) as T
        }
    })["tv-home-main", HomeViewModel::class.java]

    /** A solved traffic check grants the feed one retry, as on phone. */
    private val verification = activity.registerForActivityResult(TvVerification.Contract) { challenge ->
        if (challenge != null) main.verificationCompleted(challenge)
    }

    init {
        activity.observeAccountChanges { refreshSession() }
        refreshSession()
    }

    /** Same partition as phone Home: snapshot rows carry server text in the request hl/gl. */
    private fun snapshotKey(): String {
        val owner = MediaSession.snapshot().accountId ?: "visitor"
        return MediaSession.contentLocale()?.scope(owner) ?: owner
    }

    fun onResume() = refreshSession()

    /**
     * Follows the session (an account or content-locale change still reloads, as it must). Unlike
     * phone Home there is no quiet refresh on resume (watch-report signal, staleness): coming back
     * from Watch or to the app keeps the rows and the focused card; the rail's Refresh reloads.
     */
    private fun refreshSession() {
        val session = MediaSession.snapshot()
        main.updateSession(session.signedIn, session.generation, null)
        main.setHideShorts(prefs.isEnabled(PreferenceKeys.ENABLE_HIDE_SHORTS))
        trendingInVietnam = prefs.isEnabled(PreferenceKeys.TRENDING_REGION_VIETNAM)
        if (chipStates.values.any { it.uiState.value.sessionGeneration != session.generation }) {
            chipModels.reset(session.generation)
            chipStates.clear()
        }
    }

    /** The rail's Refresh: Home's own refresh on every row, then focus on the first new card. */
    private suspend fun refresh() {
        view.refreshPending = true
        try {
            view.memory.clear()
            main.onAction(HomeAction.Refresh)
            chipStates.values.forEach { it.onAction(HomeAction.Refresh) }
            withTimeoutOrNull(REFRESH_START_MS) { main.uiState.first { it.busy() } }
            main.uiState.first { !it.busy() }
            focusFirst++
        } finally {
            view.refreshPending = false
        }
    }

    private fun HomeUiState.busy(): Boolean = when (val current = content) {
        HomeContent.Loading -> true
        is HomeContent.Feed -> current.refreshing
        is HomeContent.Empty -> current.refreshing
        HomeContent.Failed -> false
    }

    private fun chip(chip: String): HomeViewModel {
        chipStates[chip]?.let { return it }
        val session = MediaSession.snapshot()
        val catalog = TvHomeRows.catalog(main.uiState.value) && chip in HomeCatalog.filters
        val model = chipModels.get(chip, catalog, session.generation) { handle ->
            HomeViewModel(sources.home, handle, loadOnStart = true)
        }
        model.updateSession(session.signedIn, session.generation, null)
        chipStates[chip] = model
        return model
    }

    private fun warm(id: String) {
        val candidate = MediaSession.snapshot().generation to id
        if (warmed == candidate) return
        warmed = candidate
        getKoin().get<com.hhst.youtubelite.extractor.Extractor>().warm(id)
    }

    private fun open(video: TvVideo) {
        // Shorts row cards open the TV Shorts player at that Short.
        activity.startActivity(if (video.short) com.hhst.youtubelite.tv.shorts.TvShortsActivity.intent(activity, video.id)
            else TvWatchActivity.intent(activity, video.id))
    }

    private fun model(rowKey: String): HomeViewModel? = when {
        rowKey == TvHomeRows.MAIN || rowKey == TvHomeRows.SHORTS -> main
        rowKey.startsWith("chip:") -> chipStates[rowKey.removePrefix("chip:")]
        else -> null
    }

    private fun retry(model: HomeViewModel) {
        model.onAction(if (model.uiState.value.content is HomeContent.Feed) HomeAction.LoadNext else HomeAction.Refresh)
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val refreshRequests = LocalTvShell.current?.refreshRequests ?: 0
        LaunchedEffect(refreshRequests) {
            if (refreshRequests == handledRefresh) return@LaunchedEffect
            handledRefresh = refreshRequests
            refresh()
        }
        val mainState by main.uiState.collectAsStateWithLifecycle()
        val chipUiStates = buildMap {
            chipStates.forEach { (chip, model) -> key(chip) { put(chip, model.uiState.collectAsState().value) } }
        }
        val state = remember(mainState, chipUiStates, trendingInVietnam) {
            TvHomeRows.build(mainState, chipUiStates, trendingInVietnam)
        }
        // Chip VMs are bound to the main feed's mode (catalog or feed); a mode switch rebuilds them.
        val catalog = TvHomeRows.catalog(mainState)
        LaunchedEffect(catalog, mainState.sessionGeneration) {
            val stale = chipStates.keys.any { key -> key !in TvHomeRows.chips(mainState) }
            if (stale) {
                chipModels.reset(mainState.sessionGeneration)
                chipStates.clear()
            }
        }
        val challenge = mainState.verification
        if (challenge != null && mainState.content !is HomeContent.Feed) {
            TvVerificationNotice(onVerify = { verification.launch(challenge.challengeUrl) }, blocking = true, modifier = modifier)
            return
        }
        Column(modifier) {
            if (challenge != null) TvVerificationNotice(onVerify = { verification.launch(challenge.challengeUrl) }, blocking = false)
            HomeScreen(state)
        }
    }

    @Composable
    private fun HomeScreen(state: TvHomeUiState) {
        TvHomeScreen(
            state = state,
            onOpen = ::open,
            onLoadMore = { key -> model(key)?.onAction(HomeAction.AutomaticLoadNext) },
            onRetryRow = { key -> model(key)?.let(::retry) },
            onRetry = { main.onAction(HomeAction.Refresh) },
            onRowShown = { row -> row.chip?.let(::chip) },
            onFocusDwell = { video -> if (!video.short) warm(video.id) },
            onOpenChannel = { video -> activity.startActivity(TvChannelRoute.intent(activity, video.channelId)) },
            view = view,
            focusFirst = focusFirst,
        )
    }
}
