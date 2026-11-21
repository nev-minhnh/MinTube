package com.hhst.youtubelite.tv.watch

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.player.PlayerUiState
import com.hhst.youtubelite.player.surface.PlayerUi
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvCard
import com.hhst.youtubelite.tv.design.TvChip
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvRowHeader
import com.hhst.youtubelite.tv.design.TvSizes
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.TvVideo
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon
import kotlinx.coroutines.delay

/** Title block shown over the video. */
data class TvWatchInfo(val title: String, val channel: String = "", val detail: String = "")

enum class TvSettingKind { Quality, Speed, Captions, Audio, Chapters }

/** One choice in an open settings list. */
data class TvOption(val key: String, val label: String, val selected: Boolean)

/**
 * The TV watch screen: [video] full screen, a remote-driven overlay on top. Keys go through
 * [TvPlayerKeyMapper]; overlay rows (settings, up next) take focus when they open, and moving
 * focus back onto the seek bar returns to the plain controls. State-hoisted: the activity passes
 * the shared player state and [TvPlayerCallbacks]; tests pass a fake.
 */
@Composable
fun TvPlayerScreen(
    player: PlayerUiState,
    position: State<Long>,
    buffered: State<Long>,
    /** Target of an in-progress seek (scrub preview), null when none. */
    seekTarget: State<Long?>,
    info: TvWatchInfo,
    upNext: List<TvVideo>,
    autoplayEnabled: Boolean,
    callbacks: TvPlayerCallbacks,
    modifier: Modifier = Modifier,
    keyMapper: TvPlayerKeyMapper = remember { TvPlayerKeyMapper() },
    video: @Composable () -> Unit = {},
) {
    var mode by remember { mutableStateOf(TvOverlayMode.Hidden) }
    var options by remember { mutableStateOf<TvSettingKind?>(null) }
    var seekBarFocused by remember { mutableStateOf(false) }
    var rootFocused by remember { mutableStateOf(false) }
    var seekOffset by remember { mutableLongStateOf(0L) }
    var interactions by remember { mutableIntStateOf(0) }
    var cancelledFor by remember { mutableStateOf<String?>(null) }
    val root = remember { FocusRequester() }
    val seekBar = remember { FocusRequester() }
    val settings = remember { FocusRequester() }
    val optionList = remember { FocusRequester() }
    val upNextRow = remember { FocusRequester() }
    val retry = remember { FocusRequester() }
    val latestPlayer by rememberUpdatedState(player)
    val candidate = upNext.firstOrNull { it.id != player.videoId }
    val countdown by remember(player.videoId, player.durationMs, player.isLive, player.ended, autoplayEnabled, candidate, cancelledFor) {
        derivedStateOf {
            TvAutoplay.secondsLeft(position.value, player.durationMs, player.isLive, autoplayEnabled,
                candidate != null, cancelledFor == player.videoId, player.ended)
        }
    }
    val error = player.error != null

    fun show(next: TvOverlayMode) {
        mode = next
        options = null
    }

    fun handle(action: TvPlayerAction): Boolean {
        when (action) {
            TvPlayerAction.PassThrough -> return false
            TvPlayerAction.Consume -> Unit
            is TvPlayerAction.TogglePlayPause -> {
                callbacks.playPause()
                if (action.showControls && mode == TvOverlayMode.Hidden) show(TvOverlayMode.Controls)
            }
            TvPlayerAction.Play -> callbacks.play()
            TvPlayerAction.Pause -> callbacks.pause()
            is TvPlayerAction.Seek -> {
                if (latestPlayer.durationMs > 0 || latestPlayer.isLive) {
                    if (mode == TvOverlayMode.Hidden) show(TvOverlayMode.Controls)
                    seekOffset += action.deltaMs
                    callbacks.seekPreview(seekOffset)
                }
            }
            TvPlayerAction.CommitSeek -> if (seekOffset != 0L) {
                callbacks.seekCommit(seekOffset)
                seekOffset = 0L
            }
            is TvPlayerAction.Show -> show(action.mode)
            TvPlayerAction.CloseOptions -> options = null
            TvPlayerAction.Hide -> show(TvOverlayMode.Hidden)
            TvPlayerAction.Exit -> callbacks.exit()
            TvPlayerAction.Next -> callbacks.next()
            TvPlayerAction.Previous -> callbacks.previous()
            TvPlayerAction.PlayUpNext -> callbacks.next()
            TvPlayerAction.CancelAutoplay -> {
                cancelledFor = latestPlayer.videoId
                callbacks.cancelAutoplay()
            }
        }
        return true
    }

    // Right after the controls open, focus may still sit on the root for a frame: treat it as the bar.
    fun context() = TvPlayerKeyContext(mode, seekBarFocused || rootFocused && mode == TvOverlayMode.Controls,
        options != null, countdown != null)

    // Back is delivered through the dispatcher (predictive back), not as a key event.
    BackHandler { handle(keyMapper.map(TvKeyInput(AndroidKeyEvent.KEYCODE_BACK, down = true), context())) }

    // A held seek key whose release was lost (focus moved, window changed) still lands.
    LaunchedEffect(seekOffset) {
        if (seekOffset == 0L) return@LaunchedEffect
        delay(SEEK_COMMIT_FALLBACK_MS)
        handle(TvPlayerAction.CommitSeek)
    }
    // Controls fade after a while of no input during playback; rows and option lists stay.
    LaunchedEffect(mode, interactions, player.isPlaying, options) {
        if (mode != TvOverlayMode.Controls || !player.isPlaying || options != null) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        show(TvOverlayMode.Hidden)
    }
    LaunchedEffect(error) { if (error) show(TvOverlayMode.Controls) }
    // Each mode owns a focus target; the root keeps focus with the overlay hidden so keys arrive.
    LaunchedEffect(mode, options, error) {
        withFrameNanos { }
        val target = when {
            error && mode == TvOverlayMode.Controls -> retry
            mode == TvOverlayMode.Hidden -> root
            mode == TvOverlayMode.Controls -> seekBar
            mode == TvOverlayMode.Settings -> if (options != null) optionList else settings
            else -> upNextRow
        }
        if (!runCatching { target.requestFocus(FocusDirection.Enter) }.getOrDefault(false)) {
            runCatching { root.requestFocus(FocusDirection.Enter) }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.keyCode == AndroidKeyEvent.KEYCODE_BACK) return@onPreviewKeyEvent false
                interactions++
                handle(keyMapper.map(TvKeyInput.of(native), context()))
            }
            .focusRequester(root)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .testTag(TAG_PLAYER),
    ) {
        video()
        if (player.loading && !error) {
            TvSpinner(Modifier.align(Alignment.Center))
        }
        AnimatedVisibility(mode != TvOverlayMode.Hidden, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().height(180.dp).background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent))))
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(340.dp).background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)))))
                TitleBlock(info, player, Modifier.align(Alignment.TopStart)
                    .padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical))
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth()
                        .padding(bottom = TvSpacing.OverscanVertical),
                    verticalArrangement = Arrangement.spacedBy(TvSpacing.md),
                ) {
                    if (error) {
                        Row(Modifier.padding(horizontal = TvSpacing.OverscanHorizontal), verticalAlignment = Alignment.CenterVertically) {
                            Text(player.error.orEmpty().ifBlank { stringResource(R.string.tv_player_failed) },
                                style = TvType.Body, color = TvColors.Ink, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                            Spacer(Modifier.width(TvSpacing.lg))
                            TvButton(stringResource(R.string.retry), callbacks::retry,
                                Modifier.focusRequester(retry).testTag(TAG_PLAYER_RETRY), icon = AppIcon.Refresh, primary = true)
                        }
                    }
                    if (mode == TvOverlayMode.Settings) {
                        SettingsRows(player, position, options, settings, optionList,
                            onOpen = { kind ->
                                if (kind == TvSettingKind.Audio) callbacks.audioMenuOpened()
                                options = kind
                            },
                            onPick = { kind, option ->
                                apply(kind, option, player, callbacks)
                                options = null
                            })
                    }
                    TimeRow(player, position, seekTarget)
                    TvSeekBar(
                        position = { seekTarget.value ?: position.value },
                        buffered = { buffered.value },
                        durationMs = player.durationMs,
                        segments = player.sponsorSegments,
                        chapters = player.chapters,
                        focused = seekBarFocused,
                        modifier = Modifier
                            .padding(horizontal = TvSpacing.OverscanHorizontal)
                            .focusRequester(seekBar)
                            .onFocusChanged {
                                seekBarFocused = it.isFocused
                                // Focus walked back onto the bar from a row: plain controls again.
                                if (it.isFocused && mode != TvOverlayMode.Controls) show(TvOverlayMode.Controls)
                            }
                            .focusable()
                            .testTag(TAG_SEEK_BAR),
                    )
                    if (mode == TvOverlayMode.UpNext) {
                        UpNextRow(upNext, upNextRow, onOpen = { video ->
                            show(TvOverlayMode.Hidden)
                            callbacks.openVideo(video.id)
                        }, onNearEnd = callbacks::loadMoreRelated)
                    }
                }
            }
        }
        countdown?.let { seconds ->
            if (candidate != null) {
                AutoplayCard(candidate, seconds, Modifier.align(Alignment.BottomEnd)
                    .padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical)
                    .padding(bottom = if (mode == TvOverlayMode.Hidden) 0.dp else 120.dp))
            }
        }
    }
}

private fun apply(kind: TvSettingKind, option: TvOption, player: PlayerUiState, callbacks: TvPlayerCallbacks) {
    when (kind) {
        TvSettingKind.Quality -> callbacks.quality(option.key.takeUnless { it == AUTO })
        TvSettingKind.Speed -> option.key.toFloatOrNull()?.let(callbacks::speed)
        TvSettingKind.Captions -> if (option.key == OFF) callbacks.subtitlesOff() else callbacks.subtitle(option.key)
        TvSettingKind.Audio -> callbacks.audioTrack(option.key)
        TvSettingKind.Chapters -> option.key.toLongOrNull()?.let { callbacks.seekTo(it * 1000) }
    }
}

/** Settings chips available for the current media, in row order. */
internal fun settingKinds(player: PlayerUiState): List<TvSettingKind> = buildList {
    if (player.qualities.isNotEmpty()) add(TvSettingKind.Quality)
    add(TvSettingKind.Speed)
    add(TvSettingKind.Captions)
    if (player.audioTracks.size > 1) add(TvSettingKind.Audio)
    if (player.chapters.isNotEmpty()) add(TvSettingKind.Chapters)
}

@Composable
private fun optionsFor(kind: TvSettingKind, player: PlayerUiState, positionMs: Long): List<TvOption> = when (kind) {
    TvSettingKind.Quality -> listOf(TvOption(AUTO, stringResource(R.string.player_quality_auto), player.qualityLabel == null)) +
        player.qualities.map { TvOption(it, it, it == player.qualityLabel) }
    TvSettingKind.Speed -> PlayerUi.SpeedChoices.map { TvOption(it.toString(), PlayerUi.speedLabel(it), it == player.speed) }
    TvSettingKind.Captions -> {
        val auto = stringResource(R.string.subtitle_auto_generated)
        listOf(TvOption(OFF, stringResource(R.string.tv_player_off), !player.subtitleEnabled)) +
            player.subtitleTracks.map { track ->
                val label = PlayerUi.languageLabel(track.language).let { if (track.autoGenerated) "$it $auto" else it }
                TvOption(track.key, label, player.subtitleEnabled && player.subtitleKey == track.key)
            }
    }
    TvSettingKind.Audio -> player.audioTracks.map { TvOption(it.key, it.label, it.key == player.audioTrackKey) }
    TvSettingKind.Chapters -> {
        val current = player.chapters.lastOrNull { it.startSeconds * 1000L <= positionMs }
        player.chapters.map { chapter ->
            TvOption(chapter.startSeconds.toString(), "${PlayerUi.formatTime(chapter.startSeconds * 1000L)}  ${chapter.title}",
                chapter == current)
        }
    }
}

@Composable
private fun TvSettingKind.label(): String = stringResource(when (this) {
    TvSettingKind.Quality -> R.string.player_info_quality
    TvSettingKind.Speed -> R.string.info_playback_speed
    TvSettingKind.Captions -> R.string.subtitles
    TvSettingKind.Audio -> R.string.audio_track
    TvSettingKind.Chapters -> R.string.chapters
})

@Composable
private fun SettingsRows(
    player: PlayerUiState,
    position: State<Long>,
    open: TvSettingKind?,
    settings: FocusRequester,
    optionList: FocusRequester,
    onOpen: (TvSettingKind) -> Unit,
    onPick: (TvSettingKind, TvOption) -> Unit,
) {
    val kinds = settingKinds(player)
    if (open != null) {
        val choices = optionsFor(open, player, position.value)
        val selected = choices.indexOfFirst { it.selected }.coerceAtLeast(0)
        val list = rememberLazyListState(selected)
        LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().focusRestorer(optionList).testTag(TAG_OPTIONS),
            contentPadding = PaddingValues(horizontal = TvSpacing.OverscanHorizontal, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
        ) {
            itemsIndexed(choices, key = { _, option -> option.key }) { index, option ->
                TvChip(option.label, option.selected, { onPick(open, option) },
                    (if (index == selected) Modifier.focusRequester(optionList) else Modifier).testTag("$TAG_OPTION-$index"))
            }
        }
    }
    LazyRow(
        modifier = Modifier.fillMaxWidth().focusRestorer(settings).testTag(TAG_SETTINGS),
        contentPadding = PaddingValues(horizontal = TvSpacing.OverscanHorizontal, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        itemsIndexed(kinds, key = { _, kind -> kind.name }) { index, kind ->
            TvChip(kind.label(), selected = kind == open, onClick = { onOpen(kind) },
                modifier = (if (index == 0) Modifier.focusRequester(settings) else Modifier).testTag("$TAG_SETTING-${kind.name}"),
                detail = settingValue(kind, player, position.value))
        }
    }
}

@Composable
private fun settingValue(kind: TvSettingKind, player: PlayerUiState, positionMs: Long): String? = when (kind) {
    TvSettingKind.Quality -> player.qualityLabel ?: stringResource(R.string.player_quality_auto)
    TvSettingKind.Speed -> PlayerUi.speedLabel(player.speed)
    TvSettingKind.Captions -> if (player.subtitleEnabled) player.subtitleKey?.let { key ->
        player.subtitleTracks.firstOrNull { it.key == key }?.let { PlayerUi.languageLabel(it.language) } } else stringResource(R.string.tv_player_off)
    TvSettingKind.Audio -> player.audioTracks.firstOrNull { it.key == player.audioTrackKey }?.label
    TvSettingKind.Chapters -> player.chapters.lastOrNull { it.startSeconds * 1000L <= positionMs }?.title
}

@Composable
private fun TitleBlock(info: TvWatchInfo, player: PlayerUiState, modifier: Modifier) {
    Column(modifier.fillMaxWidth(0.72f)) {
        Text(info.title.ifBlank { player.title }, style = TvType.Title, color = TvColors.Ink, maxLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag(TAG_TITLE))
        val detail = listOf(info.channel.ifBlank { player.author.orEmpty() }, info.detail).filter(String::isNotBlank).joinToString(" · ")
        if (detail.isNotBlank()) Text(detail, style = TvType.Body, color = TvColors.Secondary, maxLines = 1)
    }
}

@Composable
private fun TimeRow(player: PlayerUiState, position: State<Long>, seekTarget: State<Long?>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = TvSpacing.OverscanHorizontal), verticalAlignment = Alignment.CenterVertically) {
        if (player.isLive) {
            Text(stringResource(R.string.tv_player_live), style = TvType.Label, color = Color.White,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(TvColors.Live).padding(horizontal = 6.dp, vertical = 2.dp))
            Spacer(Modifier.width(TvSpacing.md))
        }
        val shown = seekTarget.value ?: position.value
        Text(PlayerUi.formatTime(shown), style = TvType.BodyStrong, color = TvColors.Ink, modifier = Modifier.testTag(TAG_TIME))
        if (player.durationMs > 0 && !player.isLive) {
            Text(" / ${PlayerUi.formatTime(player.durationMs)}", style = TvType.Body, color = TvColors.Secondary)
        }
        Spacer(Modifier.weight(1f))
        if (player.speed != 1f) Text(PlayerUi.speedLabel(player.speed), style = TvType.Caption, color = TvColors.Secondary)
    }
}

@Composable
private fun UpNextRow(videos: List<TvVideo>, requester: FocusRequester, onOpen: (TvVideo) -> Unit, onNearEnd: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(TAG_UP_NEXT)) {
        TvRowHeader(stringResource(R.string.tv_player_up_next), Modifier.padding(start = TvSpacing.OverscanHorizontal))
        if (videos.isEmpty()) {
            Text(stringResource(R.string.tv_player_no_related), style = TvType.Body, color = TvColors.Secondary,
                modifier = Modifier.padding(start = TvSpacing.OverscanHorizontal))
            return
        }
        val list = rememberLazyListState()
        val nearEnd by remember(list) { derivedStateOf {
            val info = list.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 3
        } }
        LaunchedEffect(nearEnd, videos.size) { if (nearEnd) onNearEnd() }
        LazyRow(
            state = list,
            modifier = Modifier.fillMaxWidth().focusRestorer(requester),
            contentPadding = PaddingValues(horizontal = TvSpacing.OverscanHorizontal, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.CardGap),
        ) {
            itemsIndexed(videos, key = { _, video -> video.id }) { index, video ->
                TvCard(video, onClick = { onOpen(video) }, width = TvSizes.CardWidth * 0.85f,
                    modifier = (if (index == 0) Modifier.focusRequester(requester) else Modifier).testTag("$TAG_UP_NEXT_CARD-$index"))
            }
        }
    }
}

@Composable
private fun AutoplayCard(next: TvVideo, seconds: Int, modifier: Modifier) {
    Row(
        modifier.width(460.dp).clip(RoundedCornerShape(TvSizes.Corner)).background(TvColors.Panel.copy(alpha = 0.94f))
            .padding(TvSpacing.md).testTag(TAG_AUTOPLAY),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        YoutubeThumb(next.artwork, Modifier.width(160.dp).height(90.dp).clip(RoundedCornerShape(TvSizes.SmallCorner)),
            placeholderColor = TvColors.Surface)
        Spacer(Modifier.width(TvSpacing.md))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.tv_player_up_next_in, seconds), style = TvType.Label, color = TvColors.Accent)
            Text(next.title, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(TvSpacing.xs))
            Text("${stringResource(R.string.tv_player_play_now)}    ${stringResource(R.string.tv_player_cancel)}",
                style = TvType.Caption, color = TvColors.Secondary, maxLines = 1)
        }
    }
}

@Composable
private fun TvSpinner(modifier: Modifier) {
    androidx.compose.material3.CircularProgressIndicator(modifier.size(56.dp), color = TvColors.Accent, strokeWidth = 4.dp)
}

private const val AUTO = "auto"
private const val OFF = "off"
private const val CONTROLS_AUTO_HIDE_MS = 5_000L
private const val SEEK_COMMIT_FALLBACK_MS = 900L

const val TAG_PLAYER = "tv-player"
const val TAG_SEEK_BAR = "tv-player-seekbar"
const val TAG_TIME = "tv-player-time"
const val TAG_TITLE = "tv-player-title"
const val TAG_SETTINGS = "tv-player-settings"
const val TAG_SETTING = "tv-player-setting"
const val TAG_OPTIONS = "tv-player-options"
const val TAG_OPTION = "tv-player-option"
const val TAG_UP_NEXT = "tv-player-upnext"
const val TAG_UP_NEXT_CARD = "tv-player-upnext-card"
const val TAG_AUTOPLAY = "tv-player-autoplay"
const val TAG_PLAYER_RETRY = "tv-player-retry"
