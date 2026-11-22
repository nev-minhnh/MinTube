package com.hhst.youtubelite.search.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.search.domain.SearchAction
import com.hhst.youtubelite.search.domain.SearchChannel
import com.hhst.youtubelite.search.domain.SearchResultsLayout
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.CenteredListGutter
import com.hhst.youtubelite.ui.design.miniDockListPadding

/** Channels filter results: one [SearchChannelRow] per channel inside the reading column. */
@Composable
fun ChannelResultsList(channels: List<SearchChannel>, state: LazyListState, onAction: (SearchAction) -> Unit, footer: (@Composable () -> Unit)? = null,
    subscriptions: Map<String, Boolean> = emptyMap(), subscriptionsPending: Set<String> = emptySet(), inset: Dp = MinSpace.lg) {
    CenteredListGutter(SearchResultsLayout.ColumnMaxWidth.dp) { gutter ->
        val dock = miniDockListPadding(MinSpace.xxl)
        LazyColumn(Modifier.fillMaxSize().then(dock.modifier).testTag("search-channels-list"), state = state,
            contentPadding = PaddingValues(start = inset + gutter, end = inset + gutter, top = MinSpace.sm, bottom = dock.bottom)) {
            items(channels, key = { it.id }) { channel ->
                SearchChannelRow(channel, onAction, subscribed = subscriptions[channel.id] == true,
                    pending = channel.id in subscriptionsPending, enabled = subscriptionsPending.isEmpty())
            }
            if (footer != null) item(key = "pagination") { footer() }
        }
    }
}
