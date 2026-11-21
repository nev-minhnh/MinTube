package com.hhst.youtubelite.tv.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon

/** Display model shared by every TV video card (Home rows, Up next, later Search and Channel grids). */
data class TvVideo(
    val id: String,
    val title: String,
    val channel: String = "",
    val metadata: String = "",
    val artwork: String = "",
    val duration: String = "",
    val live: Boolean = false,
    val progress: Float? = null,
    val short: Boolean = false,
) {
    val subtitle: String get() = listOf(channel, metadata).filter(String::isNotBlank).joinToString(" · ")
}

/**
 * Video card: a focusable thumbnail that scales to [TvFocus.Scale] with a cyan border and glow,
 * title and channel below. Unfocused cards dim to [TvFocus.UnfocusedAlpha].
 * [modifier] applies to the focusable surface, so test tags and focus requesters target it.
 */
@Composable
fun TvCard(
    video: TvVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = if (video.short) TvSizes.ShortCardWidth else TvSizes.CardWidth,
    onLongClick: (() -> Unit)? = null,
    onFocused: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val alpha by animateFloatAsState(if (focused) 1f else TvFocus.UnfocusedAlpha, label = "tvCardAlpha")
    val shape = RoundedCornerShape(TvSizes.Corner)
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(if (video.short) 9f / 16f else 16f / 9f)
                .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                .semantics { contentDescription = video.title },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvColors.Surface, contentColor = TvColors.Ink,
                focusedContainerColor = TvColors.Surface, focusedContentColor = TvColors.Ink,
                pressedContainerColor = TvColors.Surface, pressedContentColor = TvColors.Ink,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.Scale),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(
                focusedGlow = Glow(TvColors.Accent.copy(alpha = 0.55f), TvFocus.GlowElevation),
            ),
            interactionSource = source,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
                YoutubeThumb(video.artwork, Modifier.fillMaxSize(), placeholderColor = TvColors.Surface)
                val badge = when {
                    video.live -> "LIVE"
                    else -> video.duration
                }
                if (badge.isNotBlank()) {
                    Text(
                        badge,
                        style = TvType.Label,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (video.live) TvColors.Live else TvColors.Scrim)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                video.progress?.takeIf { it > 0f }?.let { progress ->
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(TvColors.Ghost)) {
                        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(TvColors.Accent))
                    }
                }
            }
        }
        Spacer(Modifier.height(TvSpacing.md))
        Column(Modifier.graphicsLayer { this.alpha = alpha }) {
            Text(video.title, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (video.subtitle.isNotBlank()) {
                Text(video.subtitle, style = TvType.Caption, color = TvColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Row title above a horizontal list. */
@Composable
fun TvRowHeader(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Row(modifier.padding(bottom = TvSpacing.md), verticalAlignment = Alignment.Bottom) {
        Text(title, style = TvType.Title, color = TvColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrBlank()) {
            Spacer(Modifier.width(TvSpacing.md))
            Text(detail, style = TvType.Caption, color = TvColors.Secondary, maxLines = 1)
        }
    }
}

/** Pill button. Focus fills it with the accent; [primary] starts filled with the panel accent container. */
@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = TvSizes.ButtonHeight),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) TvColors.AccentContainer else TvColors.Surface,
            contentColor = TvColors.Ink,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
            pressedContainerColor = TvColors.Accent,
            pressedContentColor = TvColors.OnAccent,
            disabledContainerColor = TvColors.Surface.copy(alpha = 0.5f),
            disabledContentColor = TvColors.Secondary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(TvColors.Accent.copy(alpha = 0.45f), 8.dp)),
    ) {
        Row(
            Modifier.padding(horizontal = TvSpacing.xl, vertical = TvSpacing.sm).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            val content = androidx.tv.material3.LocalContentColor.current
            if (icon != null) AppIcon(icon, Modifier.size(22.dp), color = content)
            Text(text, style = TvType.BodyStrong, color = content, maxLines = 1)
        }
    }
}

/** Filter or option chip. [selected] marks the current value; focus fills it with the accent. */
@Composable
fun TvChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = TvSizes.ChipHeight),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) TvColors.AccentContainer else TvColors.Surface.copy(alpha = 0.85f),
            contentColor = if (selected) TvColors.Accent else TvColors.Ink,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
            pressedContainerColor = TvColors.Accent,
            pressedContentColor = TvColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, TvColors.Accent), shape = CircleShape) else Border.None,
        ),
    ) {
        val content = androidx.tv.material3.LocalContentColor.current
        Row(Modifier.padding(horizontal = TvSpacing.lg, vertical = TvSpacing.sm).align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = TvType.Label, color = content, maxLines = 1)
            if (!detail.isNullOrBlank()) {
                Spacer(Modifier.width(TvSpacing.sm))
                Text(detail, style = TvType.Caption, color = content.copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

/** Placeholder row while a row loads. Not focusable and hidden from accessibility. */
@Composable
fun TvSkeletonRow(
    modifier: Modifier = Modifier,
    cardWidth: Dp = TvSizes.CardWidth,
    aspectRatio: Float = 16f / 9f,
    count: Int = 5,
    showHeader: Boolean = true,
) {
    Column(modifier.clearAndSetSemantics {}) {
        if (showHeader) {
            Box(Modifier.padding(bottom = TvSpacing.md).width(200.dp).height(26.dp)
                .clip(RoundedCornerShape(6.dp)).background(TvColors.Ghost))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap)) {
            repeat(count) {
                Column(Modifier.width(cardWidth)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(aspectRatio).clip(RoundedCornerShape(TvSizes.Corner))
                        .background(TvColors.Surface))
                    Spacer(Modifier.height(TvSpacing.md))
                    Box(Modifier.fillMaxWidth(0.9f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(TvColors.Ghost))
                    Spacer(Modifier.height(TvSpacing.sm))
                    Box(Modifier.fillMaxWidth(0.55f).height(13.dp).clip(RoundedCornerShape(4.dp)).background(TvColors.Ghost))
                }
            }
        }
    }
}

/**
 * Empty, error and offline states. The action is focusable (a remote has no other way out);
 * [requestFocus] moves focus to it when the message appears.
 */
@Composable
fun TvStateMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    icon: AppIcon? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionModifier: Modifier = Modifier,
    requestFocus: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    Column(modifier.widthIn(max = 640.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
        if (icon != null) AppIcon(icon, Modifier.size(48.dp), color = TvColors.Secondary)
        Text(title, style = TvType.Title, color = TvColors.Ink, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Text(message, style = TvType.Body, color = TvColors.Secondary, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(TvSpacing.sm))
            TvButton(actionLabel, onAction, actionModifier.focusRequester(requester), primary = true)
            if (requestFocus) LaunchedEffect(Unit) { runCatching { requester.requestFocus(FocusDirection.Enter) } }
        }
    }
}
