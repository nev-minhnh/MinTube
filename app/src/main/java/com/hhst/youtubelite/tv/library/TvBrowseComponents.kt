package com.hhst.youtubelite.tv.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocus
import com.hhst.youtubelite.tv.design.TvFocusMemory
import com.hhst.youtubelite.tv.design.TvFocusRow
import com.hhst.youtubelite.tv.design.TvFocusTarget
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.tv.shell.LocalTvShell
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Item key of the trailing "View all" card in a library row. */
const val VIEW_ALL_KEY = "view-all"

/** Grid geometry for a content width: columns of at least [minCard] and the card width that fills them. */
data class TvGridMetrics(val columns: Int, val cardWidth: Dp) {
    companion object {
        fun of(available: Dp, minCard: Dp = 180.dp, gap: Dp = TvSpacing.CardGap): TvGridMetrics {
            val columns = ((available + gap) / (minCard + gap)).toInt().coerceAtLeast(1)
            return TvGridMetrics(columns, (available - gap * (columns - 1)) / columns)
        }
    }
}

/** Focusable card that ends a row and opens the full page. Same frame and focus treatment as [com.hhst.youtubelite.tv.design.TvCard]. */
@Composable
fun TvViewAllCard(onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = TvSizes.CardWidth, aspect: Float = 16f / 9f,
                  onFocused: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(TvSizes.Corner)
    val label = stringResource(R.string.ui_view_all)
    Column(Modifier.width(width)) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().aspectRatio(aspect)
                .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                .semantics { contentDescription = label },
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvColors.Surface, contentColor = TvColors.Secondary,
                focusedContainerColor = TvColors.AccentContainer, focusedContentColor = TvColors.Accent,
                pressedContainerColor = TvColors.AccentContainer, pressedContentColor = TvColors.Accent,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.Scale),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = shape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(TvColors.Accent.copy(alpha = 0.55f), TvFocus.GlowElevation)),
        ) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                val color = LocalContentColor.current
                AppIcon(AppIcon.ArrowForward, Modifier.size(32.dp), color = color)
                Spacer(Modifier.height(TvSpacing.sm))
                Text(label, style = TvType.BodyStrong, color = color, maxLines = 1)
            }
        }
    }
}

/** Round channel avatar with its name below; focus scales it and rings it in the accent. */
@Composable
fun TvChannelAvatar(
    name: String,
    avatar: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hasNew: Boolean = false,
    onFocused: (() -> Unit)? = null,
) {
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(Modifier.width(AvatarSlot), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            modifier = modifier.size(AvatarSize)
                .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                .semantics { contentDescription = name },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = TvColors.Surface, focusedContainerColor = TvColors.Surface, pressedContainerColor = TvColors.Surface,
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = TvFocus.Scale),
            border = ClickableSurfaceDefaults.border(
                border = if (hasNew) Border(BorderStroke(2.dp, TvColors.Accent.copy(alpha = 0.6f)), shape = CircleShape) else Border.None,
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = CircleShape),
            ),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Glow(TvColors.Accent.copy(alpha = 0.55f), TvFocus.GlowElevation)),
            interactionSource = source,
        ) {
            YoutubeThumb(avatar, Modifier.fillMaxSize(), placeholderColor = TvColors.Surface)
        }
        Spacer(Modifier.height(TvSpacing.md))
        Text(name, style = TvType.Caption, color = if (focused) TvColors.Ink else TvColors.Secondary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** Placeholder avatars while the channel list loads. */
@Composable
fun TvAvatarSkeleton(modifier: Modifier = Modifier, count: Int = 6) {
    androidx.compose.foundation.layout.Row(modifier, horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap)) {
        repeat(count) {
            Column(Modifier.width(AvatarSlot), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(AvatarSize).background(TvColors.Surface, CircleShape))
                Spacer(Modifier.height(TvSpacing.md))
                Box(Modifier.width(72.dp).height(13.dp).background(TvColors.Ghost, RoundedCornerShape(4.dp)))
            }
        }
    }
}

/** Centres a state message in the content area, clear of the overscan edge. */
@Composable
fun TvCentered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical),
        contentAlignment = Alignment.Center) { content() }
}

/** Page title line used by the full-screen library pages and the destinations. */
@Composable
fun TvPageTitle(title: String, modifier: Modifier = Modifier, detail: String? = null) {
    Column(modifier.padding(bottom = TvSpacing.sm)) {
        Text(title, style = TvType.Display, color = TvColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!detail.isNullOrBlank()) Text(detail, style = TvType.Caption, color = TvColors.Secondary, maxLines = 2)
    }
}

/**
 * Focus must never be lost. When the screen is resumed and neither it nor a rail the user chose
 * holds focus — a refresh or a removal took the focused card away, or a full-screen page just
 * loaded ([initial]) — focus goes back to the remembered card ([TvFocusMemory.restoreTarget]),
 * else to [fallback]. Inside the shell the first focus belongs to the shell, so [initial] is false there.
 */
@Composable
fun TvFocusKeeper(
    rows: List<TvFocusRow>,
    memory: TvFocusMemory,
    hasFocus: () -> Boolean,
    initial: Boolean,
    focus: suspend (TvFocusTarget) -> Boolean,
    fallback: () -> Boolean = { false },
) {
    val shell = LocalTvShell.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val state by lifecycle.currentStateFlow.collectAsState()
    val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
    val focusAction by rememberUpdatedState(focus)
    val fallbackAction by rememberUpdatedState(fallback)
    val focused by rememberUpdatedState(hasFocus)
    // Also when Compose moves focus to the rail by itself (a shelf's Retry leaves while it reloads).
    val railFocused = shell?.railFocused == true
    LaunchedEffect(rows, resumed, railFocused) {
        if (!resumed || memory.lastRowKey == null && !initial) return@LaunchedEffect
        // Compose may hand focus to the rail when the focused card leaves; only a user's move there counts.
        repeat(RESTORE_ATTEMPTS) {
            withFrameNanos { }
            delay(RESTORE_SETTLE_MS)
            if (focused() || shell?.railFocusedByUser == true) return@LaunchedEffect
            val target = memory.restoreTarget(rows)
            if (if (target != null) focusAction(target) else fallbackAction()) return@LaunchedEffect
        }
    }
}

private val AvatarSize = 88.dp
private val AvatarSlot = 116.dp
private const val RESTORE_SETTLE_MS = 150L
private const val RESTORE_ATTEMPTS = 4
