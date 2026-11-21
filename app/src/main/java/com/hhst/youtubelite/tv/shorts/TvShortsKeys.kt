package com.hhst.youtubelite.tv.shorts

import android.view.KeyEvent
import com.hhst.youtubelite.tv.watch.TvKeyInput

sealed interface TvShortsAction {
    /** Not a player key here: focus navigation and clicks handle it. */
    data object PassThrough : TvShortsAction
    /** Swallow the key (the other half of a handled key, or a held Up/Down). */
    data object Consume : TvShortsAction
    data object TogglePlayPause : TvShortsAction
    data object Play : TvShortsAction
    data object Pause : TvShortsAction
    data class Seek(val deltaMs: Long) : TvShortsAction
    data object Next : TvShortsAction
    data object Previous : TvShortsAction
    /** Hold OK: move focus to the Like / Dislike buttons. */
    data object FocusActions : TvShortsAction
}

/**
 * Remote → Shorts player. While the video holds focus: Up/Down change Short (one per press, a
 * held key does not skip a run of Shorts), OK plays or pauses on release, holding OK moves to the
 * like buttons, Left/Right seek ∓/±5 s. Media keys work wherever focus is. Back is the activity's.
 *
 * The only state pairs a key-up with its handled key-down, so the release of a key the player
 * consumed never clicks a button that gained focus meanwhile.
 */
class TvShortsKeyMapper {
    private val handledDowns = mutableSetOf<Int>()
    private var okHeld = false

    fun map(input: TvKeyInput, videoFocused: Boolean): TvShortsAction {
        if (!input.down) {
            if (!handledDowns.remove(input.keyCode)) return TvShortsAction.PassThrough
            if (input.keyCode in OK_KEYS) {
                val held = okHeld
                okHeld = false
                return if (held) TvShortsAction.Consume else TvShortsAction.TogglePlayPause
            }
            return TvShortsAction.Consume
        }
        val action = mapDown(input, videoFocused)
        if (action != TvShortsAction.PassThrough) handledDowns += input.keyCode
        return action
    }

    private fun mapDown(input: TvKeyInput, videoFocused: Boolean): TvShortsAction {
        when (input.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> return once(input, TvShortsAction.TogglePlayPause)
            KeyEvent.KEYCODE_MEDIA_PLAY -> return once(input, TvShortsAction.Play)
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> return once(input, TvShortsAction.Pause)
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_CHANNEL_DOWN -> return once(input, TvShortsAction.Next)
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_CHANNEL_UP -> return once(input, TvShortsAction.Previous)
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> return TvShortsAction.Seek(SEEK_STEP_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> return TvShortsAction.Seek(-SEEK_STEP_MS)
        }
        // The rest of a hold that moved focus to the buttons must not press them.
        if (okHeld && input.keyCode in OK_KEYS) return TvShortsAction.Consume
        if (!videoFocused) return TvShortsAction.PassThrough
        return when (input.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> once(input, TvShortsAction.Previous)
            KeyEvent.KEYCODE_DPAD_DOWN -> once(input, TvShortsAction.Next)
            KeyEvent.KEYCODE_DPAD_LEFT -> TvShortsAction.Seek(-SEEK_STEP_MS)
            KeyEvent.KEYCODE_DPAD_RIGHT -> TvShortsAction.Seek(SEEK_STEP_MS)
            in OK_KEYS -> when {
                input.repeatCount == 0 -> TvShortsAction.Consume
                okHeld -> TvShortsAction.Consume
                else -> {
                    okHeld = true
                    TvShortsAction.FocusActions
                }
            }
            else -> TvShortsAction.PassThrough
        }
    }

    private fun once(input: TvKeyInput, action: TvShortsAction) = if (input.repeatCount > 0) TvShortsAction.Consume else action

    companion object {
        const val SEEK_STEP_MS = 5_000L
        private val OK_KEYS = setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
    }
}

/** Neighbour of [currentId] in [ids]; null at either end or when [currentId] is unknown. */
fun tvShortsNeighbour(ids: List<String>, currentId: String?, forward: Boolean): String? {
    val index = ids.indexOf(currentId)
    if (index < 0) return if (forward) ids.firstOrNull() else null
    return ids.getOrNull(if (forward) index + 1 else index - 1)
}

/** Seek target clamped to the media; [durationMs] <= 0 means unknown (only the start is clamped). */
fun tvShortsSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = (positionMs + deltaMs).coerceAtLeast(0L)
    return if (durationMs > 0) target.coerceAtMost((durationMs - 500).coerceAtLeast(0L)) else target
}
