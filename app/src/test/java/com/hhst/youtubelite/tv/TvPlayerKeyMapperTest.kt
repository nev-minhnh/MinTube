package com.hhst.youtubelite.tv

import android.view.KeyEvent
import com.hhst.youtubelite.tv.watch.TvKeyInput
import com.hhst.youtubelite.tv.watch.TvOverlayMode
import com.hhst.youtubelite.tv.watch.TvPlayerAction
import com.hhst.youtubelite.tv.watch.TvPlayerKeyContext
import com.hhst.youtubelite.tv.watch.TvPlayerKeyMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class TvPlayerKeyMapperTest {
    private val hidden = TvPlayerKeyContext(TvOverlayMode.Hidden)
    private val onSeekBar = TvPlayerKeyContext(TvOverlayMode.Controls, seekBarFocused = true)
    private val inSettings = TvPlayerKeyContext(TvOverlayMode.Settings)

    private fun down(code: Int, repeat: Int = 0) = TvKeyInput(code, down = true, repeatCount = repeat)
    private fun up(code: Int) = TvKeyInput(code, down = false)
    private fun map(code: Int, context: TvPlayerKeyContext, repeat: Int = 0) = TvPlayerKeyMapper.mapDown(down(code, repeat), context)

    @Test fun okOnTheVideoTogglesPlaybackAndShowsControls() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            assertEquals(TvPlayerAction.TogglePlayPause(showControls = true), map(code, hidden))
        }
        assertEquals(TvPlayerAction.TogglePlayPause(showControls = true), map(KeyEvent.KEYCODE_DPAD_CENTER, onSeekBar))
    }

    @Test fun okOnAFocusedButtonClicksIt() {
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_CENTER, inSettings))
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_CENTER, TvPlayerKeyContext(TvOverlayMode.UpNext)))
        assertEquals(TvPlayerAction.PassThrough,
            map(KeyEvent.KEYCODE_DPAD_CENTER, TvPlayerKeyContext(TvOverlayMode.Controls, seekBarFocused = false)))
    }

    @Test fun leftAndRightSeekTenSecondsFromTheVideoOrTheSeekBar() {
        assertEquals(TvPlayerAction.Seek(-10_000), map(KeyEvent.KEYCODE_DPAD_LEFT, hidden))
        assertEquals(TvPlayerAction.Seek(10_000), map(KeyEvent.KEYCODE_DPAD_RIGHT, hidden))
        assertEquals(TvPlayerAction.Seek(10_000), map(KeyEvent.KEYCODE_DPAD_RIGHT, onSeekBar))
        // In a row, Left/Right move focus between chips and cards.
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_RIGHT, inSettings))
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_LEFT, TvPlayerKeyContext(TvOverlayMode.UpNext)))
    }

    @Test fun holdingASeekKeyAccelerates() {
        assertEquals(10_000L, TvPlayerKeyMapper.seekStepMs(0))
        assertEquals(0L, TvPlayerKeyMapper.seekStepMs(1))
        assertEquals(0L, TvPlayerKeyMapper.seekStepMs(3))
        assertEquals(10_000L, TvPlayerKeyMapper.seekStepMs(4))
        assertEquals(30_000L, TvPlayerKeyMapper.seekStepMs(20))
        assertEquals(60_000L, TvPlayerKeyMapper.seekStepMs(60))
        assertEquals(60_000L, TvPlayerKeyMapper.seekStepMs(400))
        // Throttled repeats are swallowed, not passed to focus navigation.
        assertEquals(TvPlayerAction.Consume, map(KeyEvent.KEYCODE_DPAD_RIGHT, onSeekBar, repeat = 2))
        assertEquals(TvPlayerAction.Seek(-30_000), map(KeyEvent.KEYCODE_DPAD_LEFT, onSeekBar, repeat = 24))
        // Accelerated seek over a 2 s hold (~40 repeats) covers far more than a tap.
        val held = (0..40).sumOf { TvPlayerKeyMapper.seekStepMs(it) }
        assertEquals(10_000L + 4 * 10_000L + 6 * 30_000L, held)
    }

    @Test fun upOpensSettingsAndDownOpensUpNext() {
        assertEquals(TvPlayerAction.Show(TvOverlayMode.Settings), map(KeyEvent.KEYCODE_DPAD_UP, hidden))
        assertEquals(TvPlayerAction.Show(TvOverlayMode.Settings), map(KeyEvent.KEYCODE_DPAD_UP, onSeekBar))
        assertEquals(TvPlayerAction.Show(TvOverlayMode.UpNext), map(KeyEvent.KEYCODE_DPAD_DOWN, hidden))
        assertEquals(TvPlayerAction.Show(TvOverlayMode.UpNext), map(KeyEvent.KEYCODE_DPAD_DOWN, onSeekBar))
        // Inside a row, Up/Down walk focus (back onto the seek bar).
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_DOWN, inSettings))
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_DPAD_UP, TvPlayerKeyContext(TvOverlayMode.UpNext)))
    }

    @Test fun backClosesLayersBeforeLeaving() {
        assertEquals(TvPlayerAction.CloseOptions, map(KeyEvent.KEYCODE_BACK, inSettings.copy(optionsOpen = true)))
        assertEquals(TvPlayerAction.Hide, map(KeyEvent.KEYCODE_BACK, inSettings))
        assertEquals(TvPlayerAction.Hide, map(KeyEvent.KEYCODE_BACK, onSeekBar))
        assertEquals(TvPlayerAction.Hide, map(KeyEvent.KEYCODE_BACK, TvPlayerKeyContext(TvOverlayMode.UpNext, countdown = true)))
        assertEquals(TvPlayerAction.CancelAutoplay, map(KeyEvent.KEYCODE_BACK, hidden.copy(countdown = true)))
        assertEquals(TvPlayerAction.Exit, map(KeyEvent.KEYCODE_BACK, hidden))
    }

    @Test fun backHidesEveryVisibleOverlayAndLeavesOnlyFromThePlainVideo() {
        // Controls with focus anywhere: on the bar (OK, pause, a seek), on the root right after
        // they opened, on a row, with the countdown card under them.
        val visible = listOf(
            onSeekBar,
            TvPlayerKeyContext(TvOverlayMode.Controls, seekBarFocused = false),
            onSeekBar.copy(countdown = true),
            inSettings,
            TvPlayerKeyContext(TvOverlayMode.UpNext),
            // The bar still fading out, or a seek preview in flight, while the mode is already Hidden.
            hidden.copy(barVisible = true),
            hidden.copy(barVisible = true, countdown = true),
        )
        for (context in visible) {
            assertEquals("$context", true, context.overlayVisible)
            assertEquals("$context", TvPlayerAction.Hide, map(KeyEvent.KEYCODE_BACK, context))
        }
        assertEquals(false, hidden.overlayVisible)
        assertEquals(TvPlayerAction.Exit, map(KeyEvent.KEYCODE_BACK, hidden))
    }

    @Test fun backNeverTouchesPlayback() {
        val contexts = listOf(hidden, hidden.copy(barVisible = true), onSeekBar, inSettings, inSettings.copy(optionsOpen = true),
            TvPlayerKeyContext(TvOverlayMode.UpNext), hidden.copy(countdown = true))
        for (context in contexts) {
            val action = map(KeyEvent.KEYCODE_BACK, context)
            assertEquals("$context", false, action is TvPlayerAction.TogglePlayPause || action == TvPlayerAction.Play ||
                action == TvPlayerAction.Pause)
        }
    }

    @Test fun okOnTheAutoplayCardPlaysTheNextVideo() {
        assertEquals(TvPlayerAction.PlayUpNext, map(KeyEvent.KEYCODE_DPAD_CENTER, hidden.copy(countdown = true)))
        assertEquals(TvPlayerAction.TogglePlayPause(showControls = true),
            map(KeyEvent.KEYCODE_DPAD_CENTER, onSeekBar.copy(countdown = true)))
    }

    @Test fun mediaKeysWorkInEveryMode() {
        for (context in listOf(hidden, onSeekBar, inSettings, TvPlayerKeyContext(TvOverlayMode.UpNext))) {
            assertEquals(TvPlayerAction.Play, map(KeyEvent.KEYCODE_MEDIA_PLAY, context))
            assertEquals(TvPlayerAction.Pause, map(KeyEvent.KEYCODE_MEDIA_PAUSE, context))
            assertEquals(TvPlayerAction.Pause, map(KeyEvent.KEYCODE_MEDIA_STOP, context))
            assertEquals(TvPlayerAction.Seek(10_000), map(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, context))
            assertEquals(TvPlayerAction.Seek(-10_000), map(KeyEvent.KEYCODE_MEDIA_REWIND, context))
            assertEquals(TvPlayerAction.Next, map(KeyEvent.KEYCODE_MEDIA_NEXT, context))
            assertEquals(TvPlayerAction.Previous, map(KeyEvent.KEYCODE_MEDIA_PREVIOUS, context))
        }
        assertEquals(TvPlayerAction.TogglePlayPause(showControls = true), map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, hidden))
        assertEquals(TvPlayerAction.TogglePlayPause(showControls = false), map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, inSettings))
        assertEquals(TvPlayerAction.Consume, map(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, hidden, repeat = 1))
    }

    @Test fun unrelatedKeysPassThrough() {
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_VOLUME_UP, hidden))
        assertEquals(TvPlayerAction.PassThrough, map(KeyEvent.KEYCODE_SEARCH, onSeekBar))
    }

    @Test fun releasingAHandledSeekKeyCommitsAndOtherUpsAreSwallowed() {
        val mapper = TvPlayerKeyMapper()
        assertEquals(TvPlayerAction.Seek(10_000), mapper.map(down(KeyEvent.KEYCODE_DPAD_RIGHT), hidden))
        assertEquals(TvPlayerAction.Consume, mapper.map(down(KeyEvent.KEYCODE_DPAD_RIGHT, 1), onSeekBar))
        assertEquals(TvPlayerAction.CommitSeek, mapper.map(up(KeyEvent.KEYCODE_DPAD_RIGHT), onSeekBar))
        // A second release is not paired with a handled press any more.
        assertEquals(TvPlayerAction.PassThrough, mapper.map(up(KeyEvent.KEYCODE_DPAD_RIGHT), onSeekBar))

        assertEquals(TvPlayerAction.TogglePlayPause(true), mapper.map(down(KeyEvent.KEYCODE_DPAD_CENTER), hidden))
        // The overlay opened in between: the release must not click the newly focused control.
        assertEquals(TvPlayerAction.Consume, mapper.map(up(KeyEvent.KEYCODE_DPAD_CENTER), onSeekBar))

        assertEquals(TvPlayerAction.PassThrough, mapper.map(down(KeyEvent.KEYCODE_DPAD_CENTER), inSettings))
        assertEquals(TvPlayerAction.PassThrough, mapper.map(up(KeyEvent.KEYCODE_DPAD_CENTER), inSettings))
    }
}
