package com.hhst.youtubelite.channel.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelLivePage
import com.hhst.youtubelite.channel.domain.ChannelLiveVideo
import com.hhst.youtubelite.channel.domain.ChannelOverlay
import com.hhst.youtubelite.channel.domain.ChannelPage
import com.hhst.youtubelite.channel.domain.ChannelSection
import com.hhst.youtubelite.channel.domain.ChannelShort
import com.hhst.youtubelite.channel.domain.ChannelShortMenuAction
import com.hhst.youtubelite.channel.domain.ChannelSortOption
import com.hhst.youtubelite.channel.domain.ChannelTab
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.post.domain.PostDetailAction
import com.hhst.youtubelite.post.domain.PostMenuAction
import com.hhst.youtubelite.post.domain.PostSummary
import com.hhst.youtubelite.post.presentation.PostAuthorRow
import com.hhst.youtubelite.post.presentation.PostCardInteractions
import com.hhst.youtubelite.post.presentation.PostColumn
import com.hhst.youtubelite.post.presentation.PostImageCarousel
import com.hhst.youtubelite.post.presentation.PostPollChoices
import com.hhst.youtubelite.post.presentation.PostRatingRow
import com.hhst.youtubelite.ui.components.FilterChip
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.ShortCard
import com.hhst.youtubelite.ui.components.VideoCard
import com.hhst.youtubelite.ui.components.VideoRow
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.ui.design.miniDockListPadding

/*
 * Shorts, Live and Posts tabs. Each is its own LazyColumn that starts with the channel header and
 * the pinned tab row, like the tabs in ChannelVideoTabs.kt. Sorting stays the existing client-side
 * order of the loaded items.
 */

@Composable
fun ChannelShortsTab(
    page: ChannelPage,
    section: ChannelSection<List<ChannelShort>>,
    sort: ChannelSortOption,
    width: Float,
    state: LazyListState,
    onAction: (ChannelAction) -> Unit,
    production: Boolean = false,
    canPage: Boolean = false,
    append: AppendState = AppendState.Idle,
    subscribePending: Boolean = false,
) {
    val viewport by rememberUpdatedState(width)
    val shorts = remember(section, sort) {
        val loaded = (section as? ChannelSection.Ready)?.value.orEmpty()
        when (sort) {
            ChannelSortOption.Latest -> loaded.sortedBy { it.order }
            ChannelSortOption.Popular -> loaded.sortedByDescending { it.popularity }
            ChannelSortOption.Oldest -> loaded.sortedByDescending { it.order }
        }
    }
    ObserveChannelPagination(state, canPage, append) { onAction(ChannelAction.LoadShortsNext) }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("channel-shorts-list"),
        contentPadding = PaddingValues(bottom = dock.bottom),
    ) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Shorts, onAction) }
        item { ChannelSortChips(ChannelTab.Shorts, sort, viewport) { onAction(ChannelAction.SortShorts(it)) } }
        if (section is ChannelSection.Ready) {
            channelGrid(shorts, ChannelMetrics.shortsColumns(width), width, key = { "shorts-${it.id}" }, contentType = "short",
                rowGap = ChannelMetrics.GridGap) { short ->
                ChannelShortItem(short, onAction, production)
            }
        } else item { ChannelTabStatus(ChannelTab.Shorts, section, viewport, onAction) }
        channelAppend("channel-shorts-append", canPage, append, R.string.ui_couldn_t_load_more_shorts_tap_to_retry) {
            onAction(ChannelAction.LoadShortsNext)
        }
    }
}

/** The shared Short card; the overflow is drawn here because its tag is part of the channel contract. */
@Composable
private fun ChannelShortItem(short: ChannelShort, onAction: (ChannelAction) -> Unit, production: Boolean) {
    // The anonymous page has no short menu; production hides the dead entry.
    ShortCard(
        short.title, short.artwork, { onAction(ChannelAction.OpenShort(short.id)) },
        Modifier.fillMaxWidth().testTag("channel-short-${short.id}"), metadata = short.views,
        onMenu = if (production) null else ({ onAction(ChannelAction.OpenShortMenu(short.id)) }),
        menuDescription = stringResource(R.string.a11y_more_actions_for, short.title),
        menuTag = "channel-short-menu-${short.id}",
    )
}

@Composable
fun ChannelLiveTab(
    page: ChannelPage,
    section: ChannelSection<ChannelLivePage>,
    sort: ChannelSortOption,
    width: Float,
    state: LazyListState,
    onAction: (ChannelAction) -> Unit,
    canPage: Boolean = false,
    append: AppendState = AppendState.Idle,
    subscribePending: Boolean = false,
) {
    val viewport by rememberUpdatedState(width)
    val streams = remember(section, sort) {
        val live = (section as? ChannelSection.Ready)?.value
        val past = when (sort) {
            ChannelSortOption.Latest -> live?.past.orEmpty().sortedBy { it.order }
            ChannelSortOption.Popular -> live?.past.orEmpty().sortedByDescending { it.popularity }
            ChannelSortOption.Oldest -> live?.past.orEmpty().sortedByDescending { it.order }
        }
        live?.current.orEmpty() + past
    }
    val columns = ChannelMetrics.videoColumns(width)
    ObserveChannelPagination(state, canPage, append) { onAction(ChannelAction.LoadLiveNext) }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("channel-live-list"),
        contentPadding = PaddingValues(bottom = dock.bottom),
    ) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Live, onAction) }
        item { ChannelSortChips(ChannelTab.Live, sort, viewport) { onAction(ChannelAction.SortLive(it)) } }
        if (section is ChannelSection.Ready) {
            channelGrid(streams, columns, width, key = { "live-${it.id}" }, contentType = "live") { video ->
                ChannelLiveItem(video, viewport, card = columns > 1, onAction)
            }
        } else item { ChannelTabStatus(ChannelTab.Live, section, viewport, onAction) }
        channelAppend("channel-live-append", canPage, append, R.string.ui_couldn_t_load_more_streams_tap_to_retry) {
            onAction(ChannelAction.LoadLiveNext)
        }
    }
}

/** A current or past stream on the card family; a stream that is on air wears the Danger LIVE badge. */
@Composable
private fun ChannelLiveItem(video: ChannelLiveVideo, width: Float, card: Boolean, onAction: (ChannelAction) -> Unit) {
    val metadata = if (video.liveNow) video.views else listOf(video.views, video.time).filter(String::isNotBlank).joinToString(" · ")
    val badge = if (video.liveNow) stringResource(R.string.player_live) else video.duration
    val open = { onAction(ChannelAction.OpenLive(video.id)) }
    val tag = Modifier.testTag("channel-live-${video.id}")
    if (card) VideoCard(video.title, metadata, video.artwork, open, tag, durationLabel = badge, live = video.liveNow)
    else VideoRow(video.title, metadata, video.artwork, open, tag, durationLabel = badge, live = video.liveNow,
        thumbnailWidth = ChannelMetrics.rowThumbnail(width, LocalDensity.current.fontScale))
}

@Composable
fun ChannelPostsTab(
    page: ChannelPage,
    section: ChannelSection<List<PostSummary>>,
    width: Float,
    state: LazyListState,
    expandedPosts: Set<String>,
    onAction: (ChannelAction) -> Unit,
    production: Boolean = false,
    canPage: Boolean = false,
    append: AppendState = AppendState.Idle,
    subscribePending: Boolean = false,
    postInteractions: PostCardInteractions? = null,
) {
    val viewport by rememberUpdatedState(width)
    ObserveChannelPagination(state, canPage, append) { onAction(ChannelAction.LoadPostsNext) }
    val dock = miniDockListPadding(MinSpace.xxl)
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("channel-posts-list"),
        contentPadding = PaddingValues(bottom = dock.bottom),
    ) {
        item { ChannelHeader(page, viewport, onAction, subscribePending) }
        stickyHeader("channel-tabbar") { ChannelTabBar(ChannelTab.Posts, onAction) }
        when (section) {
            is ChannelSection.Ready -> items(section.value.size, key = { section.value[it].identity.postId }, contentType = { "post" }) { index ->
                val post = section.value[index]
                ChannelPostCard(
                    post = post,
                    expanded = post.identity.postId in expandedPosts,
                    width = viewport,
                    onAction = onAction,
                    production = production,
                    interactions = postInteractions,
                )
            }
            else -> item { ChannelTabStatus(ChannelTab.Posts, section, viewport, onAction) }
        }
        channelAppend("channel-posts-append", canPage, append, R.string.ui_couldn_t_load_more_posts_tap_to_retry) {
            onAction(ChannelAction.LoadPostsNext)
        }
    }
}

/**
 * A post in the channel feed, with the same author row and rating row as the post page. Rating and
 * votes go through the shared post interactions when the host provides them, as before.
 */
@Composable
private fun ChannelPostCard(
    post: PostSummary,
    expanded: Boolean,
    width: Float,
    onAction: (ChannelAction) -> Unit,
    production: Boolean = false,
    interactions: PostCardInteractions? = null,
) {
    val interaction = interactions?.posts?.get(post.identity.postId)
    val rating = interaction?.post?.rating ?: VideoRating.None
    val pending = interaction?.pendingAction
    fun rate(liked: Boolean) {
        if (interactions != null) interactions.perform(post.identity, if (liked) PostDetailAction.Like else PostDetailAction.Dislike)
        else onAction(ChannelAction.PostFeedback(post.identity, liked))
    }
    val open = { onAction(ChannelAction.OpenPost(post.identity)) }
    val id = post.identity.postId
    val inset = ChannelMetrics.inset(width)
    PostColumn {
        Column(Modifier.fillMaxWidth().testTag("channel-post-$id")) {
            PostAuthorRow(
                post.author.name, post.age, post.author.avatar, inset,
                nameTag = "channel-post-author-$id", avatarTag = "channel-post-avatar-$id",
                onTextClick = open,
                trailing = {
                    if (!production) AppIconButton(
                        AppIcon.More, stringResource(R.string.a11y_post_actions),
                        Modifier.testTag("channel-post-menu-$id"),
                    ) { onAction(ChannelAction.OpenPostMenu(post.identity)) }
                    else Spacer(Modifier.padding(end = inset))
                },
            )
            var overflow by remember(post.identity) { mutableStateOf(false) }
            Column(
                Modifier.fillMaxWidth().animateContentSize(tween(180)).padding(horizontal = inset).padding(top = MinSpace.sm)
                    .testTag("channel-post-body-$id"),
            ) {
                BasicText(
                    post.text,
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { open() },
                    style = MinType.Body.copy(color = DesignTokens.Ink),
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { overflow = it.hasVisualOverflow },
                )
                if (!expanded && overflow) MinButton(
                    stringResource(R.string.ui_read_more), { onAction(ChannelAction.TogglePostExpanded(post.identity)) },
                    kind = MinButtonKind.Text, testTag = "channel-post-read-more-$id",
                )
                if (expanded) MinButton(
                    stringResource(R.string.ui_show_less), { onAction(ChannelAction.TogglePostExpanded(post.identity)) },
                    kind = MinButtonKind.Text, testTag = "channel-post-show-less-$id",
                )
            }
            (interaction?.post?.poll ?: post.poll)?.let { poll ->
                PostPollChoices(poll, pending != null, Modifier.padding(horizontal = inset).padding(top = MinSpace.md),
                    pendingOption = (pending as? PostDetailAction.Vote)?.option) {
                    if (interactions != null) interactions.perform(post.identity, PostDetailAction.Vote(it, !poll.options[it].selected))
                    else onAction(ChannelAction.OpenPost(post.identity))
                }
            }
            if (post.images.isNotEmpty()) {
                var imageIndex by rememberSaveable(post.identity.postId) { mutableIntStateOf(0) }
                PostImageCarousel(
                    images = post.images,
                    selectedIndex = imageIndex,
                    onSelected = { imageIndex = it },
                    onOpen = { index ->
                        if (interactions?.openImages != null) interactions.openImages.invoke(post.images, index)
                        else open()
                    },
                    modifier = Modifier.padding(top = MinSpace.md).testTag("channel-post-images-$id"),
                    contentInset = inset,
                )
            }
            PostRatingRow(
                likes = interaction?.post?.likes ?: post.likes,
                comments = post.comments,
                rating = rating,
                enabled = pending == null,
                likePending = pending == PostDetailAction.Like,
                dislikePending = pending == PostDetailAction.Dislike,
                onLike = { rate(true) },
                onDislike = { rate(false) },
                onComments = { onAction(ChannelAction.PostComments(post.identity)) },
                likeTag = "channel-post-like-$id",
                dislikeTag = "channel-post-dislike-$id",
                commentsTag = "channel-post-comments-$id",
                modifier = Modifier.padding(start = inset - MinSpace.md, end = inset - MinSpace.md, top = MinSpace.xs, bottom = MinSpace.sm),
            )
            Box(Modifier.fillMaxWidth().padding(horizontal = inset).height(1.dp).background(DesignTokens.Border))
        }
    }
}

/** The existing sort options as filter chips; a non-default order says it only covers loaded items. */
@Composable
private fun ChannelSortChips(
    tab: ChannelTab,
    selected: ChannelSortOption,
    width: Float,
    onSelect: (ChannelSortOption) -> Unit,
) {
    val key = tab.name.lowercase()
    Column(Modifier.fillMaxWidth().padding(bottom = MinSpace.md)) {
        ChannelChipRow("channel-$key-sort", width) {
            ChannelSortOption.entries.forEach { option ->
                FilterChip(stringResource(option.labelResource), option == selected, { onSelect(option) },
                    Modifier.testTag("channel-$key-sort-${option.name.lowercase()}"))
            }
        }
        // No server sort lane exists for shorts/live: say so instead of implying a global order.
        if (selected != ChannelSortOption.Latest) {
            AppText(
                stringResource(R.string.ui_sorted_within_loaded_items_only), MinType.Caption,
                Modifier.padding(horizontal = ChannelMetrics.inset(width)).testTag("channel-$key-sort-scope"),
                color = DesignTokens.Secondary,
            )
        }
    }
}

@Composable
fun ChannelCommunityMenuSheets(
    overlay: ChannelOverlay,
    onAction: (ChannelAction) -> Unit,
) {
    when (overlay) {
        is ChannelOverlay.ShortMenu -> MenuBottomSheet(
            items = ChannelShortMenuAction.entries.map {
                MenuSheetItem(
                    icon = if (it == ChannelShortMenuAction.NotInterested) AppIcon.Block else AppIcon.Feedback,
                    label = stringResource(it.labelResource),
                    tag = "channel-short-menu-${it.name.lowercase()}",
                )
            },
            onDismiss = { onAction(ChannelAction.CloseOverlay) },
            onSelect = { index -> onAction(ChannelAction.SelectShortMenu(ChannelShortMenuAction.entries[index])) },
            tag = "channel-short-menu",
            paneTitle = stringResource(R.string.ui_short_actions),
        )
        is ChannelOverlay.PostMenu -> MenuBottomSheet(
            items = PostMenuAction.entries.map {
                MenuSheetItem(icon = AppIcon.Report, label = stringResource(it.labelResource), tag = "channel-post-menu-${it.name.lowercase()}")
            },
            onDismiss = { onAction(ChannelAction.CloseOverlay) },
            onSelect = { index -> onAction(ChannelAction.SelectPostMenu(PostMenuAction.entries[index])) },
            tag = "channel-post-menu-sheet",
            paneTitle = stringResource(R.string.a11y_post_actions),
        )
        else -> Unit
    }
}
