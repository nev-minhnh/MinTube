package com.hhst.youtubelite.tv.watch

import com.hhst.youtubelite.core.time.publishedAgeText
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.player.sponsor.SponsorBlockManager
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSidePanel
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.watch.domain.VideoRating
import kotlinx.coroutines.launch

/** The page facts behind the Info panel and the like/subscribe buttons. Null fields: not loaded yet. */
data class TvWatchDetails(
    val title: String = "",
    val channel: String = "",
    val date: String = "",
    val views: String = "",
    val description: String = "",
    val likes: String = "",
    val rating: VideoRating = VideoRating.None,
    val subscribed: Boolean = false,
    val canSubscribe: Boolean = false,
)

/** Everything the controls row needs beyond the player state. Defaults: nothing loaded, signed out. */
data class TvWatchExtras(
    val details: TvWatchDetails? = null,
    val signedIn: Boolean = false,
    /** An action is in flight (the view model ignores a second one). */
    val pending: Boolean = false,
    /** The page read hit a traffic check: a Verify button appears in the row. */
    val verification: Boolean = false,
    /** Show the "sign in to do this" panel. */
    val signInPrompt: Boolean = false,
    /** A short confirmation or failure line ("Saved to Watch later"). */
    val notice: String? = null,
)

/** Account actions of the TV watch screen; the activity maps them to [com.hhst.youtubelite.watch.presentation.WatchViewModel]. */
interface TvWatchActions {
    fun like() = Unit
    fun dislike() = Unit
    fun subscribe() = Unit
    fun watchLater() = Unit
    fun verify() = Unit
    fun signIn() = Unit
    fun dismissSignInPrompt() = Unit
    fun dismissNotice() = Unit

    companion object {
        val None: TvWatchActions = object : TvWatchActions {}
    }
}

/** Buttons appended to the settings row, in order. */
enum class TvWatchButton { Info, Like, Dislike, Subscribe, WatchLater, Verify }

/** Account commands need a signed-in session; signed out, they open the sign-in prompt instead. */
object TvWatchButtons {
    fun visible(extras: TvWatchExtras): List<TvWatchButton> = buildList {
        add(TvWatchButton.Info)
        add(TvWatchButton.Like)
        add(TvWatchButton.Dislike)
        if (extras.details?.canSubscribe != false) add(TvWatchButton.Subscribe)
        add(TvWatchButton.WatchLater)
        if (extras.verification) add(TvWatchButton.Verify)
    }

    fun needsSignIn(button: TvWatchButton): Boolean = when (button) {
        TvWatchButton.Like, TvWatchButton.Dislike, TvWatchButton.Subscribe, TvWatchButton.WatchLater -> true
        TvWatchButton.Info, TvWatchButton.Verify -> false
    }
}

/**
 * Detects a SponsorBlock auto-skip from the shared player state alone: the position jumps
 * forward from inside (or just before) a segment to its end, without a seek the user asked for.
 * The engine's skip controller is untouched; this only reads positions and segments.
 */
class TvSponsorSkipDetector {
    private var last = -1L
    private var suppressUntil = 0L

    /** A seek the user made (D-pad, chapter, Up next) is never reported as a skip. */
    fun onUserSeek(nowMs: Long) {
        suppressUntil = nowMs + USER_SEEK_GRACE_MS
    }

    fun reset() {
        last = -1L
        suppressUntil = 0L
    }

    /** Feed every position sample; returns the skipped segment when this sample landed a skip. */
    fun onPosition(positionMs: Long, segments: List<SponsorBlockManager.Segment>, nowMs: Long): SponsorBlockManager.Segment? {
        val previous = last
        last = positionMs
        if (previous < 0 || nowMs < suppressUntil) return null
        if (positionMs - previous < MIN_JUMP_MS) return null
        return segments.firstOrNull { segment ->
            segment.endMs > segment.startMs &&
                previous >= segment.startMs - ENTRY_TOLERANCE_MS && previous < segment.endMs &&
                positionMs >= segment.endMs - LANDING_TOLERANCE_MS && positionMs <= segment.endMs + LANDING_TOLERANCE_MS
        }
    }

    companion object {
        /** Larger than two position samples of normal playback at 2x. */
        const val MIN_JUMP_MS = 1_500L
        const val ENTRY_TOLERANCE_MS = 1_000L
        const val LANDING_TOLERANCE_MS = 1_500L
        const val USER_SEEK_GRACE_MS = 2_000L
        const val TOAST_MS = 3_000L
    }
}

/** Toast text for a skipped segment category (SponsorBlock API names). */
@Composable
internal fun sponsorSkippedLabel(category: String): String = stringResource(when (category) {
    "sponsor" -> R.string.tv_sponsor_skipped_sponsor
    "selfpromo" -> R.string.tv_sponsor_skipped_selfpromo
    "intro" -> R.string.tv_sponsor_skipped_intro
    "outro" -> R.string.tv_sponsor_skipped_outro
    else -> R.string.tv_sponsor_skipped_other
})

/** A small non-focusable pill in the top-right corner (skip toast, action notices). */
@Composable
internal fun TvToast(text: String, modifier: Modifier = Modifier) {
    Text(text, style = TvType.BodyStrong, color = TvColors.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.clip(RoundedCornerShape(24.dp)).background(TvColors.Panel.copy(alpha = 0.94f))
            .padding(horizontal = TvSpacing.xl, vertical = TvSpacing.md))
}

/**
 * Description panel: title, channel, date, views and the full description. The text block holds
 * focus; Up/Down scroll it. Back closes the panel.
 */
@Composable
internal fun TvInfoPanel(details: TvWatchDetails, fallbackTitle: String, onDismiss: () -> Unit) {
    TvSidePanel(details.title.ifBlank { fallbackTitle }, onDismiss, Modifier.testTag(TAG_INFO_PANEL), width = 640.dp) {
        val meta = listOf(details.channel, details.views, publishedAgeText(details.date.takeIf(String::isNotBlank)), details.likes.takeIf(String::isNotBlank)?.let {
            stringResource(R.string.tv_watch_likes, it)
        }.orEmpty()).filter(String::isNotBlank).joinToString(" · ")
        if (meta.isNotBlank()) Text(meta, style = TvType.Body, color = TvColors.Secondary)
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val focus = remember { FocusRequester() }
        Column(
            Modifier.fillMaxWidth().weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .focusRequester(focus)
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { scope.launch { scroll.animateScrollBy(SCROLL_STEP_PX) }; true }
                        Key.DirectionUp -> { scope.launch { scroll.animateScrollBy(-SCROLL_STEP_PX) }; true }
                        // Nothing else in the panel: keep focus on the text.
                        Key.DirectionLeft, Key.DirectionRight -> true
                        else -> false
                    }
                }
                .focusable()
                .verticalScroll(scroll)
                .testTag(TAG_INFO_TEXT),
        ) {
            Text(details.description.ifBlank { stringResource(R.string.tv_watch_no_description) }, style = TvType.Body,
                color = TvColors.Ink)
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus(FocusDirection.Enter) }
        }
    }
}

/** "Sign in to like, subscribe and save" with a focused Sign in button. */
@Composable
internal fun TvSignInPromptPanel(onSignIn: () -> Unit, onDismiss: () -> Unit) {
    TvSidePanel(stringResource(R.string.ui_sign_in), onDismiss, Modifier.testTag(TAG_SIGN_IN_PROMPT)) {
        Text(stringResource(R.string.tv_watch_sign_in_message), style = TvType.Body, color = TvColors.Secondary)
        val focus = remember { FocusRequester() }
        Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            TvButton(stringResource(R.string.ui_sign_in), onSignIn, Modifier.focusRequester(focus).testTag("$TAG_SIGN_IN_PROMPT-sign-in"),
                primary = true)
            TvButton(stringResource(R.string.tv_verify_not_now), onDismiss, Modifier.testTag("$TAG_SIGN_IN_PROMPT-dismiss"))
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { focus.requestFocus(FocusDirection.Enter) }
        }
    }
}

private const val SCROLL_STEP_PX = 240f

const val TAG_ACTION = "tv-player-action"
const val TAG_INFO_PANEL = "tv-player-info"
const val TAG_INFO_TEXT = "tv-player-info-text"
const val TAG_SIGN_IN_PROMPT = "tv-player-sign-in"
const val TAG_TOAST = "tv-player-toast"
