package com.hhst.youtubelite.tv.shell

import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Top-level TV destinations, in rail order. */
enum class TvDestination(@param:StringRes val label: Int, val icon: AppIcon) {
    Home(R.string.tv_nav_home, AppIcon.HomeFilled),
    Search(R.string.tv_nav_search, AppIcon.Search),
    Subscriptions(R.string.tv_nav_subscriptions, AppIcon.Subscriptions),
    Library(R.string.tv_nav_library, AppIcon.Library),
    Settings(R.string.tv_nav_settings, AppIcon.Settings),
}

/** What Back does in the shell. */
enum class TvBackStep { FocusRail, ShowHome, Exit }

object TvShellNavigation {
    /** Content → rail; rail on another destination → Home; rail on Home → leave the app. */
    fun back(contentFocused: Boolean, selected: TvDestination): TvBackStep = when {
        contentFocused -> TvBackStep.FocusRail
        selected != TvDestination.Home -> TvBackStep.ShowHome
        else -> TvBackStep.Exit
    }
}

/**
 * Shell state shared with destination screens through [LocalTvShell]. A screen calls
 * [focusContent] when it wants focus back in the content (e.g. after it finished loading).
 */
@Stable
class TvShellState(initial: TvDestination) {
    var selected by mutableStateOf(initial)
        private set
    var railFocused by mutableStateOf(false)
        internal set
    /**
     * The rail holds focus because the user went there (D-pad or Back). False when Compose moved
     * focus there by itself after the focused card left the content (a refresh): the screen should
     * then put focus back on its remembered card.
     */
    var railFocusedByUser by mutableStateOf(false)
        internal set
    /** Uptime of the last Left press or Back-to-rail: the only ways a user enters the rail from content. */
    internal var railIntentAt = 0L
    var contentFocused by mutableStateOf(false)
        internal set
    /** Set while the shell keeps trying to put focus in the content (first launch, after a selection). */
    internal var pendingContentFocus by mutableStateOf(true)

    internal val contentRequester = FocusRequester()
    internal val railRequesters: Map<TvDestination, FocusRequester> = TvDestination.entries.associateWith { FocusRequester() }

    fun select(destination: TvDestination, focusContent: Boolean = true) {
        selected = destination
        if (focusContent) pendingContentFocus = true
    }

    /** Enters the content group (its focus restorer picks the card). False when nothing is focusable yet. */
    fun focusContent(): Boolean = runCatching { contentRequester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)

    fun focusRail(): Boolean {
        railIntentAt = SystemClock.uptimeMillis()
        return runCatching { railRequesters.getValue(selected).requestFocus(FocusDirection.Enter) }.getOrDefault(false)
    }

    internal fun onRailFocus(hasFocus: Boolean) {
        if (hasFocus && !railFocused) railFocusedByUser = SystemClock.uptimeMillis() - railIntentAt < USER_FOCUS_WINDOW_MS
        if (!hasFocus) railFocusedByUser = false
        railFocused = hasFocus
    }

    companion object {
        val Saver: Saver<TvShellState, String> = Saver(save = { it.selected.name },
            restore = { name -> TvShellState(TvDestination.entries.firstOrNull { it.name == name } ?: TvDestination.Home) })
    }
}

@Composable
fun rememberTvShellState(initial: TvDestination = TvDestination.Home): TvShellState =
    rememberSaveable(saver = TvShellState.Saver) { TvShellState(initial) }

val LocalTvShell = staticCompositionLocalOf<TvShellState?> { null }

/**
 * Left navigation rail plus content. The rail expands while it holds focus and overlays the
 * content, so rows never reflow. Initial focus goes to the content; Back follows [TvShellNavigation].
 * [content] renders the selected destination; it should expose focusable items once loaded.
 */
@Composable
fun TvShell(
    state: TvShellState,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (TvDestination) -> Unit,
) {
    CompositionLocalProvider(LocalTvShell provides state) {
        BackHandler {
            when (TvShellNavigation.back(state.contentFocused, state.selected)) {
                TvBackStep.FocusRail -> state.focusRail()
                TvBackStep.ShowHome -> state.select(TvDestination.Home)
                TvBackStep.Exit -> onExit()
            }
        }
        // Content may still be loading: keep offering it focus until it takes it or the user goes to the rail.
        LaunchedEffect(state.pendingContentFocus, state.selected) {
            if (!state.pendingContentFocus) return@LaunchedEffect
            // A selection made in the rail moves focus out of it; on launch, a user who
            // already went to the rail keeps it.
            val startedInRail = state.railFocused
            for (attempt in 0 until CONTENT_FOCUS_ATTEMPTS) {
                if (!startedInRail && state.railFocused) break
                if (state.contentFocused || state.focusContent()) break
                delay(CONTENT_FOCUS_RETRY_MS)
            }
            state.pendingContentFocus = false
        }
        Box(modifier.fillMaxSize().background(TvColors.Background).onPreviewKeyEvent { event ->
            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) state.railIntentAt = SystemClock.uptimeMillis()
            false
        }) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = TvSizes.RailCollapsed)
                    .focusRequester(state.contentRequester)
                    .focusRestorer()
                    .onFocusChanged { state.contentFocused = it.hasFocus }
                    .focusGroup()
                    .testTag(TAG_CONTENT),
            ) {
                content(state.selected)
            }
            TvRail(state)
        }
    }
}

@Composable
private fun TvRail(state: TvShellState) {
    val expanded = state.railFocused
    val width by animateDpAsState(if (expanded) TvSizes.RailExpanded else TvSizes.RailCollapsed, label = "tvRailWidth")
    val rail = stringResource(R.string.tv_nav_label)
    Box(
        Modifier
            .fillMaxHeight()
            .width(width)
            .background(
                if (expanded) Brush.horizontalGradient(listOf(TvColors.Panel, TvColors.Panel.copy(alpha = 0.96f), Color.Transparent))
                else Brush.horizontalGradient(listOf(TvColors.Background, TvColors.Background)),
            ),
    ) {
        Column(
            Modifier
                .fillMaxHeight()
                .padding(start = TvSpacing.OverscanHorizontal - 12.dp, end = TvSpacing.lg, top = TvSpacing.OverscanVertical,
                    bottom = TvSpacing.OverscanVertical)
                .focusRestorer(state.railRequesters.getValue(state.selected))
                .onFocusChanged { state.onRailFocus(it.hasFocus) }
                .focusGroup()
                .selectableGroup()
                .semantics { contentDescription = rail }
                .testTag(TAG_RAIL),
            verticalArrangement = Arrangement.spacedBy(TvSpacing.sm, Alignment.CenterVertically),
        ) {
            TvDestination.entries.forEach { destination ->
                TvRailItem(
                    destination = destination,
                    selected = destination == state.selected,
                    expanded = expanded,
                    onClick = { state.select(destination) },
                    modifier = Modifier.focusRequester(state.railRequesters.getValue(destination)),
                )
            }
        }
    }
}

@Composable
private fun TvRailItem(
    destination: TvDestination,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(destination.label)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(TvSizes.RailItemHeight)
            .semantics {
                this.selected = selected
                contentDescription = label
            }
            .testTag(railItemTag(destination)),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(TvSizes.RailItemHeight / 2)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.Transparent,
            contentColor = if (selected) TvColors.Accent else TvColors.Secondary,
            focusedContainerColor = TvColors.Accent,
            focusedContentColor = TvColors.OnAccent,
            pressedContainerColor = TvColors.Accent,
            pressedContentColor = TvColors.OnAccent,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
    ) {
        val color = LocalContentColor.current
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(24.dp)) {
                AppIcon(destination.icon, Modifier.size(24.dp), color = color)
                if (selected && !expanded) {
                    Box(Modifier.align(Alignment.BottomCenter).padding(top = 2.dp).size(4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(TvColors.Accent))
                }
            }
            AnimatedVisibility(expanded, enter = fadeIn(), exit = fadeOut()) {
                Row {
                    Spacer(Modifier.width(TvSpacing.lg))
                    Text(label, style = TvType.BodyStrong, color = color, maxLines = 1)
                }
            }
        }
    }
}

private const val USER_FOCUS_WINDOW_MS = 600L
private const val CONTENT_FOCUS_ATTEMPTS = 100
private const val CONTENT_FOCUS_RETRY_MS = 120L

const val TAG_RAIL = "tv-rail"
const val TAG_CONTENT = "tv-shell-content"
fun railItemTag(destination: TvDestination) = "tv-rail-${destination.name}"
