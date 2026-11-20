package com.hhst.youtubelite.shorts.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.shorts.domain.ShortItem
import com.hhst.youtubelite.shorts.domain.ShortsAction
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.SubscribeButton
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction
import com.hhst.youtubelite.watch.domain.VideoRating

internal val TopBarHeight = 56.dp
internal val AvatarSize = 36.dp
private val ProgressHeight = 3.dp
/** From this much room on each side of the 9:16 video, the channel block moves out of the frame. */
private val SideInfoMinWidth = 248.dp
private val SideInfoMaxWidth = 360.dp

/** The action rail's width; wider at large font scales so its labels keep their words whole. */
internal fun railWidth(fontScale: Float): Dp = if (fontScale > 1.5f) 96.dp else 64.dp

/** White text straight on video: a soft shadow keeps it legible where the scrim is thin. */
internal fun TextStyle.onVideo(): TextStyle =
    copy(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(0f, 1f), blurRadius = 4f))

/**
 * One short. Narrow windows are full-bleed with the rail and the channel block over the
 * video. When the window leaves room beside a 9:16 frame (landscape, tablets), the frame is
 * centered at full height, the rail sits beside it on the end edge and, with enough room,
 * the channel block sits beside it on the start edge.
 */
@Composable
internal fun ShortPage(item: ShortItem, onAction: (ShortsAction) -> Unit, videoContent: (@Composable (String) -> Unit)?,
    showArtwork: Boolean = true,
    videoReady: Boolean = false,
    playbackControls: (@Composable (String) -> Unit)? = null,
    playbackProgress: (@Composable (String) -> Unit)? = null,
    rating: VideoRating = VideoRating.None,
    subscribed: Boolean = false, subscribePending: Boolean = false, ratingPending: Boolean = false, menuVisible: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxSize().testTag("short-page-${item.id}")) {
        val rail = railWidth(LocalDensity.current.fontScale)
        val frameWidth = maxHeight * 9f / 16f
        val side = (maxWidth - frameWidth) / 2
        val wide = side >= rail + MinSpace.xxl
        val sideInfo = wide && side >= SideInfoMinWidth
        val vertical = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical)
        val chromeMaxHeight = (maxHeight - TopBarHeight - MinSpace.xxl).coerceAtLeast(0.dp)

        // One call site for the stage in every layout, so a resize never remounts the video surface.
        Box(Modifier.align(Alignment.Center)
            .then(if (wide) Modifier.width(frameWidth).fillMaxHeight() else Modifier.fillMaxSize())) {
            videoContent?.invoke(item.id)
            if (!videoReady) {
                val cover = Modifier.fillMaxSize().background(Color.Black).testTag("short-cover-${item.id}")
                // Live pages only reveal decoder output, including neighbors during a swipe.
                if (!showArtwork) Box(cover)
                else Artwork(item.artwork, cover.appDescription(R.string.a11y_play_title, item.title),
                    contentScale = ContentScale.Fit)
            }
            if (!sideInfo) Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        }
        playbackControls?.invoke(item.id)

        if (menuVisible) AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, item.title),
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = MinSpace.xs, end = MinSpace.xs).testTag("short-more-${item.id}"),
            color = Color.White) {
            onAction(ShortsAction.OpenMenu(item.id))
        }

        // Rail: over the end edge of a full-bleed video, or beside the centered frame.
        Box(Modifier.align(Alignment.BottomEnd)
            .then(if (wide) Modifier.width(side) else Modifier)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(start = if (wide) MinSpace.sm else 0.dp, end = if (wide) 0.dp else MinSpace.xs, bottom = MinSpace.lg)) {
            ShortRail(item, onAction, rating, ratingPending,
                Modifier.align(Alignment.BottomStart).width(rail).heightIn(max = chromeMaxHeight))
        }

        // Channel block: bottom-start of the video, or bottom of the start-side margin.
        val info = Modifier.heightIn(max = chromeMaxHeight)
        when {
            sideInfo -> Box(Modifier.align(Alignment.BottomStart).width(side)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(MinSpace.lg)) {
                ShortInfo(item, onAction, subscribed, subscribePending,
                    info.align(Alignment.BottomEnd).widthIn(max = SideInfoMaxWidth))
            }
            wide -> Box(Modifier.align(Alignment.BottomCenter).width(frameWidth)
                .windowInsetsPadding(vertical).padding(MinSpace.lg)) {
                ShortInfo(item, onAction, subscribed, subscribePending, info.align(Alignment.BottomStart))
            }
            else -> Box(Modifier.align(Alignment.BottomStart).windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(start = MinSpace.lg, end = rail + MinSpace.md, bottom = MinSpace.lg)) {
                ShortInfo(item, onAction, subscribed, subscribePending, info.align(Alignment.BottomStart))
            }
        }

        if (playbackProgress != null) Box(Modifier.align(Alignment.BottomCenter)
            .then(if (wide) Modifier.width(frameWidth) else Modifier.fillMaxWidth())
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
            playbackProgress(item.id)
        }
    }
}

@Composable
private fun ShortRail(item: ShortItem, onAction: (ShortsAction) -> Unit, rating: VideoRating, ratingPending: Boolean,
    modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).testTag("short-rail-${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val liked = rating == VideoRating.Like
        val disliked = rating == VideoRating.Dislike
        RailAction(if (liked) AppIcon.LikeFilled else AppIcon.Like, stringResource(R.string.ui_like_1_s, item.title),
            item.likes, selected = liked, pending = ratingPending,
            onClick = { onAction(ShortsAction.Like(item.id)) })
        Spacer(Modifier.height(MinSpace.sm))
        RailAction(if (disliked) AppIcon.DislikeFilled else AppIcon.Dislike, stringResource(R.string.ui_dislike_1_s, item.title),
            stringResource(R.string.shorts_dislike), selected = disliked, pending = ratingPending,
            onClick = { onAction(ShortsAction.Dislike(item.id)) })
        Spacer(Modifier.height(MinSpace.sm))
        RailAction(AppIcon.Comment, stringResource(R.string.ui_comments_for_1_s, item.title), item.comments,
            onClick = { onAction(ShortsAction.OpenComments(item.id)) })
        Spacer(Modifier.height(MinSpace.sm))
        RailAction(AppIcon.Share, stringResource(R.string.ui_share_1_s, item.title), stringResource(R.string.share),
            onClick = { onAction(ShortsAction.Share(item.id)) })
    }
}

/** A 48dp action on a scrim disc with its label below; [selected] is null for plain actions. */
@Composable
private fun RailAction(icon: AppIcon, description: String, label: String, onClick: () -> Unit,
    selected: Boolean? = null, pending: Boolean = false) {
    val interactions = rememberAppInteractions()
    val haptic = rememberHapticAction()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(MinSpace.Touch).clip(MinRadius.Full).background(Color.Black.copy(alpha = 0.28f))
            .appStateLayer(interactions, MinSpace.Touch / 2)
            .clickable(interactionSource = interactions, indication = null, enabled = !pending, role = Role.Button) {
                haptic(); onClick()
            }
            .semantics {
                contentDescription = description
                if (selected != null) this.selected = selected
            }, contentAlignment = Alignment.Center) {
            if (pending) ActionProgress(color = Color.White)
            else AppIcon(icon, color = if (selected == true) DesignTokens.Accent else Color.White)
        }
        if (label.isNotEmpty()) {
            AppText(label, RailLabel, Modifier.testTag("rail-label-$description"),
                color = Color.White, maxLines = 2, align = TextAlign.Center)
        } else {
            // Keep the rail row's height stable when an action has no count label yet.
            Spacer(Modifier.height(with(LocalDensity.current) { RailLabel.lineHeight.toDp() }))
        }
    }
}

private val RailLabel = MinType.Caption.copy(fontWeight = FontWeight.Medium).onVideo()

@Composable
private fun ShortInfo(item: ShortItem, onAction: (ShortsAction) -> Unit, subscribed: Boolean, subscribePending: Boolean,
    modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.heightIn(min = 40.dp).testTag("short-channel-${item.id}"),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f, fill = false).heightIn(min = MinSpace.Touch).clip(MinRadius.Full)
                .clickable(enabled = item.channelId.isNotBlank(), role = Role.Button) {
                    onAction(ShortsAction.OpenChannel(item.channelId))
                }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(AvatarSize).clip(MinRadius.Full).background(DesignTokens.Ghost)
                    .appDescription(R.string.a11y_go_to_channel, item.author), contentAlignment = Alignment.Center) {
                    if (item.avatar.isNotBlank()) Artwork(item.avatar, Modifier.fillMaxSize())
                    else AppText(item.author.take(1).uppercase(), MinType.Subtitle, color = Color.White)
                }
                Column(Modifier.padding(start = MinSpace.sm, end = MinSpace.xs)) {
                    AppText(item.author, MinType.Body.copy(fontWeight = FontWeight.Medium).onVideo(),
                        color = Color.White, maxLines = 1)
                    if (item.views.isNotBlank()) AppText(item.views, MinType.Caption.onVideo(),
                        Modifier.testTag("short-views-${item.id}"), color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
            }
            SubscribeButton(subscribed, { onAction(ShortsAction.Subscribe(item.channelId)) },
                Modifier.padding(start = MinSpace.sm), enabled = item.channelId.isNotBlank(),
                pending = subscribePending, onDarkSurface = true,
                testTag = "short-subscribe-${item.id}", channelId = item.channelId)
        }
        ShortTitle(item)
    }
}

/** Two lines of title; a longer one expands in place on tap. */
@Composable
private fun ShortTitle(item: ShortItem) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var overflows by remember(item.id) { mutableStateOf(false) }
    val toggle = stringResource(if (expanded) R.string.ui_show_less else R.string.ui_show_more)
    BasicText(
        rememberDeArrowTitle(item.id, item.title),
        Modifier.padding(top = MinSpace.sm).testTag("short-title-${item.id}")
            .then(if (expanded || overflows) Modifier.clickable(onClickLabel = toggle) { expanded = !expanded } else Modifier),
        style = MinType.Body.onVideo().copy(color = Color.White),
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!expanded) overflows = it.hasVisualOverflow },
    )
}

/**
 * The playback position of the playing short as a thin Accent line. [positionMs] is read in
 * the draw phase, so position ticks never recompose the page.
 */
@Composable
internal fun ShortProgressBar(positionMs: () -> Long, durationMs: Long, modifier: Modifier = Modifier) {
    if (durationMs <= 0L) return
    val accent = DesignTokens.Accent
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val description = stringResource(R.string.shorts_progress)
    Box(modifier.fillMaxWidth().height(ProgressHeight).testTag("short-progress")
        .semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo((positionMs().toFloat() / durationMs).coerceIn(0f, 1f), 0f..1f)
        }
        .drawBehind {
            val played = size.width * (positionMs().toFloat() / durationMs).coerceIn(0f, 1f)
            drawRect(Color.White.copy(alpha = 0.24f))
            drawRect(accent, topLeft = Offset(if (rtl) size.width - played else 0f, 0f), size = Size(played, size.height))
        })
}
