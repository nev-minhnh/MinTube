package com.hhst.youtubelite.tv.subscriptions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.data.media.MediaSession
import com.hhst.youtubelite.subscriptions.data.SubscriptionsScenario
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsAction
import com.hhst.youtubelite.subscriptions.presentation.SubscriptionsViewModel
import com.hhst.youtubelite.tv.routes.TvChannelRoute
import com.hhst.youtubelite.tv.routes.TvSignInRoute
import com.hhst.youtubelite.tv.watch.TvWatchActivity
import com.hhst.youtubelite.ui.observeAccountChanges
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Wires TV Subscriptions to the unchanged [SubscriptionsViewModel] (built as SubscriptionsActivity
 * builds it). Owned by [com.hhst.youtubelite.tv.TvMainActivity]; [Content] is the destination.
 */
class TvSubscriptionsController(private val activity: ComponentActivity) {
    private val sources = getKoin().get<ContentRepositories>()

    val model: SubscriptionsViewModel = ViewModelProvider(activity, object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == SubscriptionsViewModel::class.java)
            return SubscriptionsViewModel(sources.subscriptions, extras.createSavedStateHandle(), SubscriptionsScenario.Content,
                MediaSession.signedIn(), sources.saves) as T
        }
    })["tv-subscriptions", SubscriptionsViewModel::class.java]

    init {
        activity.observeAccountChanges(::syncSession)
        syncSession()
    }

    /** As on phone: follow the account, and re-read the channel list when it is stale. */
    fun onResume() {
        syncSession()
        model.onForeground()
    }

    private fun syncSession() {
        MediaSession.snapshot().let { model.updateSession(it.signedIn, it.generation) }
    }

    @Composable
    fun Content(modifier: Modifier = Modifier) {
        val raw by model.uiState.collectAsStateWithLifecycle()
        val state = remember(raw) { TvSubscriptionsModel.build(raw) }
        TvSubscriptionsScreen(
            state = state,
            onOpenVideo = { activity.startActivity(TvWatchActivity.intent(activity, it.id)) },
            onOpenChannel = { activity.startActivity(TvChannelRoute.intent(activity, it.id)) },
            onLoadMore = { model.onAction(SubscriptionsAction.LoadNext) },
            onRetryMore = { model.onAction(SubscriptionsAction.LoadNext) },
            onLoadMoreChannels = { model.onAction(SubscriptionsAction.LoadMoreChannels) },
            onRetryChannels = { model.onAction(SubscriptionsAction.RetryChannels) },
            onRetry = { model.onAction(SubscriptionsAction.Retry) },
            onSignIn = { activity.startActivity(TvSignInRoute.intent(activity)) },
            modifier = modifier,
        )
    }
}
