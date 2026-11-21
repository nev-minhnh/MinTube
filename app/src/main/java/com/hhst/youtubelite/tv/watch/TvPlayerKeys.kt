package com.hhst.youtubelite.tv.watch

import android.view.KeyEvent
import com.hhst.youtubelite.tv.design.TvVideo

/** What the player overlay shows. Hidden = video only. */
enum class TvOverlayMode { Hidden, Controls, Settings, UpNext }

/** The overlay facts the remote mapping depends on. */
data class TvPlayerKeyContext(
    val mode: TvOverlayMode,
    /** The seek bar holds focus (only possible in [TvOverlayMode.Controls]). */
    val seekBarFocused: Boolean = false,
    /** A settings option list (quality, speed, ...) is open. */
    val optionsOpen: Boolean = false,
    /** The autoplay countdown card is showing. */
    val countdown: Boolean = false,
    /**
     * Overlay chrome is still on screen although [mode] is [TvOverlayMode.Hidden]: the controls
     * are fading out, or a seek preview is in flight.
     */
    val barVisible: Boolean = false,
) {
    /** Anything of the overlay (title, time, seek bar, rows) is on screen over the video. */
    val overlayVisible: Boolean get() = mode != TvOverlayMode.Hidden || barVisible
}

/** A remote key as the mapper sees it (decoupled from android.view.KeyEvent for unit tests). */
data class TvKeyInput(val keyCode: Int, val down: Boolean, val repeatCount: Int = 0) {
    companion object {
        fun of(event: KeyEvent) = TvKeyInput(event.keyCode, event.action == KeyEvent.ACTION_DOWN, event.repeatCount)
    }
}

sealed interface TvPlayerAction {
    /** Not a player command: let focus navigation and clicks handle the key. */
    data object PassThrough : TvPlayerAction
    /** Swallow the key (the up half of a handled key, or a throttled repeat). */
    data object Consume : TvPlayerAction
    data class TogglePlayPause(val showControls: Boolean) : TvPlayerAction
    data object Play : TvPlayerAction
    data object Pause : TvPlayerAction
    /** Relative seek preview; repeats accumulate until [CommitSeek]. */
    data class Seek(val deltaMs: Long) : TvPlayerAction
    data object CommitSeek : TvPlayerAction
    data class Show(val mode: TvOverlayMode) : TvPlayerAction
    data object CloseOptions : TvPlayerAction
    data object Hide : TvPlayerAction
    data object Exit : TvPlayerAction
    data object Next : TvPlayerAction
    data object Previous : TvPlayerAction
    data object PlayUpNext : TvPlayerAction
    data object CancelAutoplay : TvPlayerAction
}

/**
 * Remote → player mapping for the TV watch screen.
 *
 * - OK: play/pause and show the controls (video or seek bar focused); with the autoplay card
 *   showing over the video it plays the next video now.
 * - Left/Right: seek ∓/±10 s; holding accelerates ([seekStepMs]). Elsewhere they move focus.
 * - Up: settings row (quality, speed, captions, audio, chapters). Down: the "Up next" row.
 * - Back: close an option list, then hide whatever overlay is on screen (controls shown by OK,
 *   pause or a seek, the settings row, "Up next", a fading bar) without touching playback, then
 *   cancel the autoplay card; only with nothing over the video does it leave.
 * - Media keys: play/pause, play, pause, stop (pause), fast-forward/rewind (seek), next, previous.
 *
 * The only state kept here pairs key-up with a handled key-down, so the up half of a key the
 * player consumed never reaches a focused button, and releasing a seek key commits the seek.
 */
class TvPlayerKeyMapper {
    private val handledDowns = mutableSetOf<Int>()

    fun map(input: TvKeyInput, context: TvPlayerKeyContext): TvPlayerAction {
        if (!input.down) {
            if (!handledDowns.remove(input.keyCode)) return TvPlayerAction.PassThrough
            return if (input.keyCode in SEEK_KEYS) TvPlayerAction.CommitSeek else TvPlayerAction.Consume
        }
        val action = mapDown(input, context)
        if (action != TvPlayerAction.PassThrough) handledDowns += input.keyCode
        return action
    }

    companion object {
        const val SEEK_STEP_MS = 10_000L
        private val SEEK_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND,
        )

        /**
         * Seek step for one key event. A press seeks 10 s. Held keys repeat ~20 times a second, so
         * only every 4th repeat seeks: 10 s steps for the first second, then 30 s, then 60 s.
         * 0 = a throttled repeat.
         */
        fun seekStepMs(repeatCount: Int): Long = when {
            repeatCount <= 0 -> SEEK_STEP_MS
            repeatCount % 4 != 0 -> 0L
            repeatCount < 20 -> SEEK_STEP_MS
            repeatCount < 60 -> 30_000L
            else -> 60_000L
        }

        fun mapDown(input: TvKeyInput, context: TvPlayerKeyContext): TvPlayerAction {
            val onVideo = context.mode == TvOverlayMode.Hidden
            val onSeekBar = context.mode == TvOverlayMode.Controls && context.seekBarFocused
            fun seek(sign: Long): TvPlayerAction = seekStepMs(input.repeatCount).let { step ->
                if (step == 0L) TvPlayerAction.Consume else TvPlayerAction.Seek(sign * step)
            }
            return when (input.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK ->
                    if (input.repeatCount > 0) TvPlayerAction.Consume else TvPlayerAction.TogglePlayPause(showControls = onVideo)
                KeyEvent.KEYCODE_MEDIA_PLAY -> TvPlayerAction.Play
                KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> TvPlayerAction.Pause
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> seek(1)
                KeyEvent.KEYCODE_MEDIA_REWIND -> seek(-1)
                KeyEvent.KEYCODE_MEDIA_NEXT -> TvPlayerAction.Next
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> TvPlayerAction.Previous
                KeyEvent.KEYCODE_BACK -> when {
                    context.optionsOpen -> TvPlayerAction.CloseOptions
                    context.overlayVisible -> TvPlayerAction.Hide
                    context.countdown -> TvPlayerAction.CancelAutoplay
                    else -> TvPlayerAction.Exit
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> when {
                    input.repeatCount > 0 -> if (onVideo || onSeekBar) TvPlayerAction.Consume else TvPlayerAction.PassThrough
                    onVideo && context.countdown -> TvPlayerAction.PlayUpNext
                    onVideo || onSeekBar -> TvPlayerAction.TogglePlayPause(showControls = true)
                    else -> TvPlayerAction.PassThrough
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> if (onVideo || onSeekBar) seek(-1) else TvPlayerAction.PassThrough
                KeyEvent.KEYCODE_DPAD_RIGHT -> if (onVideo || onSeekBar) seek(1) else TvPlayerAction.PassThrough
                KeyEvent.KEYCODE_DPAD_UP -> if (onVideo || onSeekBar) TvPlayerAction.Show(TvOverlayMode.Settings) else TvPlayerAction.PassThrough
                KeyEvent.KEYCODE_DPAD_DOWN -> if (onVideo || onSeekBar) TvPlayerAction.Show(TvOverlayMode.UpNext) else TvPlayerAction.PassThrough
                else -> TvPlayerAction.PassThrough
            }
        }
    }
}

/** Autoplay-next countdown: the last [WINDOW_MS] of a video that the engine will auto-advance. */
object TvAutoplay {
    const val WINDOW_MS = 10_000L

    /** Seconds left on the countdown card, or null when the card should not show. */
    fun secondsLeft(
        positionMs: Long,
        durationMs: Long,
        live: Boolean,
        enabled: Boolean,
        hasCandidate: Boolean,
        cancelled: Boolean,
        ended: Boolean,
    ): Int? {
        if (!enabled || live || !hasCandidate || cancelled || ended || durationMs <= 0) return null
        val remaining = durationMs - positionMs
        if (remaining <= 0 || remaining > WINDOW_MS) return null
        return ((remaining + 999) / 1000).toInt()
    }

    /**
     * Whether the engine will auto-advance at the end. A playlist always advances to its next item
     * (the autoplay setting governs only related videos); a user queue hides the card.
     */
    fun enabled(autoplaySetting: Boolean, queueNext: Boolean, queueEnabled: Boolean, inPlaylist: Boolean): Boolean =
        queueNext && (inPlaylist || autoplaySetting && !queueEnabled)

    /**
     * The video the card announces: during playlist playback the playlist's next item (none after
     * its last item), never a related video; otherwise the first related video that is not this one.
     */
    fun candidate(upNext: List<TvVideo>, currentId: String?, inPlaylist: Boolean, playlistNext: TvVideo?): TvVideo? =
        if (inPlaylist) playlistNext else upNext.firstOrNull { it.id != currentId }
}
