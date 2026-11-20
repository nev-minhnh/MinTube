package com.hhst.youtubelite.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/**
 * The page's first item starts with the title block ([titleBlockPx] tall: title, metadata, action row);
 * once all of it has scrolled under the player the strip takes over.
 */
internal fun LazyListState.titleBlockScrolledAway(titleBlockPx: Int): Boolean =
    firstVisibleItemIndex > 0 || titleBlockPx > 0 && firstVisibleItemScrollOffset >= titleBlockPx

/**
 * Extra downward shift for a sticky header so it stacks under a strip of [stripHeightPx] instead of
 * sliding beneath it. [headerTopPx] is where the list placed the header (0 when pinned).
 */
internal fun stickyUnderStripOffset(headerTopPx: Int?, stripHeightPx: Float): Float =
    if (headerTopPx == null || stripHeightPx <= 0f) 0f else (stripHeightPx - headerTopPx).coerceIn(0f, stripHeightPx)

/**
 * Compact "now playing" strip pinned directly under the player once the title block has scrolled away.
 * [progress] is the 0..1 reveal (fade plus a short slide); at 0 the strip is not composed at all.
 */
@Composable
internal fun WatchNowPlayingStrip(
    title: String,
    channel: String,
    progress: () -> Float,
    inset: Dp,
    onBackToTop: () -> Unit,
    onHeightChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.watch_strip_now_playing_a11y, title, channel)
    val shadow = DesignTokens.Ink.copy(alpha = 0.10f)
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val shown = progress()
                alpha = shown
                translationY = -(1f - shown) * 8.dp.toPx()
            }
            .testTag("watch-now-playing-strip"),
    ) {
        // The reported height (row plus hairline) is where a sticky header below should stack.
        Column(Modifier.fillMaxWidth().onSizeChanged { onHeightChanged(it.height) }) {
            Row(
                Modifier
                    .fillMaxWidth()
                    // Same surface as the sticky chip row: page background with the player glow continued under it.
                    .watchGlowBackground()
                    // One focusable element for accessibility; the chevron stays a 48dp touch target.
                    .clearAndSetSemantics {
                        contentDescription = description
                        role = Role.Button
                        onClick { onBackToTop(); true }
                    }
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onBackToTop)
                    .heightIn(min = 52.dp)
                    .padding(start = inset, end = MinSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = MinSpace.xs)) {
                    AppText(title, MinType.Subtitle, Modifier.testTag("watch-now-playing-title"), maxLines = 1)
                    if (channel.isNotBlank()) AppText(channel, MinType.Caption, color = DesignTokens.Secondary, maxLines = 1)
                }
                AppIconButton(AppIcon.ChevronUp, stringResource(R.string.watch_strip_back_to_top),
                    Modifier.testTag("watch-now-playing-top"), onClick = onBackToTop)
            }
            // The Border token is translucent: on its own the 1dp line let the list scrolled beneath it
            // show through as a stray dash. Draw it on the strip's opaque surface instead.
            Box(Modifier.fillMaxWidth().height(1.dp).watchGlowBackground().background(DesignTokens.Border)
                .testTag("watch-now-playing-hairline"))
        }
        // Soft fade onto the content below so the edge reads in both themes.
        Spacer(Modifier.fillMaxWidth().height(8.dp).background(Brush.verticalGradient(listOf(shadow, Color.Transparent))))
    }
}
