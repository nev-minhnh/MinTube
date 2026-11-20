package com.hhst.youtubelite.subscriptions.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as listItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.presentation.AppendFooter
import com.hhst.youtubelite.channel.presentation.LoadingIndicator
import com.hhst.youtubelite.channel.presentation.StatePage
import com.hhst.youtubelite.core.VerificationRequired
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.subscriptions.domain.SubscribedChannel
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsAction
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsChannels
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsChip
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsContent
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsOverlay
import com.hhst.youtubelite.subscriptions.domain.SubscriptionsUiState
import com.hhst.youtubelite.ui.components.ChannelRow
import com.hhst.youtubelite.ui.components.ChannelRowSkeleton
import com.hhst.youtubelite.ui.components.DurationBadge
import com.hhst.youtubelite.ui.components.FilterChips
import com.hhst.youtubelite.ui.components.MainNavigationBar
import com.hhst.youtubelite.ui.components.MainTab
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.ThumbnailProgress
import com.hhst.youtubelite.ui.components.VideoCardSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppMotion
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalHideShorts
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.RefreshableContent
import com.hhst.youtubelite.ui.design.VerificationBanner
import com.hhst.youtubelite.ui.design.VerificationCard
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.watch.domain.WatchProvisionalStore
import com.hhst.youtubelite.watch.domain.toProvisional
import kotlin.math.roundToInt

/**
 * Subscriptions: title bar, channel strip, filter chips and the feed on the Mint card family.
 * The header slides away while the feed scrolls and returns on a reverse drag; every load, retry
 * and selection is still the ViewModel's.
 */
@Composable
fun SubscriptionsScreen(state: SubscriptionsUiState, onAction: (SubscriptionsAction) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().background(DesignTokens.Background).testTag("subscriptions-screen")) {
        // The window class comes from the space this screen really has (split screen, test frames).
        val window = WindowClass.of(maxWidth.value.toInt())
        val gridState = rememberLazyGridState()
        val channelListState = rememberLazyListState()
        val channelStripState = rememberLazyListState()
        var headerHidden by rememberSaveable { mutableStateOf(false) }
        var displayedChannel by rememberSaveable { mutableStateOf(state.selectedChannel?.channelId) }
        var displayedChip by rememberSaveable { mutableStateOf(state.selectedChip) }
        val headerFraction by animateFloatAsState(if (headerHidden) 0f else 1f,
            AppMotion.scrollResponse(LocalMotionScale.current), label = "subscriptions-header")
        val headerScroll = remember(gridState) { object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) headerHidden = false
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Only hide after the feed moves, so horizontal strips and pull-to-refresh keep the header.
                if (source == NestedScrollSource.UserInput && consumed.y < 0f && gridState.canScrollBackward) headerHidden = true
                return Offset.Zero
            }
        } }
        LaunchedEffect(gridState) {
            snapshotFlow { gridState.canScrollBackward }.collect { scrolled ->
                if (!scrolled) headerHidden = false
            }
        }
        val headerRequired = !state.signedIn || state.overlay == SubscriptionsOverlay.Channels ||
            state.content != SubscriptionsContent.Loading && state.content !is SubscriptionsContent.Feed
        LaunchedEffect(headerRequired) {
            if (headerRequired) headerHidden = false
        }
        val leadingUpdate = (state.channels as? SubscriptionsChannels.Ready)?.channels
            ?.firstOrNull()?.takeIf { it.hasNewContent }?.channelId
        LaunchedEffect(leadingUpdate) {
            // A keyed LazyRow otherwise keeps the previous first channel anchored after reordering.
            if (leadingUpdate != null && state.selectedChannel == null) channelStripState.scrollToItem(0)
        }
        LaunchedEffect(state.selectedChannel?.channelId) {
            val id = state.selectedChannel?.channelId ?: return@LaunchedEffect
            val index = (state.channels as? SubscriptionsChannels.Ready)?.channels?.indexOfFirst { it.channelId == id } ?: -1
            if (index >= 0) channelStripState.animateScrollToItem((index - 1).coerceAtLeast(0))
        }
        LaunchedEffect(state.selectedChannel?.channelId, state.selectedChip) {
            if (displayedChannel != state.selectedChannel?.channelId || displayedChip != state.selectedChip) {
                gridState.scrollToItem(0)
                headerHidden = false
            }
            displayedChannel = state.selectedChannel?.channelId
            displayedChip = state.selectedChip
        }
        val showingChannels = state.overlay == SubscriptionsOverlay.Channels
        Column(Modifier.fillMaxSize().then(if (state.overlay != SubscriptionsOverlay.None && !showingChannels) Modifier.clearAndSetSemantics {} else Modifier)) {
            if (showingChannels) {
                ChannelsOverlay(state.channels, channelListState, window, onAction, Modifier.weight(1f))
            } else {
                // Measure the full stack (including large text), then release its space as it slides away.
                Column(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
                    val header = measurable.measure(constraints.copy(minHeight = 0))
                    val height = (header.height * headerFraction).roundToInt()
                    layout(header.width, height) { header.placeRelative(0, height - header.height) }
                }) {
                    SubscriptionsTopBar(state.selectedChannel != null, onAction)
                    if (state.signedIn) {
                        ChannelAvatarStrip(state.channels, state.selectedChannel?.channelId, channelStripState, window, onAction)
                        val selectedChannel = state.selectedChannel
                        if (selectedChannel == null) SubscriptionsChipStrip(state.selectedChip, state.chips, window, onAction)
                        else SelectedChannelRow(selectedChannel, window, onAction)
                    }
                }
                RefreshableContent(value = state, refreshing = state.content == SubscriptionsContent.Loading,
                    onRefresh = { onAction(SubscriptionsAction.Retry) }, modifier = Modifier.weight(1f).fillMaxWidth().nestedScroll(headerScroll),
                    enabled = state.signedIn && state.overlay == SubscriptionsOverlay.None, contentKey = Triple(state.signedIn, state.selectedChannel?.channelId, state.selectedChip), tag = "subscriptions-refresh",
                    canPull = { !gridState.canScrollBackward }) { state ->
                    val verification = state.verification
                    when (val content = state.content) {
                        SubscriptionsContent.Loading -> SubscriptionsSkeleton(window)
                        SubscriptionsContent.Failed -> if (verification != null) VerificationCard(
                            verification,
                            onVerify = { onAction(SubscriptionsAction.OpenVerification(verification.challengeUrl)) },
                            onDismiss = { onAction(SubscriptionsAction.DismissVerification) },
                            modifier = Modifier.testTag("subscriptions-verification"),
                        ) else SubscriptionsError { onAction(SubscriptionsAction.Retry) }
                        is SubscriptionsContent.SignedOut -> StatePage {
                            StateMessage(
                                signedOutText(content.prompt.title), message = signedOutText(content.prompt.message),
                                icon = AppIcon.Subscriptions, actionLabel = signedOutText(content.prompt.action),
                                onAction = { onAction(SubscriptionsAction.SignIn) }, tag = "subscriptions-signed-out",
                            )
                        }
                        is SubscriptionsContent.Feed -> SubscriptionsFeed(content, window, gridState, state.verification, onAction)
                    }
                }
            }
            MainNavigationBar("main", state.signedIn, null, selected = MainTab.Subscriptions) { tab -> onAction(SubscriptionsAction.Destination(tab)) }
        }
    }
}

/** The repository's prompt is English; the copy that has a translation is shown translated. */
@Composable
private fun signedOutText(text: String): String = when (text) {
    "Don't miss new videos" -> stringResource(R.string.ui_don_t_miss_new_videos)
    "Sign in to see updates from your favorite YouTube channels" ->
        stringResource(R.string.ui_sign_in_to_see_updates_from_your_favorite_youtube_channels)
    "Sign in" -> stringResource(R.string.ui_sign_in)
    else -> text
}

/** Same geometry as `MinTopBar`; written out because the title and back button carry this screen's tags. */
@Composable
private fun SubscriptionsTopBar(selectedChannel: Boolean, onAction: (SubscriptionsAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).background(DesignTokens.Background).testTag("subscriptions-topbar")
            .padding(start = if (selectedChannel) MinSpace.xs else MinSpace.lg, end = MinSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectedChannel) AppIconButton(AppIcon.Back, stringResource(R.string.a11y_back),
            modifier = Modifier.testTag("subscriptions-back-to-all")) { onAction(SubscriptionsAction.SelectChannel(null)) }
        AppText(stringResource(R.string.subscriptions_title), MinType.Title,
            Modifier.weight(1f).testTag("subscriptions-title").semantics { heading() }, maxLines = 1)
        AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search_youtube)) { onAction(SubscriptionsAction.OpenSearch) }
    }
}

@Composable
private fun SubscriptionsChipStrip(selected: SubscriptionsChip, chips: List<SubscriptionsChip>, window: WindowClass, onAction: (SubscriptionsAction) -> Unit) {
    // Today and Posts have no server lane (FEATURE_MATRIX SUB-03); they never get a chip.
    val shown = remember(chips) { chips.filterNot { it == SubscriptionsChip.Today || it == SubscriptionsChip.Posts } }
    FilterChips(
        shown.map { stringResource(it.labelResource) }, shown.indexOf(selected),
        { index -> onAction(SubscriptionsAction.SelectChip(shown[index])) },
        contentPadding = PaddingValues(horizontal = MinSpace.pageInset(window)), tag = "subscriptions-chips",
    )
}

/** Shown instead of the chips while one channel's feed is selected. */
@Composable
private fun SelectedChannelRow(channel: SubscribedChannel, window: WindowClass, onAction: (SubscriptionsAction) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = MinSpace.pageInset(window), end = MinSpace.pageInset(window) - MinSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(channel.name, MinType.Subtitle, Modifier.weight(1f).padding(end = MinSpace.sm), maxLines = 1)
        MinButton(stringResource(R.string.subscription_visit_channel),
            { onAction(SubscriptionsAction.OpenChannel(channel.channelId)) },
            kind = MinButtonKind.Tonal, icon = AppIcon.Person, testTag = "subscriptions-visit-channel")
    }
}

private val StripAvatar = 56.dp

@Composable
private fun ChannelAvatarStrip(channels: SubscriptionsChannels, selectedChannelId: String?, listState: LazyListState, window: WindowClass, onAction: (SubscriptionsAction) -> Unit) {
    val allDescription = stringResource(R.string.all_subscriptions)
    Row(Modifier.fillMaxWidth().testTag("subscriptions-channels"), verticalAlignment = Alignment.CenterVertically) {
        LazyRow(Modifier.weight(1f), state = listState, verticalAlignment = Alignment.Top,
            contentPadding = PaddingValues(start = MinSpace.pageInset(window) - MinSpace.sm, top = MinSpace.xs, bottom = MinSpace.sm)) {
            when (channels) {
                is SubscriptionsChannels.Ready -> listItems(channels.channels, key = { it.channelId }) { channel ->
                    val isSelected = channel.channelId == selectedChannelId
                    val description = if (channel.hasNewContent) stringResource(R.string.subscription_new_activity, channel.name) else channel.name
                    Column(Modifier.width(76.dp).clip(MinRadius.Field).clickable(role = Role.Tab) {
                        onAction(SubscriptionsAction.SelectChannel(channel.channelId))
                    }.semantics(mergeDescendants = true) { contentDescription = description; selected = isSelected }
                        .testTag("subs-strip-${channel.channelId}").padding(vertical = MinSpace.xs),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        SubscriptionAvatar(channel, StripAvatar, "subs-new-${channel.channelId}", isSelected)
                        AppText(channel.name,
                            if (isSelected) MinType.Caption.copy(fontWeight = FontWeight.Medium) else MinType.Caption,
                            Modifier.padding(top = MinSpace.xs, start = MinSpace.xs, end = MinSpace.xs),
                            color = if (isSelected) DesignTokens.AccentText else DesignTokens.Secondary, maxLines = 1)
                    }
                }
                SubscriptionsChannels.Loading -> items(6) {
                    Column(Modifier.width(76.dp).padding(vertical = MinSpace.xs).clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
                        SkeletonBox(Modifier.size(StripAvatar), CircleShape)
                        SkeletonLine(Modifier.padding(top = MinSpace.sm, start = MinSpace.md, end = MinSpace.md), height = 10.dp)
                    }
                }
                SubscriptionsChannels.Failed -> item {
                    MinButton(stringResource(R.string.retry), { onAction(SubscriptionsAction.RetryChannels) },
                        Modifier.padding(start = MinSpace.sm), kind = MinButtonKind.Text, icon = AppIcon.Refresh)
                }
            }
        }
        Box(Modifier.widthIn(min = 56.dp).heightIn(min = 64.dp).clip(MinRadius.Field).clickable(role = Role.Button) {
                onAction(SubscriptionsAction.ShowChannels)
            }.testTag("subscriptions-manage").semantics { contentDescription = allDescription }
            .padding(horizontal = MinSpace.md), contentAlignment = Alignment.Center) {
            AppText(stringResource(R.string.download_filter_all), MinType.Label, color = DesignTokens.AccentText, maxLines = 1)
        }
    }
}

/** Round avatar; an accent ring marks the selected channel and an accent dot marks new uploads. */
@Composable
private fun SubscriptionAvatar(channel: SubscribedChannel, size: Dp, badgeTag: String, selected: Boolean = false) {
    Box(Modifier.size(size)) {
        Box(Modifier.fillMaxSize().then(if (selected) Modifier.border(2.dp, DesignTokens.Accent, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape)) { SubscriptionsArtwork(channel.avatar, Modifier.fillMaxSize()) }
        if (channel.hasNewContent) NewContentDot(Modifier.align(Alignment.BottomEnd).testTag(badgeTag))
    }
}

@Composable
private fun NewContentDot(modifier: Modifier = Modifier) {
    Box(modifier.size(14.dp).background(DesignTokens.Background, CircleShape).padding(2.dp).background(DesignTokens.Accent, CircleShape))
}

/** First load: the card grid in outline, one screen's worth, never scrollable. */
@Composable
private fun SubscriptionsSkeleton(window: WindowClass) {
    val compact = window == WindowClass.Compact
    val inset = if (compact) 0.dp else MinSpace.pageInset(window)
    Column(
        Modifier.fillMaxSize().testTag("subscriptions-skeleton").clearAndSetSemantics {}
            .verticalScroll(rememberScrollState(), enabled = false).padding(horizontal = inset),
        verticalArrangement = Arrangement.spacedBy(MinSpace.xl),
    ) {
        repeat(if (compact) 3 else 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.lg)) {
                repeat(window.feedColumns) { VideoCardSkeleton(Modifier.weight(1f), fullBleed = compact) }
            }
        }
    }
}

@Composable
private fun SubscriptionsFeed(content: SubscriptionsContent.Feed, window: WindowClass, gridState: LazyGridState, verification: VerificationRequired?, onAction: (SubscriptionsAction) -> Unit) {
    val compact = window == WindowClass.Compact
    val inset = if (compact) 0.dp else MinSpace.pageInset(window)
    val hidden = LocalHideShorts.current
    val visibleItems = remember(content.page.items, hidden) { content.page.items.filterNot { hidden && it.short } }
    val nearEnd by remember(gridState, visibleItems.size) { derivedStateOf {
        (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= visibleItems.size - 2
    } }
    LaunchedEffect(nearEnd, content.page.nextKey, content.append) {
        // Scroll pages the feed only while it shows rows; an empty filtered feed is
        // continued by the view model within its bound, never by this effect alone.
        if (nearEnd && visibleItems.isNotEmpty() && content.page.nextKey != null && content.append == AppendState.Idle) {
            onAction(SubscriptionsAction.LoadNext)
        }
    }
    Column(Modifier.fillMaxSize()) {
        verification?.let { challenge ->
            VerificationBanner(challenge,
                onVerify = { onAction(SubscriptionsAction.OpenVerification(challenge.challengeUrl)) },
                onDismiss = { onAction(SubscriptionsAction.DismissVerification) },
                modifier = Modifier.padding(horizontal = MinSpace.pageInset(window)).padding(top = MinSpace.sm, bottom = MinSpace.sm)
                    .testTag("subscriptions-verification-banner"))
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(window.feedColumns), state = gridState,
            modifier = Modifier.fillMaxSize().testTag("subscriptions-feed"),
            contentPadding = PaddingValues(start = inset, end = inset, bottom = MinSpace.xxl),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.lg),
        ) {
            items(visibleItems, key = { it.id }, contentType = { "video" }) { item ->
                Box((if (LocalMotionScale.current > 0f) Modifier.animateItem() else Modifier).padding(bottom = MinSpace.xl)) {
                    SubscriptionsVideoCard(item, compact = compact, onAction)
                }
            }
            if (visibleItems.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    when {
                        // The server said the feed ends here; the filter found nothing in it.
                        content.page.nextKey == null -> StateMessage(
                            stringResource(R.string.ui_no_videos),
                            message = stringResource(R.string.ui_new_videos_from_your_subscriptions_will_show_up_here),
                            icon = AppIcon.Subscriptions, tag = "subscriptions-empty",
                        )
                        // An empty filtered page is not the end; the model keeps reading a
                        // bounded stretch, then this row hands control back to the user.
                        content.append == AppendState.Idle && content.emptyAutoLoads >= 3 -> Box(
                            Modifier.fillMaxWidth().padding(top = MinSpace.huge), contentAlignment = Alignment.Center,
                        ) {
                            MinButton(stringResource(R.string.load_more), { onAction(SubscriptionsAction.LoadNext) },
                                kind = MinButtonKind.Tonal, testTag = "subscriptions-load-more")
                        }
                        else -> Column(Modifier.fillMaxWidth().padding(top = MinSpace.huge, start = MinSpace.xxl, end = MinSpace.xxl),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingIndicator()
                            AppText(stringResource(R.string.ui_looking_for_matching_videos), MinType.Body,
                                Modifier.padding(top = MinSpace.md), color = DesignTokens.Secondary)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }, key = "append") {
                AppendFooter(content.append, stringResource(R.string.ui_something_went_wrong_tap_to_retry),
                    { onAction(SubscriptionsAction.LoadNext) })
            }
        }
    }
}

@Composable
internal fun SubscriptionsArtwork(asset: String, modifier: Modifier = Modifier) {
    Artwork(asset, modifier)
}

/**
 * The feed card. Same anatomy as the shared `VideoCard` (16:9 thumbnail, badge, avatar, two-line
 * title, metadata, 48dp overflow); written here because the avatar carries its own tag and label.
 */
@Composable
internal fun SubscriptionsVideoCard(item: FeedItem.Video, compact: Boolean, onAction: (SubscriptionsAction) -> Unit) {
    val open = {
        WatchProvisionalStore.put(item.toProvisional())
        onAction(SubscriptionsAction.OpenVideo(item.id))
    }
    Column(Modifier.fillMaxWidth().testTag("subs-item-${item.id}").clickable(role = Role.Button, onClick = open)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(if (compact) RectangleShape else MinRadius.Card)) {
            VideoArtwork(item.id, item.artwork, Modifier.fillMaxSize())
            if (item.duration.isNotBlank()) DurationBadge(item.duration, item.live)
            item.progress?.let { ThumbnailProgress(it) }
        }
        Row(Modifier.fillMaxWidth().padding(start = if (compact) MinSpace.sm else 0.dp, top = MinSpace.sm), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(MinSpace.Touch).clip(CircleShape).clickable(role = Role.Button) {
                onAction(SubscriptionsAction.OpenChannel(item.channelId))
            }.testTag("subs-channel-${item.channelId}").appDescription(R.string.a11y_go_to_channel, item.author),
                contentAlignment = Alignment.Center) {
                SubscriptionsArtwork(item.avatar.orEmpty(), Modifier.size(36.dp).clip(CircleShape))
            }
            Spacer(Modifier.width(MinSpace.xs))
            Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
                AppText(rememberDeArrowTitle(item.id, item.title), MinType.Subtitle, maxLines = 2)
                val metadata = listOf(item.author, item.metadata).filter(String::isNotBlank).joinToString(" · ")
                if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs),
                    color = DesignTokens.Secondary, maxLines = 2)
            }
            AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, item.title)) {
                onAction(SubscriptionsAction.OpenMenu(item.id))
            }
        }
    }
}

@Composable
internal fun SubscriptionsError(onRetry: () -> Unit) {
    StatePage {
        StateMessage(
            stringResource(R.string.ui_something_went_wrong), message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error, actionLabel = stringResource(R.string.retry), onAction = onRetry, tag = "subscriptions-error",
        )
    }
}

/** The full list of subscribed channels; a row selects that channel's feed. */
@Composable
private fun ChannelsOverlay(channels: SubscriptionsChannels, listState: LazyListState, window: WindowClass, onAction: (SubscriptionsAction) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(DesignTokens.Background).testTag("channels-overlay")) {
        Column(Modifier.fillMaxSize()) {
            MinTopBar(title = stringResource(R.string.all_subscriptions), onBack = { onAction(SubscriptionsAction.CloseChannels) },
                tag = "channels-topbar")
            // A reading-width column keeps names and the new-content dot together on wide windows.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                val page = Modifier.widthIn(max = 720.dp).fillMaxSize()
                when (channels) {
                    SubscriptionsChannels.Loading -> Column(
                        page.testTag("channels-skeleton").clearAndSetSemantics {}
                            .verticalScroll(rememberScrollState(), enabled = false).padding(horizontal = MinSpace.pageInset(window)),
                    ) { repeat(8) { ChannelRowSkeleton() } }
                    SubscriptionsChannels.Failed -> StatePage(page) {
                        StateMessage(
                            stringResource(R.string.ui_couldn_t_load_channels),
                            message = stringResource(R.string.ui_your_subscriptions_feed_is_still_available),
                            kind = StateKind.Error, actionLabel = stringResource(R.string.retry),
                            onAction = { onAction(SubscriptionsAction.RetryChannels) }, tag = "channels-error",
                        )
                    }
                    is SubscriptionsChannels.Ready -> LazyColumn(page.testTag("channels-list"), state = listState,
                        contentPadding = PaddingValues(top = MinSpace.sm, bottom = MinSpace.xxl)) {
                        if (channels.channels.isEmpty()) {
                            item {
                                StateMessage(stringResource(R.string.ui_no_channels_yet), icon = AppIcon.Subscriptions, tag = "channels-empty")
                            }
                        }
                        listItems(channels.channels, key = { it.channelId }) { channel ->
                            ChannelRow(
                                channel.name, channel.avatar, { onAction(SubscriptionsAction.SelectChannel(channel.channelId)) },
                                Modifier.testTag("channels-row-${channel.channelId}").padding(horizontal = MinSpace.pageInset(window)),
                                metadata = channel.metadata,
                                trailing = if (channel.hasNewContent) {
                                    { NewContentDot(Modifier.testTag("channels-new-${channel.channelId}")) }
                                } else null,
                            )
                        }
                        if (channels.nextKey != null) {
                            item(key = "channels-append") {
                                DirectoryAppendFooter(channels.append) { onAction(SubscriptionsAction.LoadMoreChannels) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryAppendFooter(append: AppendState, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
        when (append) {
            AppendState.Loading -> LoadingIndicator()
            AppendState.Failed -> MinButton(stringResource(R.string.retry), onLoadMore, kind = MinButtonKind.Tonal, icon = AppIcon.Refresh)
            AppendState.Idle -> MinButton(stringResource(R.string.load_more), onLoadMore, kind = MinButtonKind.Tonal)
        }
    }
}
