package com.hhst.youtubelite.tv.shorts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.watch.TvKeyInput
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.watch.domain.VideoRating

enum class TvShortsStatus { Loading, Failed, Empty, Ready }

/** The Short on screen, as the side panel shows it. */
data class TvShortInfo(
    val id: String,
    val title: String,
    val channel: String,
    val detail: String = "",
    val artwork: String = "",
    val rating: VideoRating = VideoRating.None,
    val ratingPending: Boolean = false,
)

/** Playback facts the screen draws (from the shared engine, for the Short on screen only). */
data class TvShortPlayback(
    val playing: Boolean = false,
    val paused: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false,
    val durationMs: Long = 0L,
    /** The engine rendered a frame of this Short: the cover can go. */
    val videoReady: Boolean = false,
)

/** Remote commands of the Shorts player. UI tests drive a fake. */
interface TvShortsCallbacks {
    fun playPause()
    fun play()
    fun pause()
    fun seekBy(deltaMs: Long)
    fun next()
    fun previous()
    fun like()
    fun dislike()
    fun retry()
    fun retryPlayback()
}

/**
 * Full-screen Shorts: the 9:16 video centred, title, channel and actions beside it. Keys follow
 * [TvShortsKeyMapper]; holding OK reaches Like/Dislike ([canRate]), Back from there returns to the
 * video. [video] renders the engine's surface when it holds this Short.
 */
@Composable
fun TvShortsScreen(
    status: TvShortsStatus,
    info: TvShortInfo?,
    playback: TvShortPlayback,
    position: State<Long>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canRate: Boolean,
    callbacks: TvShortsCallbacks,
    modifier: Modifier = Modifier,
    video: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize().background(TvColors.Background).testTag(TAG_SHORTS)) {
        when (status) {
            TvShortsStatus.Loading -> Box(Modifier.fillMaxSize().testTag(TAG_SHORTS_LOADING), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().padding(vertical = TvSpacing.OverscanVertical).aspectRatio(9f / 16f)
                    .clip(RoundedCornerShape(TvSizes.Corner)).background(TvColors.Surface))
            }
            TvShortsStatus.Failed -> Message(stringResource(R.string.tv_shorts_failed_title), stringResource(R.string.tv_home_failed_message),
                callbacks::retry)
            TvShortsStatus.Empty -> Message(stringResource(R.string.tv_shorts_empty_title), null, callbacks::retry)
            TvShortsStatus.Ready -> Player(info, playback, position, hasPrevious, hasNext, canRate, callbacks, video)
        }
    }
}

@Composable
private fun Message(title: String, message: String?, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal), contentAlignment = Alignment.Center) {
        TvStateMessage(title, message = message, icon = AppIcon.WifiOff, actionLabel = stringResource(R.string.retry), onAction = onRetry,
            actionModifier = Modifier.testTag(TAG_SHORTS_RETRY), requestFocus = true)
    }
}

@Composable
private fun Player(
    info: TvShortInfo?,
    playback: TvShortPlayback,
    position: State<Long>,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canRate: Boolean,
    callbacks: TvShortsCallbacks,
    video: @Composable () -> Unit,
) {
    val mapper = remember { TvShortsKeyMapper() }
    val videoRequester = remember { FocusRequester() }
    val likeRequester = remember { FocusRequester() }
    val retryRequester = remember { FocusRequester() }
    var videoFocused by remember { mutableStateOf(false) }
    var actionsFocused by remember { mutableStateOf(false) }
    val actions by rememberUpdatedState(callbacks)
    val rateable by rememberUpdatedState(canRate && info != null)
    // Video first; a playback failure puts focus on its Retry.
    LaunchedEffect(playback.failed) {
        runCatching { if (playback.failed) retryRequester.requestFocus() else videoRequester.requestFocus() }
    }
    // Back from the buttons returns to the video; from the video it leaves (the activity's Back).
    BackHandler(enabled = actionsFocused) { runCatching { videoRequester.requestFocus() } }
    Row(
        Modifier.fillMaxSize()
            .padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical)
            .onPreviewKeyEvent { event ->
                when (val action = mapper.map(TvKeyInput.of(event.nativeKeyEvent), videoFocused)) {
                    TvShortsAction.PassThrough -> false
                    TvShortsAction.Consume -> true
                    TvShortsAction.TogglePlayPause -> { actions.playPause(); true }
                    TvShortsAction.Play -> { actions.play(); true }
                    TvShortsAction.Pause -> { actions.pause(); true }
                    is TvShortsAction.Seek -> { actions.seekBy(action.deltaMs); true }
                    TvShortsAction.Next -> { actions.next(); true }
                    TvShortsAction.Previous -> { actions.previous(); true }
                    TvShortsAction.FocusActions -> { if (rateable) runCatching { likeRequester.requestFocus() }; true }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.xxl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Chevron(visible = hasPrevious, up = true)
            BoxWithConstraints(Modifier.weight(1f)) {
                val height = maxHeight
                Box(
                    Modifier.height(height).width(height * 9f / 16f)
                        .clip(RoundedCornerShape(TvSizes.Corner))
                        .background(Color.Black)
                        .border(BorderStroke(if (videoFocused) 2.dp else 0.dp,
                            if (videoFocused) TvColors.Accent.copy(alpha = 0.6f) else Color.Transparent), RoundedCornerShape(TvSizes.Corner))
                        .focusRequester(videoRequester)
                        .onFocusChanged { videoFocused = it.isFocused }
                        .focusable()
                        .semantics { contentDescription = info?.title.orEmpty() }
                        .testTag(TAG_SHORTS_VIDEO),
                ) {
                    video()
                    if (!playback.videoReady && info != null) {
                        YoutubeThumb(info.artwork, Modifier.fillMaxSize(), placeholderColor = TvColors.Surface)
                    }
                    if (playback.paused && !playback.failed) {
                        Box(Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(TvColors.ScrimSoft)
                            .testTag(TAG_SHORTS_PAUSED), contentAlignment = Alignment.Center) {
                            AppIcon(AppIcon.Play, Modifier.size(36.dp), color = Color.White)
                        }
                    } else if (playback.loading && !playback.failed) {
                        Text(stringResource(R.string.tv_loading), style = TvType.Caption, color = Color.White,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = TvSpacing.xl)
                                .clip(RoundedCornerShape(6.dp)).background(TvColors.Scrim).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    Progress(position, playback.durationMs, Modifier.align(Alignment.BottomStart))
                }
            }
            Chevron(visible = hasNext, up = false)
        }
        Column(Modifier.width(SidePanel), verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            if (info != null) {
                Text(info.title, style = TvType.Title, color = TvColors.Ink, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(TAG_SHORTS_TITLE))
                if (info.channel.isNotBlank()) Text(info.channel, style = TvType.BodyStrong, color = TvColors.Secondary, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (info.detail.isNotBlank()) Text(info.detail, style = TvType.Caption, color = TvColors.Secondary, maxLines = 1)
            }
            if (playback.failed) {
                Spacer(Modifier.height(TvSpacing.sm))
                Text(stringResource(R.string.tv_player_failed), style = TvType.Body, color = TvColors.Ink)
                TvButton(stringResource(R.string.retry), callbacks::retryPlayback,
                    Modifier.focusRequester(retryRequester).testTag(TAG_SHORTS_PLAYBACK_RETRY), icon = AppIcon.Refresh, primary = true)
            }
            if (canRate && info != null) {
                Spacer(Modifier.height(TvSpacing.sm))
                Row(Modifier.onFocusChanged { actionsFocused = it.hasFocus }, horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                    TvButton(stringResource(R.string.tv_shorts_like), callbacks::like,
                        Modifier.focusRequester(likeRequester).testTag(TAG_SHORTS_LIKE),
                        icon = if (info.rating == VideoRating.Like) AppIcon.LikeFilled else AppIcon.Like,
                        primary = info.rating == VideoRating.Like, enabled = !info.ratingPending)
                    TvButton(stringResource(R.string.shorts_dislike), callbacks::dislike, Modifier.testTag(TAG_SHORTS_DISLIKE),
                        icon = if (info.rating == VideoRating.Dislike) AppIcon.DislikeFilled else AppIcon.Dislike,
                        primary = info.rating == VideoRating.Dislike, enabled = !info.ratingPending)
                }
            }
            Spacer(Modifier.height(TvSpacing.lg))
            Text(stringResource(if (canRate) R.string.tv_shorts_hint_rate else R.string.tv_shorts_hint), style = TvType.Caption,
                color = TvColors.Secondary)
        }
    }
}

@Composable
private fun Chevron(visible: Boolean, up: Boolean) {
    Box(Modifier.height(ChevronSlot), contentAlignment = Alignment.Center) {
        if (visible) Text(if (up) "▲" else "▼", style = TvType.Caption, color = TvColors.Secondary)
    }
}

@Composable
private fun Progress(position: State<Long>, durationMs: Long, modifier: Modifier) {
    if (durationMs <= 0) return
    Box(modifier.fillMaxWidth().height(4.dp).background(TvColors.Ghost)) {
        val fraction = (position.value.toFloat() / durationMs).coerceIn(0f, 1f)
        Box(Modifier.fillMaxWidth(fraction).height(4.dp).background(TvColors.BrandGradient))
    }
}

private val SidePanel = 360.dp
private val ChevronSlot = 20.dp

const val TAG_SHORTS = "tv-shorts"
const val TAG_SHORTS_LOADING = "tv-shorts-loading"
const val TAG_SHORTS_RETRY = "tv-shorts-retry"
const val TAG_SHORTS_VIDEO = "tv-shorts-video"
const val TAG_SHORTS_TITLE = "tv-shorts-title"
const val TAG_SHORTS_PAUSED = "tv-shorts-paused"
const val TAG_SHORTS_LIKE = "tv-shorts-like"
const val TAG_SHORTS_DISLIKE = "tv-shorts-dislike"
const val TAG_SHORTS_PLAYBACK_RETRY = "tv-shorts-playback-retry"
