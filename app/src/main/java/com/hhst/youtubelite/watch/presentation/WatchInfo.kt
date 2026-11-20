package com.hhst.youtubelite.watch.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppMotion
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.SubscribeButton
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.motionDuration
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction
import com.hhst.youtubelite.watch.domain.VideoRating
import com.hhst.youtubelite.watch.domain.WatchAction
import com.hhst.youtubelite.watch.domain.WatchCommentsStatus
import com.hhst.youtubelite.watch.domain.WatchProvisional
import com.hhst.youtubelite.watch.domain.WatchUiState
import com.hhst.youtubelite.watch.domain.WatchVideo

/*
 * The information block under the player: title, metadata line, action pills, channel row,
 * description card and the comments teaser. Everything is stateless; the description's
 * expanded flag stays in WatchUiState so it survives recreation.
 */

@Composable
internal fun WatchMetadata(
    video: WatchVideo,
    state: WatchUiState,
    inset: Dp,
    onAction: (WatchAction) -> Unit,
    onCopyLink: ((String) -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().testTag("watch-metadata")) {
        Column(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.md)) {
            WatchTitle(rememberDeArrowTitle(video.id, video.title), expanded = state.descriptionExpanded)
            Spacer(Modifier.height(MinSpace.xs))
            WatchMetaRow(video.live, video.views, video.age, liveBadgeTag = "watch-live-badge")
        }
        Spacer(Modifier.height(MinSpace.xs))
        WatchActionBar(video, state.actionPending, state.pendingVideoAction, state.live, inset, onAction, onCopyLink)
        WatchChannelRow(video, state.actionPending, state.pendingVideoAction, inset, onAction)
        WatchDescriptionCard(video, state.descriptionExpanded, inset, onAction)
    }
}

@Composable
private fun WatchTitle(title: String, expanded: Boolean = false) {
    AppText(
        title, MinType.Title,
        Modifier.fillMaxWidth().testTag("watch-title").semantics { heading() },
        maxLines = if (expanded) Int.MAX_VALUE else 2,
    )
}

/** Views and age on one wrapping line; a live stream leads with the badge and has no age. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchMetaRow(live: Boolean, views: String, age: String, liveBadgeTag: String? = null) {
    FlowRow(
        Modifier.fillMaxWidth().testTag("watch-meta"),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
        verticalArrangement = Arrangement.spacedBy(MinSpace.xxs),
    ) {
        if (live) {
            Box(
                Modifier.align(Alignment.CenterVertically).background(DesignTokens.Danger, MinRadius.Badge)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .then(if (liveBadgeTag != null) Modifier.testTag(liveBadgeTag) else Modifier),
            ) {
                AppText(stringResource(R.string.player_live), MinType.Micro, color = DesignTokens.OnDanger, maxLines = 1)
            }
        }
        if (views.isNotBlank()) AppText(views, MinType.Caption, Modifier.align(Alignment.CenterVertically), color = DesignTokens.Secondary)
        if (!live && age.isNotBlank()) {
            if (views.isNotBlank()) AppText("•", MinType.Caption, Modifier.align(Alignment.CenterVertically).clearAndSetSemantics {}, color = DesignTokens.Secondary)
            AppText(age, MinType.Caption, Modifier.align(Alignment.CenterVertically), color = DesignTokens.Secondary)
        }
    }
}

private class WatchPillSpec(
    val key: String,
    val icon: AppIcon,
    val selectedIcon: AppIcon,
    val description: String,
    val label: String?,
    val action: WatchAction?,
    /** Counted pills keep the old `-count` tag; text pills use `-label`. */
    val counted: Boolean = false,
)

@Composable
private fun WatchActionBar(
    video: WatchVideo,
    pending: Boolean,
    pendingAction: WatchAction?,
    live: Boolean,
    inset: Dp,
    onAction: (WatchAction) -> Unit,
    onCopyLink: ((String) -> Unit)?,
) {
    val pills = listOfNotNull(
        WatchPillSpec("like", AppIcon.Like, AppIcon.LikeFilled, stringResource(R.string.ui_like_this_video),
            video.likeCount ?: video.likes.removeSuffix(" likes").takeIf { it.isNotBlank() }, WatchAction.Like, counted = true),
        WatchPillSpec("dislike", AppIcon.Dislike, AppIcon.DislikeFilled, stringResource(R.string.ui_dislike_this_video),
            video.dislikeCount, WatchAction.Dislike, counted = true),
        WatchPillSpec("share", AppIcon.Share, AppIcon.Share, stringResource(R.string.share), stringResource(R.string.share), WatchAction.Share),
        WatchPillSpec("save", AppIcon.Bookmark, AppIcon.BookmarkFilled, stringResource(R.string.comments_composer_save),
            stringResource(R.string.comments_composer_save), WatchAction.Save),
        WatchPillSpec("download", AppIcon.Download, AppIcon.Download, stringResource(R.string.download),
            stringResource(R.string.download), WatchAction.Download),
        WatchPillSpec("queue", AppIcon.QueueAdd, AppIcon.QueueAdd, stringResource(R.string.add_to_queue),
            stringResource(R.string.add_to_queue), WatchAction.Queue),
        if (onCopyLink != null) WatchPillSpec("copy-link", AppIcon.Link, AppIcon.Link, stringResource(R.string.ui_copy_link),
            stringResource(R.string.ui_copy_link), null) else null,
        WatchPillSpec("open-with", AppIcon.OpenWith, AppIcon.OpenWith, stringResource(R.string.open_with),
            stringResource(R.string.open_with), WatchAction.OpenWith),
        // Report's submission needs a feedback token the page never carries; it hides in production.
        if (!live) WatchPillSpec("report", AppIcon.Report, AppIcon.Report, stringResource(R.string.ui_report),
            stringResource(R.string.ui_report), WatchAction.Report) else null,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).testTag("watch-actions")
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), verticalAlignment = Alignment.CenterVertically,
    ) {
        pills.forEach { pill ->
            val rating = pill.action == WatchAction.Like || pill.action == WatchAction.Dislike
            WatchPill(
                icon = pill.icon, selectedIcon = pill.selectedIcon, description = pill.description,
                label = pill.label, labelTag = if (pill.counted) "count" else "label",
                selected = (pill.action == WatchAction.Like && video.rating == VideoRating.Like) ||
                    (pill.action == WatchAction.Dislike && video.rating == VideoRating.Dislike),
                // A rating in flight owns the pair; the other actions stay usable.
                enabled = !rating || !pending,
                pending = pill.action != null && pendingAction == pill.action,
                testTag = "watch-action-${pill.key}",
                onClick = { if (pill.action != null) onAction(pill.action) else onCopyLink?.invoke(video.id) },
            )
        }
    }
}

/** Tonal pill: 36dp surface inside a 48dp target; selected pills take the accent container. */
@Composable
private fun WatchPill(
    icon: AppIcon,
    selectedIcon: AppIcon,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    label: String? = null,
    labelTag: String = "label",
    selected: Boolean = false,
    enabled: Boolean = true,
    pending: Boolean = false,
) {
    val haptic = rememberHapticAction()
    val interactions = rememberAppInteractions()
    val content = if (selected) DesignTokens.AccentText else DesignTokens.Ink
    Box(
        Modifier.heightIn(min = MinSpace.Touch).widthIn(min = MinSpace.Touch)
            .clickable(enabled = enabled, interactionSource = interactions, indication = null, role = Role.Button) { haptic(); onClick() }
            .semantics { contentDescription = description; this.selected = selected }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.heightIn(min = 36.dp).clip(CircleShape)
                .background(if (selected) DesignTokens.AccentContainer else DesignTokens.Surface)
                .appStateLayer(interactions, 18.dp)
                .testTag("$testTag-surface").padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pending) ActionProgress(Modifier.size(20.dp), color = content)
            else AppIcon(if (selected) selectedIcon else icon, Modifier.size(20.dp).testTag("$testTag-icon"), color = content)
            if (!label.isNullOrBlank()) {
                Spacer(Modifier.width(MinSpace.sm))
                AppText(label, MinType.Label, Modifier.testTag("$testTag-$labelTag"), color = content, maxLines = 1)
            }
        }
    }
}

@Composable
private fun WatchChannelRow(video: WatchVideo, pending: Boolean, pendingAction: WatchAction?, inset: Dp, onAction: (WatchAction) -> Unit) {
    val openChannel = { onAction(WatchAction.OpenChannel(video.channelId)) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = inset - MinSpace.xs, end = inset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(MinSpace.Touch).clip(CircleShape).testTag("watch-action-avatar")
                .appDescription(R.string.a11y_go_to_channel, video.channel)
                .clickable(enabled = video.channelId.isNotBlank(), role = Role.Button, onClick = openChannel),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(video.avatar.orEmpty(), Modifier.size(40.dp).clip(CircleShape))
        }
        Spacer(Modifier.width(MinSpace.sm))
        Column(
            Modifier.weight(1f).padding(end = MinSpace.sm)
                .clickable(enabled = video.channelId.isNotBlank(), role = Role.Button, onClick = openChannel),
        ) {
            AppText(video.channel, MinType.Subtitle, Modifier.testTag("watch-channel-name"), maxLines = 2)
            if (video.handle.isNotBlank()) AppText(video.handle, MinType.Caption, Modifier.testTag("watch-channel-handle"),
                color = DesignTokens.Secondary, maxLines = 1)
        }
        SubscribeButton(video.subscribed, { onAction(WatchAction.Subscribe) },
            Modifier.testTag("watch-action-subscribe"), enabled = video.channelId.isNotBlank() && !pending,
            pending = pendingAction == WatchAction.Subscribe, onDarkSurface = false, channelId = video.channelId)
    }
}

/**
 * The description lives in a tonal card: a two-line teaser while collapsed, the full linked
 * text once expanded. The toggle keeps its `watch-more` tag and Show more / Show less label.
 */
@Composable
private fun WatchDescriptionCard(video: WatchVideo, expanded: Boolean, inset: Dp, onAction: (WatchAction) -> Unit) {
    val motionScale = LocalMotionScale.current
    val toggle = { onAction(WatchAction.ToggleDescription) }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = inset, vertical = MinSpace.xs).clip(MinRadius.Card)
            .background(DesignTokens.Surface).testTag("watch-description-card")
            // Expanded text carries links and selection; only the collapsed card is one big target.
            .then(if (expanded) Modifier else Modifier.clickable(onClick = toggle))
            .padding(start = MinSpace.md, end = MinSpace.xs, bottom = MinSpace.md),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(R.string.video_description), MinType.Label, Modifier.weight(1f).semantics { heading() })
            Row(
                Modifier.heightIn(min = MinSpace.Touch).clip(CircleShape).testTag("watch-more")
                    .appDescription(if (expanded) R.string.ui_show_less else R.string.ui_show_more)
                    .clickable(role = Role.Button, onClick = toggle).padding(horizontal = MinSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppText(stringResource(if (expanded) R.string.ui_show_less else R.string.ui_show_more), MinType.Label,
                    Modifier.clearAndSetSemantics {}, color = DesignTokens.AccentText, maxLines = 1)
                AppIcon(if (expanded) AppIcon.ChevronUp else AppIcon.ChevronDown, Modifier.size(20.dp), color = DesignTokens.AccentText)
            }
        }
        val teaser = remember(video.description) { video.description.trim().replace(Regex("\\s*\\n\\s*"), " ") }
        if (!expanded && teaser.isNotBlank()) AppText(
            teaser, MinType.Body,
            Modifier.fillMaxWidth().padding(end = MinSpace.sm).testTag("watch-description-teaser"),
            color = DesignTokens.Secondary, maxLines = 2,
        )
        AnimatedVisibility(
            expanded,
            enter = expandVertically(tween(motionDuration(AppMotion.ExpandMs, motionScale))),
            exit = shrinkVertically(tween(motionDuration(AppMotion.CollapseMs, motionScale))),
        ) {
            WatchDescription(
                video.description,
                onTimestamp = { onAction(WatchAction.SeekToTimestamp(it)) },
                onLink = { onAction(WatchAction.OpenDescriptionLink(it)) },
                modifier = Modifier.fillMaxWidth().padding(end = MinSpace.sm).testTag("watch-description"),
            )
        }
    }
}

@Composable
internal fun WatchComments(video: WatchVideo, inset: Dp, onAction: (WatchAction) -> Unit) {
    val liveChat = video.live && !video.chatKey.isNullOrBlank()
    val chatFailed = video.live && video.chatLookupFailed
    // A live stream without a chat continuation has no usable chat: show the
    // unavailable card, not an ordinary comments panel.
    val chatUnavailable = video.live && video.chatKey.isNullOrBlank() && !chatFailed
    val hasPreview = !video.live && video.commentsStatus == WatchCommentsStatus.Ready && video.topComment.text.isNotBlank()
    Box(Modifier.fillMaxWidth().testTag("watch-comments").padding(horizontal = inset, vertical = MinSpace.xs)) {
        Column(
            Modifier.fillMaxWidth().clip(MinRadius.Card).background(DesignTokens.Surface)
                .testTag("watch-comments-card")
                .clickable(enabled = !chatUnavailable, role = Role.Button) {
                    onAction(if (chatFailed) WatchAction.Retry else WatchAction.OpenComments(video.id))
                }
                .padding(horizontal = MinSpace.md, vertical = MinSpace.md),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppText(stringResource(if (video.live) R.string.chat_live else R.string.comments_title), MinType.Label)
                Spacer(Modifier.width(MinSpace.sm))
                if (!video.live) AppText(video.commentCount, MinType.Caption, Modifier.weight(1f), color = DesignTokens.Secondary, maxLines = 1)
                else Spacer(Modifier.weight(1f))
                if (!chatUnavailable) AppIcon(if (chatFailed) AppIcon.Refresh else AppIcon.ChevronRight, Modifier.size(20.dp), color = DesignTokens.Secondary)
            }
            Spacer(Modifier.height(MinSpace.sm))
            Row(Modifier.fillMaxWidth().heightIn(min = 32.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasPreview) {
                    Artwork(video.topComment.artwork, Modifier.size(28.dp).clip(CircleShape))
                    Spacer(Modifier.width(MinSpace.md))
                }
                AppText(when {
                    liveChat -> stringResource(R.string.chat_join_live)
                    chatFailed -> stringResource(R.string.chat_load_failed)
                    chatUnavailable -> stringResource(R.string.chat_unavailable)
                    else -> when (video.commentsStatus) {
                        WatchCommentsStatus.Loading -> stringResource(R.string.ui_loading_comments)
                        WatchCommentsStatus.Empty -> stringResource(R.string.ui_no_comments_yet_2)
                        WatchCommentsStatus.Disabled -> stringResource(R.string.ui_comments_are_turned_off_2)
                        WatchCommentsStatus.Failed -> stringResource(R.string.ui_couldn_t_load_comments_tap_to_view)
                        WatchCommentsStatus.Ready -> video.topComment.text
                    }
                }, MinType.Body, Modifier.weight(1f).testTag("watch-comment-preview"),
                    color = if (hasPreview) DesignTokens.Ink else DesignTokens.Secondary, maxLines = 2)
            }
        }
    }
}

/**
 * Paints the tapped card's known fields while the page read is in flight; unknown
 * slots stay blank, no inert controls render, and the real block replaces this
 * without a full-page reset.
 */
@Composable
internal fun WatchProvisionalHeader(known: WatchProvisional, inset: Dp) {
    Column(Modifier.fillMaxWidth().testTag("watch-provisional")) {
        Column(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.md)) {
            WatchTitle(rememberDeArrowTitle(known.videoId, known.title))
            Spacer(Modifier.height(MinSpace.xs))
            WatchMetaRow(known.live, known.views, known.age)
        }
        Spacer(Modifier.height(MinSpace.xs))
        // The pills are not known yet: hold their row so the channel does not jump when they land.
        Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).padding(horizontal = inset).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { SkeletonBox(Modifier.size(84.dp, 36.dp), CircleShape) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = inset - MinSpace.xs, end = inset),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(MinSpace.Touch).testTag("watch-action-avatar"), contentAlignment = Alignment.Center) {
                Artwork(known.avatar.orEmpty(), Modifier.size(40.dp).clip(CircleShape))
            }
            Spacer(Modifier.width(MinSpace.sm))
            AppText(known.channel, MinType.Subtitle, Modifier.weight(1f).testTag("watch-channel-name"), maxLines = 2)
        }
        Spacer(Modifier.height(MinSpace.md))
    }
}

@Composable
internal fun WatchSkeleton(inset: Dp) {
    Column(Modifier.fillMaxWidth().testTag("watch-skeleton").clearAndSetSemantics {}) {
        Column(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.md)) {
            SkeletonLine(fraction = 0.92f, height = 20.dp)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.5f, height = 12.dp)
        }
        Spacer(Modifier.height(MinSpace.sm))
        Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).padding(horizontal = inset),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { SkeletonBox(Modifier.size(84.dp, 36.dp), CircleShape) }
        }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = inset), verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(40.dp), CircleShape)
            Spacer(Modifier.width(MinSpace.md))
            Column(Modifier.weight(1f)) {
                SkeletonLine(fraction = 0.6f, height = 16.dp)
                Spacer(Modifier.height(MinSpace.xs))
                SkeletonLine(fraction = 0.35f, height = 12.dp)
            }
            Spacer(Modifier.width(MinSpace.xxl))
            SkeletonBox(Modifier.size(96.dp, 40.dp), CircleShape)
        }
        SkeletonBox(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = MinSpace.xs).height(72.dp), MinRadius.Card)
        SkeletonBox(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = MinSpace.xs).height(88.dp), MinRadius.Card)
    }
}

@Composable
internal fun WatchError(onAction: (WatchAction) -> Unit) {
    StateMessage(
        stringResource(R.string.ui_something_went_wrong),
        message = stringResource(R.string.ui_please_try_again),
        kind = StateKind.Error,
        actionLabel = stringResource(R.string.retry),
        onAction = { onAction(WatchAction.Retry) },
        tag = "watch-error",
    )
}
