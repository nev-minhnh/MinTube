package com.hhst.youtubelite.channel.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.ImeAction
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelPage
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.channel.domain.ChannelVideo
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.VideoRow
import com.hhst.youtubelite.ui.components.VideoRowSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.miniDockListPadding

/**
 * Search inside the channel: a search field under the tab row, matching playlists, then video
 * rows. Results keep a reading width on wide windows; the query and submission are the ViewModel's.
 */
@Composable
internal fun ChannelSearchTab(page: ChannelPage, width: Float, height: Float, listState: LazyListState,
    query: String, submitted: String, onAction: (ChannelAction) -> Unit,
    results: ChannelSection<List<ChannelVideo>>? = null,
    canPage: Boolean = false,
    append: AppendState = AppendState.Idle,
    subscribePending: Boolean = false,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); onAction(ChannelAction.SubmitSearch) }
    val videos = remember(page.videos, submitted, results) {
        when (results) {
            is ChannelSection.Ready -> results.value
            null -> page.videos.filter { submitted.isBlank() || it.title.contains(submitted, ignoreCase = true) }
            else -> emptyList()
        }
    }
    val playlists = remember(page.playlists, submitted) {
        page.playlists.filter { submitted.isBlank() || it.title.contains(submitted, ignoreCase = true) }
    }
    val inset = ChannelMetrics.inset(width)
    ObserveChannelPagination(listState, canPage, append) { onAction(ChannelAction.LoadSearchNext) }
    var appliedQuery by remember { mutableStateOf(submitted) }
    LaunchedEffect(submitted) {
        if (submitted != appliedQuery) { appliedQuery = submitted; listState.scrollToItem(1) }
    }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(Modifier.fillMaxSize().then(dock.modifier).testTag("channel-search-list"), state = listState, contentPadding = PaddingValues(bottom = dock.bottom)) {
        item { ChannelHeader(page, width, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Search, onAction) }
        item {
            Box(Modifier.fillMaxWidth().testTag("channel-search-form").padding(horizontal = inset, vertical = MinSpace.md)) {
                ChannelSearchField(query, onAction, submit)
            }
        }
        items(playlists, key = { "playlist-${it.id}" }, contentType = { "playlist" }) { playlist ->
            ChannelSearchColumn(inset) { ChannelPlaylistItem(playlist, onAction) }
        }
        when (results) {
            is ChannelSection.Loading -> item {
                Column(
                    Modifier.fillMaxWidth().testTag("channel-search-loading").clearAndSetSemantics {}
                        .padding(horizontal = inset, vertical = MinSpace.sm),
                    verticalArrangement = Arrangement.spacedBy(MinSpace.xl),
                ) { repeat(3) { VideoRowSkeleton(thumbnailWidth = ChannelMetrics.rowThumbnail(width, LocalDensity.current.fontScale)) } }
            }
            is ChannelSection.Failed -> item {
                SectionState(
                    stringResource(R.string.ui_something_went_wrong), tag = "channel-search-failed",
                    message = stringResource(R.string.ui_please_try_again), kind = StateKind.Error,
                    actionLabel = stringResource(R.string.retry), actionTag = "channel-search-retry",
                    onAction = { onAction(ChannelAction.RetrySearch) },
                )
            }
            else -> {
                if (videos.isEmpty() && playlists.isEmpty()) item {
                    SectionState(stringResource(R.string.ui_no_results_found), tag = "channel-search-empty", icon = AppIcon.Search)
                }
                items(videos, key = { it.id }, contentType = { "video" }) { video ->
                    ChannelSearchColumn(inset) { ChannelSearchVideoRow(video, page.profile.name, width, onAction) }
                }
            }
        }
        channelAppend("channel-search-append", canPage, append, R.string.ui_couldn_t_load_more_results_tap_to_retry) {
            onAction(ChannelAction.LoadSearchNext)
        }
    }
}

/** Search results sit in one reading-width column on wide windows. */
@Composable
private fun ChannelSearchColumn(inset: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = MinSpace.lg), contentAlignment = Alignment.TopStart) {
        Box(Modifier.widthIn(max = ChannelMetrics.DetailsMaxWidth * 1.5f).fillMaxWidth()) { content() }
    }
}

/** A pill field on the Surface tone, with clear and submit buttons at 48dp. */
@Composable
private fun ChannelSearchField(query: String, onAction: (ChannelAction) -> Unit, submit: () -> Unit) {
    Row(
        Modifier.widthIn(max = ChannelMetrics.DetailsMaxWidth).fillMaxWidth().heightIn(min = MinSpace.Touch)
            .background(DesignTokens.Surface, CircleShape).padding(start = MinSpace.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(query, { onAction(ChannelAction.QueryChanged(it)) },
            Modifier.weight(1f).padding(vertical = MinSpace.sm).testTag("channel-search-input"),
            textStyle = MinType.Body.copy(color = DesignTokens.Ink), singleLine = true,
            cursorBrush = SolidColor(DesignTokens.Accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) AppText(stringResource(R.string.ui_search_channel), MinType.Body, color = DesignTokens.Secondary, maxLines = 1)
                    field()
                }
            })
        if (query.isNotEmpty()) AppIconButton(AppIcon.Close, stringResource(R.string.a11y_clear_channel_query)) { onAction(ChannelAction.QueryChanged("")) }
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_submit_channel_search), onClick = submit)
    }
}

@Composable
private fun ChannelSearchVideoRow(video: ChannelVideo, channel: String, width: Float, onAction: (ChannelAction) -> Unit) {
    VideoRow(
        rememberDeArrowTitle(video.id, video.title), channel, video.artwork,
        { onAction(ChannelAction.OpenVideo(video.id)) },
        Modifier.testTag("channel-search-video-${video.id}"),
        secondaryMetadata = listOf(video.views, video.age).filter(String::isNotBlank).joinToString(" · "),
        durationLabel = video.duration,
        onMenu = { onAction(ChannelAction.OpenItemMenu(video.id)) },
        menuDescription = stringResource(R.string.a11y_more_search_actions_for, video.title),
        videoId = video.id,
        thumbnailTag = "channel-search-thumb-${video.id}",
        thumbnailDescription = stringResource(R.string.a11y_title_duration, video.title, video.duration),
        thumbnailWidth = ChannelMetrics.rowThumbnail(width, LocalDensity.current.fontScale),
    )
}
