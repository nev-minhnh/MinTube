package com.hhst.youtubelite.home.presentation

import com.hhst.youtubelite.core.time.publishedAgeText
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.FeedItem
import com.hhst.youtubelite.home.domain.FeedMenuKind
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeNavigationRequest
import com.hhst.youtubelite.post.domain.PostDetailAction
import com.hhst.youtubelite.post.domain.PostIdentity
import com.hhst.youtubelite.post.presentation.PostCardInteractions
import com.hhst.youtubelite.ui.components.MinAvatar
import com.hhst.youtubelite.ui.components.PlaylistCard
import com.hhst.youtubelite.ui.components.ShortCard
import com.hhst.youtubelite.ui.components.VideoCard
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.ShortsLogo
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.watch.domain.WatchProvisionalStore
import com.hhst.youtubelite.watch.domain.toProvisional

/** Maps one feed item onto the Mint card family. Thumbnails register with the watch handoff through `videoId`. */
@Composable
internal fun FeedCard(item: FeedItem, window: WindowClass, live: Boolean = false, onAction: (HomeAction) -> Unit,
    postInteractions: PostCardInteractions? = null) {
    when (item) {
        is FeedItem.Video -> VideoCard(
            title = rememberDeArrowTitle(item.id, item.title),
            metadata = listOf(item.author, item.metadata, publishedAgeText(item.published)).filter(String::isNotBlank).joinToString(" · "),
            artwork = item.artwork,
            onClick = {
                WatchProvisionalStore.put(item.toProvisional())
                onAction(HomeAction.Navigate(HomeNavigationRequest.Watch(item.id)))
            },
            modifier = Modifier.testTag("feed-item-${item.id}"),
            durationLabel = item.duration, live = item.live, progress = item.progress,
            // The thumbnail is its own button and speaks "title, duration" (or LIVE), as the channel cards do.
            thumbnailDescription = stringResource(R.string.a11y_title_duration, item.title, item.duration),
            avatar = item.avatar.orEmpty(),
            avatarDescription = stringResource(R.string.a11y_go_to_channel, item.author),
            onAvatarClick = { onAction(HomeAction.Navigate(HomeNavigationRequest.Channel(item.channelId))) },
            avatarName = item.author,
            avatarTestTag = "channel-${item.channelId}",
            onMenu = { onAction(HomeAction.OpenMenu(item.id)) },
            menuDescription = stringResource(R.string.a11y_more_actions_for, item.title),
            videoId = item.id,
        )
        is FeedItem.Mix -> VideoCard(
            title = rememberDeArrowTitle(item.id, item.title),
            metadata = item.authors,
            artwork = item.artwork,
            onClick = {
                WatchProvisionalStore.put(item.toProvisional())
                onAction(HomeAction.Navigate(HomeNavigationRequest.Watch(item.id)))
            },
            modifier = Modifier.testTag("feed-item-${item.id}"),
            durationLabel = stringResource(R.string.ui_mix),
            onMenu = { onAction(HomeAction.OpenMenu(item.id, FeedMenuKind.Mix)) },
            menuDescription = stringResource(R.string.a11y_more_actions_for, item.title),
            videoId = item.id,
        )
        is FeedItem.Playlist -> {
            val playlist = item.summary
            PlaylistCard(
                title = playlist.title, metadata = playlist.owner, artwork = playlist.artwork,
                onClick = { onAction(HomeAction.Navigate(HomeNavigationRequest.Playlist(playlist.id))) },
                modifier = Modifier.testTag("feed-item-${item.id}"),
                countLabel = playlist.countLabel,
            )
        }
        is FeedItem.Shorts -> ShortsShelf(item, window, live, onAction)
        is FeedItem.Post -> PostCard(item, live, onAction, postInteractions)
    }
}

/** Width-based entry kept for callers outside Home (instrumented tests of other features). */
@Composable
internal fun FeedCard(item: FeedItem, compact: Boolean, windowWidth: Float, live: Boolean = false, onAction: (HomeAction) -> Unit,
    postInteractions: PostCardInteractions? = null) {
    val window = if (compact) WindowClass.Compact else WindowClass.of(windowWidth.toInt())
    FeedCard(item, window, live, onAction, postInteractions)
}

/** Lets a full-span row reach both window edges from inside the padded feed grid. */
private fun Modifier.bleed(inset: Dp) = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val edge = inset.roundToPx()
    val placeable = measurable.measure(Constraints(
        minWidth = constraints.maxWidth + edge * 2, maxWidth = constraints.maxWidth + edge * 2,
        minHeight = constraints.minHeight, maxHeight = constraints.maxHeight,
    ))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-edge, 0) }
}

/** Shorts as one horizontally scrolling shelf: header with the shelf menu, then 9:16 cards. */
@Composable
private fun ShortsShelf(item: FeedItem.Shorts, window: WindowClass, live: Boolean, onAction: (HomeAction) -> Unit) {
    val inset = MinSpace.pageInset(window)
    val cardWidth = if (window == WindowClass.Compact) 148.dp else 172.dp
    Column(Modifier.fillMaxWidth().testTag("shorts-shelf")) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
            ShortsLogo(Modifier.size(24.dp))
            AppText(stringResource(R.string.ui_shorts), MinType.Title,
                Modifier.padding(start = MinSpace.sm).weight(1f).semantics { heading() }, maxLines = 1)
            if (!live) AppIconButton(AppIcon.More, stringResource(R.string.a11y_shorts_options)) {
                onAction(HomeAction.OpenMenu(item.id, FeedMenuKind.Shorts))
            }
        }
        LazyRow(Modifier.bleed(inset).fillMaxWidth().testTag("shorts-shelf-row"),
            contentPadding = PaddingValues(horizontal = inset),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            items(item.videos) { video ->
                ShortCard(
                    title = rememberDeArrowTitle(video.id, video.title),
                    artwork = video.artwork,
                    onClick = { onAction(HomeAction.Navigate(HomeNavigationRequest.Shorts(video.id))) },
                    modifier = Modifier.width(cardWidth).testTag("short-${video.id}"),
                    metadata = if (video.isNew) stringResource(R.string.ui_new) else null,
                    onMenu = if (live) null else ({ onAction(HomeAction.OpenMenu(video.id, FeedMenuKind.Short)) }),
                    menuDescription = stringResource(R.string.a11y_more_actions_for, video.title),
                    videoId = video.id,
                )
            }
        }
    }
}

@Composable
private fun Avatar(asset: String, author: String, channelId: String, onAction: (HomeAction) -> Unit, size: Dp = 40.dp) {
    val label = stringResource(R.string.a11y_go_to_channel, author)
    Box(Modifier.size(MinSpace.Touch).clip(CircleShape).clickable(role = Role.Button) {
        onAction(HomeAction.Navigate(HomeNavigationRequest.Channel(channelId)))
    }.testTag("channel-$channelId").semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        MinAvatar(asset, author, size = size)
    }
}

/** Community post on a Panel card: author row, text, square image strip, like/dislike and comments. */
@Composable
private fun PostCard(item: FeedItem.Post, live: Boolean, onAction: (HomeAction) -> Unit, interactions: PostCardInteractions?) {
    val interaction = interactions?.posts?.get(item.postId)
    val rating = interaction?.post?.rating ?: VideoRating.None
    val pending = interaction?.pendingAction
    fun rate(liked: Boolean) {
        if (interactions != null) interactions.perform(PostIdentity(item.channelId, item.postId), if (liked) PostDetailAction.Like else PostDetailAction.Dislike)
        else onAction(HomeAction.Navigate(HomeNavigationRequest.PostFeedback(item.postId, liked, item.channelId)))
    }
    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().clip(MinRadius.Card).background(DesignTokens.Panel)
        .border(1.dp, DesignTokens.Border, MinRadius.Card).testTag("community-post")) {
        Row(Modifier.padding(start = MinSpace.sm, top = MinSpace.sm, end = MinSpace.xs).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Avatar(item.artwork, item.author, item.channelId, onAction)
            Column(Modifier.weight(1f).padding(start = MinSpace.xs)) {
                AppText(item.author, MinType.Body.copy(fontWeight = FontWeight.Medium), maxLines = 1)
                AppText(item.age, MinType.Caption, color = DesignTokens.Secondary, maxLines = 1)
            }
            if (!live) AppIconButton(AppIcon.More, stringResource(R.string.a11y_post_actions)) {
                onAction(HomeAction.OpenMenu(item.id, FeedMenuKind.Post))
            }
        }
        AppText(item.text, MinType.Body, Modifier.padding(horizontal = MinSpace.lg, vertical = MinSpace.sm).clickable(role = Role.Button) {
            onAction(HomeAction.Navigate(HomeNavigationRequest.OpenPost(item.channelId, item.postId)))
        }.testTag("post-body-${item.id}"), maxLines = 3)
        if (item.images.isNotEmpty()) BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A second image always peeks in, so the strip reads as scrollable.
            val imageWidth = (maxWidth - 48.dp).coerceAtMost(420.dp)
            LazyRow(Modifier.testTag("post-images-${item.id}"), contentPadding = PaddingValues(horizontal = MinSpace.lg),
                horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
                items(item.images.size) { index ->
                    Box(Modifier.size(imageWidth).clip(MinRadius.Field)
                        .clickable(role = Role.Button) {
                            if (interactions?.openImages != null) interactions.openImages.invoke(item.images, index)
                            else onAction(HomeAction.Navigate(HomeNavigationRequest.OpenPost(item.channelId, item.postId)))
                        }.testTag("post-image-${item.id}-$index")) {
                        Artwork(item.images[index], Modifier.fillMaxSize())
                        if (item.images.size > 1) Box(Modifier.align(Alignment.TopEnd).padding(MinSpace.sm)
                            .background(Color.Black.copy(alpha = 0.75f), MinRadius.Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                            AppText("${index + 1}/${item.images.size}", MinType.Micro, color = Color.White, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
            AppIconButton(AppIcon.Like, stringResource(R.string.a11y_like_post),
                modifier = Modifier.testTag("post-like-${item.postId}").semantics { selected = rating == VideoRating.Like },
                enabled = pending == null, pending = pending == PostDetailAction.Like,
                background = if (rating == VideoRating.Like) DesignTokens.AccentContainer else Color.Transparent,
                color = if (rating == VideoRating.Like) DesignTokens.AccentText else DesignTokens.Ink) { rate(true) }
            AppText(interaction?.post?.likes ?: item.likes, MinType.Label, Modifier.widthIn(min = 32.dp).padding(horizontal = MinSpace.xs))
            AppIconButton(AppIcon.Dislike, stringResource(R.string.a11y_dislike_post),
                modifier = Modifier.testTag("post-dislike-${item.postId}").semantics { selected = rating == VideoRating.Dislike },
                enabled = pending == null, pending = pending == PostDetailAction.Dislike,
                background = if (rating == VideoRating.Dislike) DesignTokens.AccentContainer else Color.Transparent,
                color = if (rating == VideoRating.Dislike) DesignTokens.AccentText else DesignTokens.Ink) { rate(false) }
            Spacer(Modifier.weight(1f))
            Row(Modifier.heightIn(min = MinSpace.Touch).clip(CircleShape)
                .clickable(role = Role.Button) { onAction(HomeAction.Navigate(HomeNavigationRequest.PostComments(item.postId, item.channelId))) }
                .padding(horizontal = MinSpace.md), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcon.Comment, Modifier.size(20.dp))
                AppText(item.comments, MinType.Label, Modifier.padding(start = 6.dp))
            }
        }
    }
}
