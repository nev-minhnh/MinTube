package com.hhst.youtubelite.tv.grid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocus
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * A channel in a card grid: same footprint and focus treatment as [com.hhst.youtubelite.tv.design.TvCard]
 * (16:9 tile, scale, cyan border, glow), with the round avatar centred on the panel.
 */
@Composable
fun TvChannelTile(
    name: String,
    detail: String,
    avatar: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = TvSizes.CardWidth,
    onFocused: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val alpha by animateFloatAsState(if (focused) 1f else TvFocus.UnfocusedAlpha, label = "tvChannelAlpha")
    val shape = RoundedCornerShape(TvSizes.Corner)
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                .semantics { contentDescription = name },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvColors.Panel, contentColor = TvColors.Ink,
                focusedContainerColor = TvColors.Surface, focusedContentColor = TvColors.Ink,
                pressedContainerColor = TvColors.Surface, pressedContentColor = TvColors.Ink,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.Scale),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(TvColors.Accent.copy(alpha = 0.55f), TvFocus.GlowElevation)),
            interactionSource = source,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }, contentAlignment = Alignment.Center) {
                YoutubeThumb(avatar, Modifier.fillMaxHeight(0.62f).aspectRatio(1f).clip(CircleShape), placeholderColor = TvColors.Ghost)
            }
        }
        Spacer(Modifier.height(TvSpacing.md))
        Column(Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(name, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center)
            if (detail.isNotBlank()) {
                Text(detail, style = TvType.Caption, color = TvColors.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

/** Round icon-only button (remove, clear). Focus fills it with the accent. */
@Composable
fun TvIconButton(
    icon: AppIcon,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(TvSizes.ButtonHeight).semantics { this.contentDescription = contentDescription },
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.Surface.copy(alpha = 0.6f), contentColor = TvColors.Secondary,
            focusedContainerColor = TvColors.Accent, focusedContentColor = TvColors.OnAccent,
            pressedContainerColor = TvColors.Accent, pressedContentColor = TvColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AppIcon(icon, Modifier.size(20.dp), color = LocalContentColor.current)
        }
    }
}

/** A full-width list row: leading icon and one line of text; focus fills it with the accent. */
@Composable
fun TvListRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth().heightIn(min = TvSizes.ButtonHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(TvSizes.SmallCorner)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = TvColors.Ink,
            focusedContainerColor = TvColors.Accent, focusedContentColor = TvColors.OnAccent,
            pressedContainerColor = TvColors.Accent, pressedContentColor = TvColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
    ) {
        val color = LocalContentColor.current
        Row(Modifier.fillMaxWidth().padding(horizontal = TvSpacing.lg, vertical = TvSpacing.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
            if (icon != null) AppIcon(icon, Modifier.size(22.dp), color = color)
            Text(text, style = TvType.Body, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
