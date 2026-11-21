package com.hhst.youtubelite.tv

import android.view.KeyEvent
import com.hhst.youtubelite.tv.shorts.TvShortsAction
import com.hhst.youtubelite.tv.shorts.TvShortsKeyMapper
import com.hhst.youtubelite.tv.shorts.tvShortsNeighbour
import com.hhst.youtubelite.tv.shorts.tvShortsSeekTarget
import com.hhst.youtubelite.tv.watch.TvKeyInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TvShortsKeyMapperTest {
    private val mapper = TvShortsKeyMapper()
    private fun down(code: Int, repeat: Int = 0, video: Boolean = true) = mapper.map(TvKeyInput(code, down = true, repeatCount = repeat), video)
    private fun up(code: Int, video: Boolean = true) = mapper.map(TvKeyInput(code, down = false), video)

    @Test fun upAndDownChangeShortOncePerPress() {
        assertEquals(TvShortsAction.Next, down(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(TvShortsAction.Consume, down(KeyEvent.KEYCODE_DPAD_DOWN, repeat = 1))
        assertEquals(TvShortsAction.Consume, up(KeyEvent.KEYCODE_DPAD_DOWN))
        assertEquals(TvShortsAction.Previous, down(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(TvShortsAction.Consume, up(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun leftAndRightSeekFiveSecondsAndRepeat() {
        assertEquals(TvShortsAction.Seek(-5_000), down(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(TvShortsAction.Seek(5_000), down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(TvShortsAction.Seek(5_000), down(KeyEvent.KEYCODE_DPAD_RIGHT, repeat = 3))
    }

    @Test fun okTogglesOnReleaseAndHoldingMovesToTheButtonsWithoutPressingThem() {
        assertEquals(TvShortsAction.Consume, down(KeyEvent.KEYCODE_DPAD_CENTER))
        assertEquals(TvShortsAction.TogglePlayPause, up(KeyEvent.KEYCODE_DPAD_CENTER))

        assertEquals(TvShortsAction.Consume, down(KeyEvent.KEYCODE_ENTER))
        assertEquals(TvShortsAction.FocusActions, down(KeyEvent.KEYCODE_ENTER, repeat = 1))
        // Focus is on Like now: the rest of the hold and its release stay with the player.
        assertEquals(TvShortsAction.Consume, down(KeyEvent.KEYCODE_ENTER, repeat = 2, video = false))
        assertEquals(TvShortsAction.Consume, up(KeyEvent.KEYCODE_ENTER, video = false))
        // The next press on a button is the button's.
        assertEquals(TvShortsAction.PassThrough, down(KeyEvent.KEYCODE_ENTER, video = false))
        assertEquals(TvShortsAction.PassThrough, up(KeyEvent.KEYCODE_ENTER, video = false))
    }

    @Test fun dpadOnTheButtonsMovesFocus() {
        for (code in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertEquals(TvShortsAction.PassThrough, down(code, video = false))
            assertEquals(TvShortsAction.PassThrough, up(code, video = false))
        }
    }

    @Test fun mediaKeysWorkWhereverFocusIs() {
        assertEquals(TvShortsAction.TogglePlayPause, down(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, video = false))
        assertEquals(TvShortsAction.Play, down(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(TvShortsAction.Pause, down(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(TvShortsAction.Next, down(KeyEvent.KEYCODE_MEDIA_NEXT, video = false))
        assertEquals(TvShortsAction.Previous, down(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(TvShortsAction.Seek(5_000), down(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD))
        assertEquals(TvShortsAction.Seek(-5_000), down(KeyEvent.KEYCODE_MEDIA_REWIND, video = false))
    }

    @Test fun backIsNotThePlayers() {
        assertEquals(TvShortsAction.PassThrough, down(KeyEvent.KEYCODE_BACK))
        assertEquals(TvShortsAction.PassThrough, up(KeyEvent.KEYCODE_BACK))
    }

    @Test fun neighbourStopsAtTheEnds() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", tvShortsNeighbour(ids, "a", forward = true))
        assertEquals("a", tvShortsNeighbour(ids, "b", forward = false))
        assertNull(tvShortsNeighbour(ids, "a", forward = false))
        assertNull(tvShortsNeighbour(ids, "c", forward = true))
        assertEquals("a", tvShortsNeighbour(ids, "gone", forward = true))
        assertNull(tvShortsNeighbour(ids, null, forward = false))
    }

    @Test fun seekTargetStaysInsideTheShort() {
        assertEquals(0L, tvShortsSeekTarget(2_000, -5_000, 30_000))
        assertEquals(15_000L, tvShortsSeekTarget(10_000, 5_000, 30_000))
        assertEquals(29_500L, tvShortsSeekTarget(28_000, 5_000, 30_000))
        assertEquals(12_000L, tvShortsSeekTarget(7_000, 5_000, 0))
    }
}
