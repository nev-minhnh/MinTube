package com.hhst.youtubelite.tv.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * A focus-trapping panel on the right edge of the screen (its own dialog window, so the remote
 * cannot walk out of it). Back calls [onDismiss]. The caller puts focus inside, e.g. with
 * [TvOptionPanel]'s initial focus or a [FocusRequester].
 */
@Composable
fun TvSidePanel(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 520.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
            Column(
                modifier
                    .width(width)
                    .fillMaxHeight()
                    .background(TvColors.Panel)
                    .padding(horizontal = TvSpacing.xxl, vertical = TvSpacing.OverscanVertical),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.lg),
            ) {
                Text(title, style = TvType.Title, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                content()
            }
        }
    }
}

/** One choice in a [TvOptionPanel]. */
data class TvPanelOption(val label: String, val selected: Boolean, val detail: String? = null)

/**
 * A single-choice list in a [TvSidePanel]. Focus starts on the selected option; OK picks.
 * Option rows are tagged "[tag]-<index>".
 */
@Composable
fun TvOptionPanel(
    title: String,
    options: List<TvPanelOption>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    tag: String,
) {
    TvSidePanel(title, onDismiss, Modifier.testTag(tag)) {
        val initial = options.indexOfFirst { it.selected }.coerceAtLeast(0)
        val list = rememberLazyListState(initialFirstVisibleItemIndex = (initial - 2).coerceAtLeast(0))
        val first = remember { FocusRequester() }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxWidth().weight(1f).focusRestorer(first),
            contentPadding = PaddingValues(vertical = TvSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            itemsIndexed(options) { index, option ->
                TvListRow(
                    title = option.label,
                    onClick = { onSelect(index) },
                    modifier = (if (index == initial) Modifier.focusRequester(first) else Modifier).testTag("$tag-$index"),
                    summary = option.detail,
                    selected = option.selected,
                )
            }
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { first.requestFocus(FocusDirection.Enter) }
        }
    }
}

/**
 * A full-width focusable list row: title, optional summary, and on the right either a value,
 * an on/off switch ([checked]) or a selection mark ([selected]). Focus fills it with the accent.
 */
@Composable
fun TvListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    value: String? = null,
    checked: Boolean? = null,
    selected: Boolean = false,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    trailing: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(TvSizes.Corner)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = TvColors.Surface.copy(alpha = 0.6f),
            contentColor = TvColors.Ink,
            focusedContainerColor = TvColors.Surface,
            focusedContentColor = TvColors.Ink,
            pressedContainerColor = TvColors.Surface,
            pressedContentColor = TvColors.Ink,
            disabledContainerColor = TvColors.Surface.copy(alpha = 0.3f),
            disabledContentColor = TvColors.Secondary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(1.5.dp, TvColors.Accent.copy(alpha = 0.6f)), shape = shape) else Border.None,
            focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, TvColors.Accent), shape = shape),
        ),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = TvSpacing.xl, vertical = TvSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            if (icon != null) AppIcon(icon, Modifier.size(24.dp), color = TvColors.Secondary)
            Column(Modifier.weight(1f)) {
                Text(title, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (!summary.isNullOrBlank()) {
                    Text(summary, style = TvType.Caption, color = TvColors.Secondary, maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!value.isNullOrBlank()) {
                Text(value, style = TvType.Body, color = TvColors.Accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 260.dp))
            }
            if (checked != null) TvSwitch(checked)
            if (selected && checked == null) AppIcon(AppIcon.Check, Modifier.size(24.dp), color = TvColors.Accent)
            trailing?.invoke()
        }
    }
}

/** On/off indicator (not focusable itself; the row is). */
@Composable
fun TvSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier.width(52.dp).heightIn(min = 30.dp).background(
            if (checked) TvColors.Accent else TvColors.Ghost, RoundedCornerShape(15.dp)).padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(22.dp).background(if (checked) TvColors.OnAccent else TvColors.Secondary, RoundedCornerShape(11.dp)))
    }
}

/** Space between list sections. */
@Composable
fun TvSectionGap() = Spacer(Modifier.size(TvSpacing.lg))
