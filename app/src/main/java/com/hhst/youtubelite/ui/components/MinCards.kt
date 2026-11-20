package com.hhst.youtubelite.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/*
 * The Mint card family. Every card is stateless and takes plain values, so each feature maps its
 * own item type at the call site. Thumbnails are drawn by the existing [Artwork]; pass [videoId]
 * to draw through [VideoArtwork], which also registers the thumbnail bounds with WatchTransition
 * (the card-to-player handoff) exactly as the old cards did. [artworkModifier] reaches the
 * thumbnail node for callers that register bounds themselves.
 * Overflow buttons are 48dp; a card with [onMenu] needs a [menuDescription] for TalkBack.
 */

@Composable
private fun CardArtwork(artwork: String, videoId: String?, modifier: Modifier) {
    if (videoId != null) VideoArtwork(videoId, artwork, modifier) else Artwork(artwork, modifier)
}

/** Duration or LIVE label in the bottom-end corner of a thumbnail. */
@Composable
fun BoxScope.DurationBadge(label: String, live: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.align(Alignment.BottomEnd).padding(MinSpace.sm)
            .background(if (live) DesignTokens.Danger else Color.Black.copy(alpha = 0.75f), MinRadius.Badge)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        AppText(label, MinType.Micro, color = if (live) DesignTokens.OnDanger else Color.White, maxLines = 1)
    }
}

/** Watch progress along the bottom edge of a thumbnail. */
@Composable
fun BoxScope.ThumbnailProgress(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.Black.copy(alpha = 0.5f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).background(DesignTokens.Accent))
    }
}

/**
 * Feed card: 16:9 thumbnail, title (2 lines), one metadata line, optional channel avatar and
 * overflow. [fullBleed] drops the corner radius for edge-to-edge phone feeds.
 */
@Composable
fun VideoCard(
    title: String,
    metadata: String,
    artwork: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    durationLabel: String? = null,
    live: Boolean = false,
    progress: Float? = null,
    avatar: String? = null,
    avatarDescription: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null,
    menuDescription: String = "",
    videoId: String? = null,
    artworkModifier: Modifier = Modifier,
    fullBleed: Boolean = false,
) {
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(if (fullBleed) RectangleShape else MinRadius.Card)) {
            CardArtwork(artwork, videoId, artworkModifier.fillMaxSize())
            if (!durationLabel.isNullOrBlank()) DurationBadge(durationLabel, live)
            if (progress != null) ThumbnailProgress(progress)
        }
        Row(Modifier.fillMaxWidth().padding(start = if (fullBleed) MinSpace.md else 0.dp, top = MinSpace.sm), verticalAlignment = Alignment.Top) {
            if (avatar != null) {
                Box(
                    Modifier.size(MinSpace.Touch).clip(CircleShape)
                        .then(if (onAvatarClick != null) Modifier.clickable(role = Role.Button, onClick = onAvatarClick) else Modifier)
                        .then(if (avatarDescription != null) Modifier.semantics { contentDescription = avatarDescription } else Modifier),
                    contentAlignment = Alignment.Center,
                ) { Artwork(avatar, Modifier.size(36.dp).clip(CircleShape)) }
                Spacer(Modifier.width(MinSpace.xs))
            }
            Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
                AppText(title, MinType.Subtitle, maxLines = 2)
                if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = DesignTokens.Secondary, maxLines = 2)
            }
            if (onMenu != null) AppIconButton(AppIcon.More, menuDescription, onClick = onMenu)
            else Spacer(Modifier.width(if (fullBleed) MinSpace.md else 0.dp))
        }
    }
}

/** Compact list row: thumbnail at the start, title and metadata beside it. */
@Composable
fun VideoRow(
    title: String,
    metadata: String,
    artwork: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryMetadata: String? = null,
    durationLabel: String? = null,
    live: Boolean = false,
    progress: Float? = null,
    onMenu: (() -> Unit)? = null,
    menuDescription: String = "",
    videoId: String? = null,
    artworkModifier: Modifier = Modifier,
    thumbnailWidth: Dp = 160.dp,
) {
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.Top) {
        Box(Modifier.width(thumbnailWidth).aspectRatio(16f / 9f).clip(MinRadius.Field)) {
            CardArtwork(artwork, videoId, artworkModifier.fillMaxSize())
            if (!durationLabel.isNullOrBlank()) DurationBadge(durationLabel, live)
            if (progress != null) ThumbnailProgress(progress)
        }
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f).padding(top = MinSpace.xxs)) {
            AppText(title, MinType.Body, maxLines = 2)
            if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary, maxLines = 1)
            if (!secondaryMetadata.isNullOrBlank()) AppText(secondaryMetadata, MinType.Caption, color = DesignTokens.Secondary, maxLines = 1)
        }
        if (onMenu != null) AppIconButton(AppIcon.More, menuDescription, onClick = onMenu)
    }
}

/** Vertical 9:16 card with the title over a bottom scrim. Text stays white: it sits on artwork. */
@Composable
fun ShortCard(
    title: String,
    artwork: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    onMenu: (() -> Unit)? = null,
    menuDescription: String = "",
    videoId: String? = null,
    artworkModifier: Modifier = Modifier,
) {
    Box(modifier.aspectRatio(9f / 16f).clip(MinRadius.Card).clickable(role = Role.Button, onClick = onClick)) {
        CardArtwork(artwork, videoId, artworkModifier.fillMaxSize())
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.5f)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(MinSpace.md)) {
            AppText(title, MinType.Body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium), color = Color.White, maxLines = 2)
            if (!metadata.isNullOrBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = Color.White.copy(alpha = 0.85f), maxLines = 1)
        }
        if (onMenu != null) AppIconButton(AppIcon.More, menuDescription, Modifier.align(Alignment.TopEnd), color = Color.White, onClick = onMenu)
    }
}

/** Channel row: round avatar, name, metadata and a trailing slot (usually `SubscribeButton`). */
@Composable
fun ChannelRow(
    name: String,
    avatar: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    metadata: String? = null,
    avatarSize: Dp = 48.dp,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(avatar, Modifier.size(avatarSize).clip(CircleShape))
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            AppText(name, MinType.Subtitle, maxLines = 1)
            if (!metadata.isNullOrBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = DesignTokens.Secondary, maxLines = 2)
        }
        if (trailing != null) {
            Spacer(Modifier.width(MinSpace.sm))
            trailing()
        }
    }
}

/** Playlist card: stacked thumbnail with an item-count badge, title and metadata. */
@Composable
fun PlaylistCard(
    title: String,
    metadata: String,
    artwork: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    onMenu: (() -> Unit)? = null,
    menuDescription: String = "",
    artworkModifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Box(Modifier.fillMaxWidth().padding(top = MinSpace.sm)) {
            // Two sheets peeking out above the cover read as "a stack of videos".
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth(0.84f).height(MinSpace.sm)
                .background(DesignTokens.Surface, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
            Box(Modifier.align(Alignment.TopCenter).padding(top = MinSpace.xs).fillMaxWidth(0.92f).height(MinSpace.sm)
                .background(DesignTokens.Ghost, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)))
            Box(Modifier.padding(top = MinSpace.sm).fillMaxWidth().aspectRatio(16f / 9f).clip(MinRadius.Card)) {
                Artwork(artwork, artworkModifier.fillMaxSize())
                if (!countLabel.isNullOrBlank()) Row(
                    Modifier.align(Alignment.BottomEnd).padding(MinSpace.sm)
                        .background(Color.Black.copy(alpha = 0.75f), MinRadius.Badge).padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(AppIcon.Playlist, Modifier.size(14.dp), color = Color.White)
                    Spacer(Modifier.width(MinSpace.xs))
                    AppText(countLabel, MinType.Micro, color = Color.White, maxLines = 1)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = MinSpace.sm), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
                AppText(title, MinType.Subtitle, maxLines = 2)
                if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = DesignTokens.Secondary, maxLines = 1)
            }
            if (onMenu != null) AppIconButton(AppIcon.More, menuDescription, onClick = onMenu)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Skeletons matching each card
// ---------------------------------------------------------------------------------------------

@Composable
fun VideoCardSkeleton(modifier: Modifier = Modifier, fullBleed: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), if (fullBleed) RectangleShape else MinRadius.Card)
        Row(Modifier.padding(start = if (fullBleed) MinSpace.md else 0.dp, end = if (fullBleed) MinSpace.md else 0.dp, top = MinSpace.md)) {
            SkeletonBox(Modifier.size(36.dp), CircleShape)
            Spacer(Modifier.width(MinSpace.md))
            Column(Modifier.weight(1f)) {
                SkeletonLine(fraction = 0.9f, height = 16.dp)
                Spacer(Modifier.height(MinSpace.sm))
                SkeletonLine(fraction = 0.55f, height = 12.dp)
            }
        }
    }
}

@Composable
fun VideoRowSkeleton(modifier: Modifier = Modifier, thumbnailWidth: Dp = 160.dp) {
    Row(modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.width(thumbnailWidth).aspectRatio(16f / 9f), MinRadius.Field)
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
            SkeletonLine(fraction = 0.95f)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.7f)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.4f, height = 12.dp)
        }
    }
}

@Composable
fun ShortCardSkeleton(modifier: Modifier = Modifier) {
    SkeletonBox(modifier.aspectRatio(9f / 16f), MinRadius.Card)
}

@Composable
fun ChannelRowSkeleton(modifier: Modifier = Modifier, avatarSize: Dp = 48.dp) {
    Row(modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
        SkeletonBox(Modifier.size(avatarSize), CircleShape)
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f)) {
            SkeletonLine(fraction = 0.5f, height = 16.dp)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.3f, height = 12.dp)
        }
    }
}

@Composable
fun PlaylistCardSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f), MinRadius.Card)
        Spacer(Modifier.height(MinSpace.md))
        SkeletonLine(fraction = 0.8f, height = 16.dp)
        Spacer(Modifier.height(MinSpace.sm))
        SkeletonLine(fraction = 0.45f, height = 12.dp)
    }
}
