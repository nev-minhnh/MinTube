package com.hhst.youtubelite.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinTubeBrand
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** The four button kinds of the Mint system. */
enum class MinButtonKind { Primary, Tonal, Outline, Text }

/**
 * Pill button, 40dp tall inside a 48dp touch target. [danger] switches the primary and text kinds
 * to the destructive color. A [pending] button shows progress and ignores taps.
 */
@Composable
fun MinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: MinButtonKind = MinButtonKind.Primary,
    icon: AppIcon? = null,
    enabled: Boolean = true,
    pending: Boolean = false,
    danger: Boolean = false,
    testTag: String? = null,
) {
    val container = when (kind) {
        MinButtonKind.Primary -> if (danger) DesignTokens.Danger else DesignTokens.Accent
        MinButtonKind.Tonal -> DesignTokens.AccentContainer
        MinButtonKind.Outline, MinButtonKind.Text -> Color.Transparent
    }
    val content = when (kind) {
        MinButtonKind.Primary -> if (danger) DesignTokens.OnDanger else DesignTokens.OnAccent
        MinButtonKind.Tonal -> DesignTokens.Ink
        MinButtonKind.Outline, MinButtonKind.Text -> if (danger) DesignTokens.Danger else DesignTokens.AccentText
    }
    val interactive = enabled && !pending
    val interactions = rememberAppInteractions()
    val haptic = rememberHapticAction()
    Box(
        modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .defaultMinSize(minWidth = MinSpace.Touch, minHeight = MinSpace.Touch)
            .clickable(interactionSource = interactions, indication = null, enabled = interactive, role = Role.Button) {
                haptic(); onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .alpha(if (enabled) 1f else 0.38f)
                .heightIn(min = 40.dp)
                .clip(CircleShape)
                .background(container)
                .then(if (kind == MinButtonKind.Outline) Modifier.border(1.dp, MaterialTheme.colorScheme.outline, CircleShape) else Modifier)
                .appStateLayer(interactions, 20.dp)
                .padding(horizontal = if (kind == MinButtonKind.Text) 12.dp else 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (pending) {
                ActionProgress(color = content)
                Spacer(Modifier.width(8.dp))
            } else if (icon != null) {
                AppIcon(icon, Modifier.size(18.dp), color = content)
                Spacer(Modifier.width(8.dp))
            }
            AppText(text, MinType.Label, color = content, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Filter chips
// ---------------------------------------------------------------------------------------------

/** One chip: 32dp pill inside a 48dp hit area; selected chips use AccentContainer. */
@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: AppIcon? = null) {
    val interactions = rememberAppInteractions()
    val haptic = rememberHapticAction()
    Box(
        modifier.heightIn(min = MinSpace.Touch)
            .selectable(selected = selected, interactionSource = interactions, indication = null, role = Role.Tab) { haptic(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.heightIn(min = 32.dp).clip(CircleShape)
                .background(if (selected) DesignTokens.AccentContainer else DesignTokens.Surface)
                .appStateLayer(interactions, 16.dp)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                AppIcon(icon, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
            }
            AppText(label, MinType.Label, maxLines = 1)
        }
    }
}

/** Scrollable chip row. Chip tags are `<tag>-<index>`. */
@Composable
fun FilterChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = MinSpace.lg),
    tag: String = "filter-chips",
) {
    LazyRow(
        modifier.fillMaxWidth().testTag(tag),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(labels.size, key = { it }) { index ->
            FilterChip(labels[index], index == selectedIndex, { onSelect(index) }, Modifier.testTag("$tag-$index"))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Skeletons
// ---------------------------------------------------------------------------------------------

/**
 * Sweeping highlight for placeholders. Static when the system animator scale is 0
 * ([LocalMotionScale]), so "remove animations" really removes it.
 */
fun Modifier.shimmer(): Modifier = composed {
    val scale = LocalMotionScale.current
    if (scale <= 0f) return@composed this
    val highlight = DesignTokens.Ghost
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "shimmer-phase",
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.6f
        val travel = size.width + band
        val start = (if (rtl) 1f - phase else phase) * travel - band
        drawRect(Brush.linearGradient(
            listOf(Color.Transparent, highlight, Color.Transparent),
            start = Offset(start, 0f), end = Offset(start + band, size.height),
        ))
    }
}

/** A placeholder block on the Surface tone with the shimmer highlight. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, shape: Shape = MinRadius.Badge) {
    Box(modifier.clip(shape).background(DesignTokens.Surface).shimmer())
}

/** A placeholder text line; [fraction] is the share of the available width. */
@Composable
fun SkeletonLine(modifier: Modifier = Modifier, fraction: Float = 1f, height: Dp = 14.dp) {
    SkeletonBox(modifier.fillMaxWidth(fraction).height(height), CircleShape)
}

// ---------------------------------------------------------------------------------------------
// Empty / error / offline
// ---------------------------------------------------------------------------------------------

enum class StateKind { Empty, Error, Offline }

/**
 * Full-area state: icon, headline, one line of help and up to two actions.
 * The caller owns the action; nothing here retries or loads.
 */
@Composable
fun StateMessage(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    kind: StateKind = StateKind.Empty,
    icon: AppIcon = when (kind) {
        StateKind.Empty -> AppIcon.VideoOutline
        StateKind.Error -> AppIcon.Error
        StateKind.Offline -> AppIcon.WifiOff
    },
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    tag: String = "state-message",
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = MinSpace.xxl, vertical = MinSpace.xxxl).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).background(
                if (kind == StateKind.Empty) DesignTokens.AccentContainer else DesignTokens.Surface, CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(32.dp), color = when (kind) {
                StateKind.Empty -> DesignTokens.AccentText
                StateKind.Error -> DesignTokens.Danger
                StateKind.Offline -> DesignTokens.Secondary
            })
        }
        Spacer(Modifier.height(MinSpace.xl))
        AppText(title, MinType.Display, Modifier.widthIn(max = 480.dp).semantics { heading() }, align = TextAlign.Center)
        if (message != null) {
            Spacer(Modifier.height(MinSpace.sm))
            AppText(message, MinType.Body, Modifier.widthIn(max = 480.dp), color = DesignTokens.Secondary, align = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MinSpace.xl))
            MinButton(actionLabel, onAction, testTag = "$tag-action")
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            MinButton(secondaryActionLabel, onSecondaryAction, kind = MinButtonKind.Text, testTag = "$tag-secondary")
        }
    }
}

/** The offline variant with its own copy; [onRetry] is the caller's existing retry. */
@Composable
fun OfflineMessage(onRetry: () -> Unit, modifier: Modifier = Modifier, tag: String = "state-offline") {
    StateMessage(
        stringResource(R.string.state_offline_title), modifier, stringResource(R.string.state_offline_message),
        StateKind.Offline, actionLabel = stringResource(R.string.retry), onAction = onRetry, tag = tag,
    )
}

// ---------------------------------------------------------------------------------------------
// Private browsing
// ---------------------------------------------------------------------------------------------

/**
 * Shown while private browsing is on. Stateless: the caller passes the flag it already has.
 * [compact] draws the icon alone (top bars); the label stays available to TalkBack.
 */
@Composable
fun PrivateBrowsingBadge(active: Boolean, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!active) return
    val label = stringResource(R.string.settings_private_browsing)
    Row(
        modifier.testTag("private-browsing-badge").clip(CircleShape).background(DesignTokens.Ink)
            .semantics(mergeDescendants = true) { contentDescription = label }
            .padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcon.PrivateBrowsing, Modifier.size(16.dp), color = DesignTokens.Background)
        if (!compact) {
            Spacer(Modifier.width(6.dp))
            AppText(label, MinType.Micro, color = DesignTokens.Background, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Top bar
// ---------------------------------------------------------------------------------------------

/**
 * 56dp top bar (grows with the font scale). Shows the wordmark when [title] is null. Put it inside
 * the existing `CollapsingAppBar` to collapse on scroll. [actions] are usually `AppIconButton`s and
 * a [MinAvatarButton].
 */
@Composable
fun MinTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    backDescription: String = stringResource(R.string.a11y_back),
    privateBrowsing: Boolean = false,
    tag: String = "min-topbar",
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).background(DesignTokens.Background).testTag(tag)
            .padding(start = if (onBack != null) 4.dp else MinSpace.lg, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) AppIconButton(AppIcon.Back, backDescription, Modifier.testTag("$tag-back"), onClick = onBack)
        if (title == null) MinTubeBrand()
        else AppText(title, MinType.Title, Modifier.weight(1f, fill = false).semantics { heading() }, maxLines = 1)
        if (privateBrowsing) PrivateBrowsingBadge(true, Modifier.padding(start = MinSpace.sm), compact = true)
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Account entry for top bars: the real avatar when the caller has one, the generic You icon otherwise. */
@Composable
fun MinAvatarButton(avatarAsset: String?, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = rememberHapticAction()
    Box(
        modifier.size(MinSpace.Touch).clip(CircleShape)
            .clickable(role = Role.Button) { haptic(); onClick() }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarAsset.isNullOrBlank()) AppIcon(AppIcon.You)
        else Artwork(avatarAsset, Modifier.size(32.dp).clip(CircleShape))
    }
}

/** Section header: Title style, optional trailing action. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
        AppText(title, MinType.Title, Modifier.weight(1f).semantics { heading() }, maxLines = 2)
        if (actionLabel != null && onAction != null) MinButton(actionLabel, onAction, kind = MinButtonKind.Text)
    }
}
