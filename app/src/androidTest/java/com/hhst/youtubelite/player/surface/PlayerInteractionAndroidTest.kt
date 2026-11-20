package com.hhst.youtubelite.player.surface

import android.view.SurfaceView
import android.os.Build
import android.provider.Settings
import com.hhst.youtubelite.core.DeviceEvidence
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hhst.youtubelite.R
import com.hhst.youtubelite.player.PlayerUiState
import com.hhst.youtubelite.player.GestureUi
import com.hhst.youtubelite.player.QueueItem
import com.hhst.youtubelite.ui.theme.AppTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.extractor.Extractor
import com.hhst.youtubelite.player.datasource.MediaSourceResolver
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import java.util.concurrent.atomic.AtomicLong

class PlayerInteractionAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun actions(block: (String, Array<out Any?>?) -> Unit = { _, _ -> }) =
        proxyActions("TestPlayerActions", block)

    @Test fun seekArrowsFollowDirectionAndKeepAccumulatedSeconds() {
        compose.mainClock.autoAdvance = false
        val gesture = mutableStateOf<GestureUi?>(GestureUi.DoubleTapSeek(true, 10_000))
        compose.setContent { AppTheme {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).align(Alignment.Center)
                    .background(Color(0xFF242424)).testTag("gesture-frame")) {
                    GestureOverlays(gesture, Modifier.fillMaxSize())
                }
            }
        } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription(">>").assertIsDisplayed()
        compose.onNodeWithText("+10s").assertIsDisplayed()
        val animations = Settings.Global.getFloat(compose.activity.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        // Window pixel capture requires API 26; text and layout checks run on all versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val before = compose.onNodeWithContentDescription(">>").captureToImage().toPixelMap()
            compose.mainClock.advanceTimeBy(200)
            val after = compose.onNodeWithContentDescription(">>").captureToImage().toPixelMap()
            var changedPixels = 0
            for (y in 0 until minOf(before.height, after.height)) for (x in 0 until minOf(before.width, after.width)) {
                if (before[x, y] != after[x, y]) changedPixels++
            }
            if (animations) assertTrue("Animated arrows did not move or fade", changedPixels > 5)
            else assertEquals("Disabled animations must keep static arrows", 0, changedPixels)
        } else compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { gesture.value = GestureUi.DoubleTapSeek(true, 30_000) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription(">>").assertIsDisplayed()
        compose.onNodeWithText("+30s").assertIsDisplayed()
        DeviceEvidence.captureScene("youtube-forward")
        compose.runOnIdle { gesture.value = GestureUi.DoubleTapSeek(false, -10_000) }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithContentDescription("<<").assertIsDisplayed()
        compose.onNodeWithContentDescription(">>").assertDoesNotExist()
        compose.onNodeWithText("-10s").assertIsDisplayed()
        DeviceEvidence.captureScene("youtube-rewind")
        compose.runOnIdle { gesture.value = null }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("<<").assertDoesNotExist()
        compose.runOnIdle { gesture.value = GestureUi.SpeedHold }
        compose.mainClock.advanceTimeBy(100)
        val speed = compose.onNodeWithText(compose.activity.getString(R.string.speed_hold_2x))
        speed.assertIsDisplayed()
        val frame = compose.onNodeWithTag("gesture-frame").fetchSemanticsNode().boundsInRoot
        assertTrue(speed.fetchSemanticsNode().boundsInRoot.top < frame.top + frame.height * .25f)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val speedBefore = compose.onNodeWithContentDescription(">>").captureToImage().toPixelMap()
            compose.mainClock.advanceTimeBy(200)
            val speedAfter = compose.onNodeWithContentDescription(">>").captureToImage().toPixelMap()
            var speedChanges = 0
            for (y in 0 until speedBefore.height) for (x in 0 until speedBefore.width) {
                if (speedBefore[x,y] != speedAfter[x,y]) speedChanges++
            }
            if (animations) assertTrue(speedChanges > 5) else assertEquals(0, speedChanges)
        } else compose.mainClock.advanceTimeBy(200)
        DeviceEvidence.captureScene("youtube-speed-hold")
        compose.runOnIdle { gesture.value = null }
        compose.mainClock.advanceTimeByFrame()
        speed.assertDoesNotExist()
    }

    // Fixed in-app mini (owner 2026-11-21): one bottom-end dock, default size, nothing persisted.
    @Test fun landscapeMiniResizesAndRemembersBothDocks() {
        compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitForIdle()
        val kv = com.hhst.youtubelite.core.PreferenceStore.of(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), "followup-landscape-mini")
        kv.clearAll()
        val store = MiniPlayerStore(kv)
        store.writeLegacyPlacement(280, 0f, -50f)
        var handle: MiniPlayerHandle? = null
        var screenWidth = 0
        compose.setContent {
            AppTheme {
                screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                MiniPlayerWindow(screenWidth, 24, store, true, {}, onDismiss = {}, topInsetDp = 24) {
                    handle = LocalMiniPlayerHandle.current
                    Box(Modifier.fillMaxSize().testTag("mini-viewport"))
                }
            }
        }
        compose.waitForIdle()
        assertFalse(store.hasLegacyPlacement())
        val docked = compose.onNodeWithTag("mini-viewport").fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        assertEquals(MiniPlayerLayout.defaultWidthDp(screenWidth) * density, docked.width, 1.5f)
        val host = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(host.right - MiniPlayerLayout.outerMarginDp(screenWidth) * density, docked.right, 1.5f)
        compose.runOnIdle { handle!!.begin(); handle!!.pinchScale(1.2f); handle!!.end(true) }
        compose.waitForIdle()
        assertEquals(docked, compose.onNodeWithTag("mini-viewport").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { handle!!.begin(); handle!!.dragFromStart(-10_000f, -10_000f); handle!!.end(true) }
        compose.waitForIdle()
        assertEquals(docked, compose.onNodeWithTag("mini-viewport").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { handle!!.begin(); handle!!.dragFromStart(10_000f, 10_000f); handle!!.end(true) }
        compose.waitForIdle()
        compose.onNodeWithTag("mini-viewport").assertIsDisplayed()
        assertEquals(docked, compose.onNodeWithTag("mini-viewport").fetchSemanticsNode().boundsInRoot)
        assertFalse(store.hasLegacyPlacement())
        kv.clearAll()
        compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    @Test fun miniPositionSurvivesProcessRestart() {
        compose.activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val assertRestart = InstrumentationRegistry.getArguments().getString("miniRestartStage") == "assert"
        val kv = com.hhst.youtubelite.core.PreferenceStore.of(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), "followup-mini-process")
        // First stage: an upgrade from a version that saved a dragged placement.
        if (!assertRestart) { kv.clearAll(); MiniPlayerStore(kv).writeLegacyPlacement(250, -100f, -75f) }
        val store = MiniPlayerStore(kv)
        val record = java.io.File(instrumentation.targetContext.filesDir, "followup-mini-position.json")
        compose.setContent {
            AppTheme {
                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                MiniPlayerWindow(screenWidth, 24, store, true, {}, topInsetDp = 24) {
                    Box(Modifier.fillMaxSize().testTag("mini-viewport"))
                }
            }
        }
        compose.waitForIdle()
        assertFalse(store.hasLegacyPlacement())
        val bounds = compose.onNodeWithTag("mini-viewport").fetchSemanticsNode().boundsInRoot
        val position = listOf(bounds.left, bounds.top, bounds.width).joinToString(",") { it.toInt().toString() }
        // The fixed dock is the same after a process restart; the old offset never comes back.
        if (assertRestart) assertEquals(record.readText(), position) else record.writeText(position)
    }

    @Test fun miniRestoreKeepsSurfaceAndSettledPositionAndCloseAnimates() {
        val kv = com.hhst.youtubelite.core.PreferenceStore.of(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), "followup-mini-test")
        kv.clearAll()
        val store = MiniPlayerStore(kv)
        store.writeLegacyPlacement(220, -110f, -125f)
        var mini by mutableStateOf(false)
        var visible by mutableStateOf(true)
        var attached = 0
        var detached = 0
        var handle: MiniPlayerHandle? = null
        var firstSurface: SurfaceView? = null
        val callbacks = actions { method, _ ->
            when (method) { "onMiniRestore" -> mini = false; "onMiniClose" -> visible = false }
        }
        compose.setContent {
            AppTheme(darkTheme = true, dynamicColor = false) {
                Box(Modifier.fillMaxSize().testTag("mini-root")) {
                    if (visible) MiniPlayerWindow(411, 24, store, true, {}, onDismiss = { visible = false },
                        mini = mini, embeddedTopDp = 24, embeddedHeightDp = 231) {
                        handle = LocalMiniPlayerHandle.current
                        PlayerSurface(PlayerUiState(visible = true, mini = mini, title = "Mini test"),
                            remember { mutableStateOf(0L) }, remember { mutableStateOf(0L) },
                            remember { mutableStateOf(null) }, remember { mutableStateOf(null) },
                            remember { mutableStateOf(emptyList()) }, callbacks,
                            { attached++; if (firstSurface == null) firstSurface = it else assertNotSame(firstSurface, it) },
                            { detached++ }, managedByHost = true)
                    }
                }
            }
        }
        compose.waitForIdle()
        assertEquals(1, attached)
        compose.runOnIdle { mini = true }
        compose.waitForIdle()
        // The legacy placement is ignored: default width at the bottom-end corner.
        assertFalse(store.hasLegacyPlacement())
        val density = compose.density.density
        val root = compose.onNodeWithTag("mini-root").fetchSemanticsNode().boundsInRoot
        val docked = compose.onNodeWithTag("player-window").fetchSemanticsNode().boundsInRoot
        assertEquals(MiniPlayerLayout.defaultWidthDp(411) * density, docked.width, 1.5f)
        assertEquals(root.right - MiniPlayerLayout.outerMarginDp(411) * density, docked.right, 1.5f)
        compose.runOnIdle { handle!!.begin(); handle!!.dragFromStart(-120f, -50f); handle!!.end(true) }
        compose.waitForIdle()
        assertEquals(docked, compose.onNodeWithTag("player-window").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { mini = false }
        compose.waitForIdle()
        assertEquals(1, attached)
        assertEquals(0, detached)
        compose.runOnIdle { mini = true }
        compose.waitForIdle()
        assertEquals(docked, compose.onNodeWithTag("player-window").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { handle!!.dismiss() }
        compose.waitForIdle()
        assertFalse(visible)
        assertEquals(1, detached)
        compose.runOnIdle { visible = true }
        compose.waitForIdle()
        assertFalse(store.hasLegacyPlacement())
        assertEquals(2, attached)
        kv.clearAll()
    }

    @Test fun moreQueuesCurrentVideoAndClosesWithFeedback() {
        var sheet by mutableStateOf<PlayerSheet?>(PlayerSheet.More)
        var item: QueueItem? = null
        var hint: String? = null
        val callbacks = actions { method, args ->
            if (method == "onQueueAdd") item = args!![0] as QueueItem
            if (method == "onHint") hint = args!![0] as String
        }
        compose.setContent {
            AppTheme(darkTheme = true, dynamicColor = false) {
                PlayerSheetHost(sheet, PlayerUiState(videoId = "abcdefghijk", title = "Queue clip", author = "Channel"),
                    remember { mutableStateOf(0L) }, callbacks, { sheet = null }, {}, { sheet = it })
            }
        }
        compose.onNodeWithText(compose.activity.getString(R.string.add_to_queue)).performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals("abcdefghijk", item!!.videoId)
        assertEquals("Queue clip", item!!.title)
        assertEquals(compose.activity.getString(R.string.queue_item_added), hint)
        assertNull(sheet)
    }

    @androidx.media3.common.util.UnstableApi
    @Test fun decodedPlaybackContinuesThroughMiniAndRestoreWithoutSurfaceReplacement() {
        assumeTrue("Supply -e network=1 for live-network playback acceptance",
            InstrumentationRegistry.getArguments().getString("network") == "1")
        val extraction = GlobalContext.get().get<Extractor>().extract("jNQXAC9IVRw")
        val source = runBlocking {
            GlobalContext.get().get<MediaSourceResolver>().resolve(extraction.stream.await(), extraction.metadata.await())
        }
        lateinit var player: ExoPlayer
        compose.runOnUiThread {
            player = ExoPlayer.Builder(compose.activity).build().apply {
                volume = 0f
                setMediaSource(source.mediaSource)
                prepare()
                play()
            }
        }
        val store = MiniPlayerStore(com.hhst.youtubelite.core.PreferenceStore.of(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>(), "followup-live-mini"))
        var mini by mutableStateOf(false)
        var attached = 0
        var detached = 0
        val callbacks = actions()
        compose.setContent {
            AppTheme(darkTheme = true, dynamicColor = false) {
                MiniPlayerWindow(411, 24, store, false, {}, mini = mini, embeddedTopDp = 24, embeddedHeightDp = 231) {
                    PlayerSurface(PlayerUiState(visible = true, mini = mini),
                        remember { mutableStateOf(0L) }, remember { mutableStateOf(0L) },
                        remember { mutableStateOf(null) }, remember { mutableStateOf(null) },
                        remember { mutableStateOf(emptyList()) }, callbacks,
                        { attached++; player.setVideoSurfaceView(it) },
                        { detached++; player.clearVideoSurfaceView(it) }, managedByHost = true)
                }
            }
        }
        fun sample(): Pair<Long, Int> {
            val position = AtomicLong()
            var frames = 0
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player.videoDecoderCounters?.ensureUpdated()
                position.set(player.currentPosition)
                frames = player.videoDecoderCounters?.renderedOutputBufferCount ?: 0
                assertNull(player.playerError)
            }
            return position.get() to frames
        }
        try {
            compose.waitUntil(30_000) { sample().second > 5 }
            val before = sample()
            repeat(4) {
                compose.runOnIdle { mini = !mini }
                compose.waitForIdle()
                Thread.sleep(350)
            }
            val after = sample()
            assertEquals(1, attached)
            assertEquals(0, detached)
            assertTrue(after.second > before.second)
            assertTrue("Progress jumped or stopped", after.first - before.first in 500..5_000)
        } finally { compose.runOnUiThread { player.release() } }
    }
}
