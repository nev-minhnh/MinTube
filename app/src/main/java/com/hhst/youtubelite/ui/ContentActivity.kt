package com.hhst.youtubelite.ui

import android.os.Bundle
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhst.youtubelite.data.media.ContentRepositories
import com.hhst.youtubelite.player.PlaybackSession
import com.hhst.youtubelite.player.surface.MiniPlayerStore
import com.hhst.youtubelite.player.surface.MiniPlayerWindow
import com.hhst.youtubelite.watch.presentation.WatchPlayer
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

abstract class ContentActivity : NativeActivity(), com.hhst.youtubelite.ui.design.MiniDockHost {
    /** Where the docked mini sits, for the lists of this screen (see `miniDockListPadding`). */
    override val miniDockArea = com.hhst.youtubelite.ui.design.MiniDockArea()
    private var miniWindowReady = false
    private var miniEntryBounds by mutableStateOf<androidx.compose.ui.geometry.Rect?>(null)
    private var miniEntryVersion by mutableIntStateOf(0)
    private var miniPreDraw: android.view.ViewTreeObserver.OnPreDrawListener? = null
    private var miniComposition: ComposeView? = null
    private var miniHostFocused by mutableStateOf(false)
    private var resumedDuringMiniRestore = false
    /** The mini dock area in window pixels (inside the safe area, off the rail); null until laid out. */
    private var miniDockBounds: android.graphics.RectF? = null

    /**
     * This browse screen is itself in Picture-in-Picture, showing the in-app mini's video full-window.
     * The watch page has its own PiP ([com.hhst.youtubelite.player.PlaybackActivity]); this one covers
     * leaving the app from any other screen while the docked mini is playing. Same player, same
     * surface: the overlay's one [MiniPlayerWindow] just fills the PiP window instead of docking.
     */
    private var contentPip by mutableStateOf(false)
    private var contentPipWasActive = false
    private val pipPrefs: com.hhst.youtubelite.extension.ExtensionManager by inject()

    override fun onStart() {
        val installMini = !ContentRepositories.fixtures(intent) || playbackTest()
        // Publish the handoff before retained lifecycle collectors can create the mini.
        if (installMini) {
            miniWindowReady = false
            miniEntryBounds = com.hhst.youtubelite.navigation.WatchTransition.takeMiniEntryBounds()
            if (miniEntryBounds != null) miniEntryVersion++
        }
        super.onStart()
        if (!installMini) return
        awaitMiniWindow()
    }

    private fun awaitMiniWindow() {
        val decor = window.decorView
        miniPreDraw?.let { decor.viewTreeObserver.removeOnPreDrawListener(it) }
        val deadline = android.os.SystemClock.uptimeMillis() + 500
        val listener = object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val state = PlaybackSession.player.value?.uiState?.value
                val ready = state?.visible != true || !state.mini || miniWindowReady ||
                    android.os.SystemClock.uptimeMillis() >= deadline
                if (ready) {
                    decor.viewTreeObserver.removeOnPreDrawListener(this)
                    if (miniPreDraw === this) miniPreDraw = null
                } else {
                    // A retained Compose host can defer layout until dispatchDraw,
                    // which this gate is withholding.
                    miniComposition?.getChildAt(0)?.requestLayout()
                    decor.postInvalidateOnAnimation()
                }
                return ready
            }
        }
        miniPreDraw = listener
        decor.viewTreeObserver.addOnPreDrawListener(listener)
    }

    override fun onStop() {
        miniPreDraw?.let { window.decorView.viewTreeObserver.removeOnPreDrawListener(it) }
        miniPreDraw = null
        super.onStop()
        com.hhst.youtubelite.navigation.WatchTransition.clearMiniRestore(this)
        resumedDuringMiniRestore = false
        closeStoppedContentPip()
    }

    /** True while leaving the app now should turn the playing in-app mini into Picture-in-Picture. */
    private fun miniPipEligible(): Boolean {
        if (!com.hhst.youtubelite.core.PipSupport.isSupported(this)) return false
        if (ContentRepositories.fixtures(intent) && !playbackTest()) return false
        // The watch page owns PiP while it is pinned; never a second PiP host.
        if (PlaybackSession.inPip.value) return false
        val vm = PlaybackSession.player.value ?: return false
        val state = vm.uiState.value
        return state.visible && state.mini && (state.isPlaying || state.loading) && !vm.audioOnlyBackground &&
            vm.currentMediaKind != com.hhst.youtubelite.player.MediaKind.SHORTS &&
            pipPrefs.isEnabled(com.hhst.youtubelite.extension.PreferenceKeys.ENABLE_PIP)
    }

    private fun miniPipParams(autoEnter: Boolean): android.app.PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        val state = PlaybackSession.player.value?.uiState?.value
        val (n, d) = com.hhst.youtubelite.player.surface.PlayerUi.pipAspect(state?.videoWidth ?: 0, state?.videoHeight ?: 0)
        val builder = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(n, d))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    /**
     * API 31+: keep the system's auto-enter in step with the mini (Home, recents and app switches then
     * animate straight into PiP). Off while an in-app navigation or overlay holds [PipAutoEnter] suppression.
     */
    private fun syncMiniPip() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S || contentPip) return
        if (!com.hhst.youtubelite.core.PipSupport.isSupported(this)) return
        val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val autoEnter = resumed && !com.hhst.youtubelite.core.PipAutoEnter.isSuppressed() && miniPipEligible()
        miniPipParams(autoEnter)?.let { params -> runCatching { setPictureInPictureParams(params) } }
    }

    override fun startActivity(intent: android.content.Intent, options: Bundle?) {
        super.startActivity(intent, options)
        // NativeActivity has just taken the navigation PiP hold; push auto-enter off with it.
        syncMiniPip()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // API 26-30 have no auto-enter; same leave-hint rules as the watch page.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ||
            android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        if (android.os.SystemClock.elapsedRealtime() < com.hhst.youtubelite.core.PipAutoEnter.legacySuppressUntil) return
        if (com.hhst.youtubelite.core.PipAutoEnter.isSuppressed() || !miniPipEligible()) return
        miniPipParams(autoEnter = false)?.let { params -> runCatching { enterPictureInPictureMode(params) } }
    }

    // The two-arg hook is the framework dispatch path; the one-arg form lacks the config.
    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        contentPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            contentPipWasActive = true
            pipOwner = java.lang.ref.WeakReference(this)
        } else if (pipOwner?.get() === this) pipOwner = null
        com.hhst.youtubelite.diagnostics.AppLog.event(com.hhst.youtubelite.diagnostics.AppLog.Category.APP,
            "content_pip_state", mapOf("active" to isInPictureInPictureMode))
    }

    /** The PiP window was closed (not expanded): stop the mini like its X button would. */
    private fun closeStoppedContentPip() {
        if (!contentPipWasActive || isChangingConfigurations) return
        if (!isFinishing && getSystemService(android.os.PowerManager::class.java)?.isInteractive == false) return
        contentPipWasActive = false
        contentPip = false
        if (pipOwner?.get() === this) pipOwner = null
        PlaybackSession.player.value?.takeIf { it.uiState.value.visible && it.uiState.value.mini }?.onMiniClose()
    }

    override fun onDestroy() {
        if (pipOwner?.get() === this) pipOwner = null
        com.hhst.youtubelite.navigation.WatchTransition.clearMiniRestore(this)
        super.onDestroy()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (ContentRepositories.fixtures(intent) && !playbackTest()) return
        val overlay = object : android.widget.FrameLayout(this) {
            private val picturePaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
            private val blackPaint = android.graphics.Paint().apply { color = android.graphics.Color.BLACK }
            private val clip = android.graphics.Path()
            private val position = IntArray(2)
            override fun dispatchDraw(canvas: android.graphics.Canvas) {
                // Omit the mini from browse snapshots without changing view visibility:
                // hiding a SurfaceView destroys its video output.
                if (com.hhst.youtubelite.navigation.WatchTransition.capturingBackdrop) return
                super.dispatchDraw(canvas)
                val cover = com.hhst.youtubelite.navigation.WatchTransition.miniRestoreFrame(this@ContentActivity) ?: return
                val state = PlaybackSession.player.value?.uiState?.value
                if (state?.visible != true || state.videoId != cover.frame.videoId || PlaybackSession.inPip.value) return
                // openVideo leaves mini mode before the new window is drawn;
                // paint its last picture without retaining a decoder.
                getLocationInWindow(position)
                val saved = canvas.save()
                canvas.translate(-position[0].toFloat(), -position[1].toFloat())
                // The handoff picture never covers the navigation rail either.
                miniDockBounds?.let { dock ->
                    val far = 1_000_000f
                    if (layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) canvas.clipRect(-far, -far, dock.right, far)
                    else canvas.clipRect(dock.left, -far, far, far)
                }
                val bounds = cover.bounds.run { android.graphics.RectF(left, top, right, bottom) }
                val radius = com.hhst.youtubelite.player.surface.MiniPlayerLayout.CORNER_DP * resources.displayMetrics.density
                clip.reset()
                clip.addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW)
                canvas.clipPath(clip)
                canvas.drawRect(bounds, blackPaint)
                val video = cover.videoBounds.run { android.graphics.RectF(left, top, right, bottom) }
                canvas.drawBitmap(cover.frame.image, null, video, picturePaint)
                canvas.restoreToCount(saved)
            }
        }
        overlay.addView(ComposeView(this).apply {
            miniComposition = this
            setContent {
                var started by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, _ -> started = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val player by PlaybackSession.player.collectAsStateWithLifecycle()
                val pip by PlaybackSession.inPip.collectAsStateWithLifecycle()
                if (started) player?.let { vm ->
                    val state by vm.uiState.collectAsStateWithLifecycle()
                    val cover = com.hhst.youtubelite.navigation.WatchTransition.miniRestoreFrame(this@ContentActivity)
                    val previousCover = remember {
                        arrayOf<com.hhst.youtubelite.navigation.WatchTransition.MiniRestoreFrame?>(null)
                    }
                    SideEffect {
                        if (cover != null && (!state.visible || state.videoId != cover.frame.videoId || pip))
                            com.hhst.youtubelite.navigation.WatchTransition.clearMiniRestore(this@ContentActivity)
                        // This picture is on the native overlay's display list;
                        // Compose invalidation alone cannot redraw it.
                        if (cover != null || previousCover[0] != null)
                            (miniComposition?.parent as? android.view.View)?.invalidate()
                        previousCover[0] = cover
                    }
                    // Keeps auto-PiP in step with play/pause, video size and mini/visible changes.
                    LaunchedEffect(state.visible, state.mini, state.isPlaying, state.loading, state.videoWidth, state.videoHeight, pip) {
                        syncMiniPip()
                    }
                    // While another screen of this app is in PiP it alone hosts the player.
                    val otherPip = pipOwner?.get()?.let { it !== this@ContentActivity } == true
                    if (state.visible && state.mini && !pip && !otherPip) {
                        val store: MiniPlayerStore = koinInject()
                        // Shared entry: provides LocalAppColors, LocalMotionScale and LocalHideShorts here too.
                        com.hhst.youtubelite.ui.design.AppTheme {
                            // One source with the tab bar and rail: above the bar, or beside the rail.
                            val dock = com.hhst.youtubelite.ui.design.rememberNavigationForm()
                            // In this screen's own PiP the one window fills the PiP frame over a black backdrop.
                            val ownPip = contentPip
                            val density = androidx.compose.ui.platform.LocalDensity.current.density
                            DisposableEffect(Unit) { onDispose { miniDockArea.topInWindowPx = null } }
                            BoxWithConstraints((if (ownPip) Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)
                                else Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(start = com.hhst.youtubelite.ui.design.MiniDock.startInsetDp(dock).dp))
                                .onGloballyPositioned { coordinates ->
                                    val bounds = coordinates.boundsInWindow()
                                    miniDockBounds = android.graphics.RectF(bounds.left, bounds.top, bounds.right, bounds.bottom)
                                    // Same spec MiniPlayerWindow docks with (the box fills its constraints).
                                    val spec = com.hhst.youtubelite.player.surface.MiniPlayerLayout.computeSpec(
                                        (bounds.width / density).toInt(), com.hhst.youtubelite.ui.design.MiniDock.bottomInsetDp(dock),
                                        com.hhst.youtubelite.player.surface.MiniPlayerLayout.NO_WIDTH_OVERRIDE_DP,
                                        com.hhst.youtubelite.ui.design.MiniDock.bottomFloorDp(dock))
                                    miniDockArea.topInWindowPx = if (ownPip) null else com.hhst.youtubelite.ui.design.MiniDock
                                        .miniTopInWindowPx(bounds.bottom, spec.bottomMarginDp, spec.heightDp, density)
                                }
                                // The mini (also mid-entry from the watch page, or folded to the start
                                // edge) is clipped, drawing and touch, at the dock's start edge, so it
                                // never lies over the rail.
                                .graphicsLayer { clip = true; shape = StartEdgeClip }) {
                                MiniPlayerWindow(maxWidth.value.toInt(),
                                    com.hhst.youtubelite.ui.design.MiniDock.bottomInsetDp(dock), store, state.controlsVisible,
                                    vm::onToggleControls, onDismiss = vm::onMiniClose,
                                    bottomFloorDp = com.hhst.youtubelite.ui.design.MiniDock.bottomFloorDp(dock),
                                    mini = !ownPip, fillsWindow = ownPip, fullscreenSwipeEnabled = !ownPip,
                                    initialBounds = com.hhst.youtubelite.navigation.WatchTransition.miniBounds,
                                    animateFromBounds = miniEntryBounds,
                                    entryAnimationReady = miniHostFocused,
                                    entryKey = miniEntryVersion,
                                    onReadyForDisplay = {
                                        miniWindowReady = true
                                        if (resumedDuringMiniRestore) {
                                            com.hhst.youtubelite.navigation.WatchTransition.clearMiniRestore(this@ContentActivity)
                                            resumedDuringMiniRestore = false
                                        }
                                    }) {
                                    WatchPlayer(Modifier.fillMaxSize(), vm::onMiniRestore, pip = ownPip)
                                }
                            }
                        }
                    }
                }
            }
        }, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        findViewById<ViewGroup>(android.R.id.content).addView(overlay,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    private fun isInPipNow(): Boolean =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode

    private fun playbackTest() = com.hhst.youtubelite.BuildConfig.DEBUG && intent.getBooleanExtra("playback_test", false)

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        miniHostFocused = hasFocus
    }

    override fun onResume() {
        // A quick Back can resume browse before it ever stops, so there is no onStart for this handoff.
        if (!ContentRepositories.fixtures(intent) || playbackTest()) {
            // A cancelled entrance can return before this source stops; keep the
            // static picture until the returning mini is laid out.
            com.hhst.youtubelite.navigation.WatchTransition.miniRestoreFrame(this)?.let { cover ->
                val player = PlaybackSession.player.value
                val originalSurface = cover.frame.source.get()
                if (player?.uiState?.value?.mini == true && originalSurface != null && player.hasRenderedFrame(originalSurface)) {
                    // Target cancelled before taking playback; the original mini
                    // already has its live picture underneath.
                    com.hhst.youtubelite.navigation.WatchTransition.clearMiniRestore(this)
                } else {
                    resumedDuringMiniRestore = true
                    miniWindowReady = false
                    awaitMiniWindow()
                }
            }
            com.hhst.youtubelite.navigation.WatchTransition.takeMiniEntryBounds()?.let {
                miniWindowReady = false
                miniEntryBounds = it
                miniEntryVersion++
                awaitMiniWindow()
            }
        }
        super.onResume()
        com.hhst.youtubelite.navigation.WatchTransition.browseResumed(this)
        if (!isInPipNow()) contentPipWasActive = false
        syncMiniPip()
        if (!ContentRepositories.fixtures(intent) || playbackTest()) {
            // Entering PiP can itself resume browse; the pinned activity still owns playback until it exits PiP.
            if (!PlaybackSession.inPip.value) PlaybackSession.player.value?.onBrowseOpened()
        }
    }
}

/** The browse screen currently in Picture-in-Picture, if any (Compose state: other hosts hide their mini). */
private var pipOwner by mutableStateOf<java.lang.ref.WeakReference<ContentActivity>?>(null)

/** Clips only the start edge: the other three sides stay open for shadows and drag overshoot. */
private object StartEdgeClip : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val far = 1_000_000f
        return androidx.compose.ui.graphics.Outline.Rectangle(
            if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Ltr) androidx.compose.ui.geometry.Rect(0f, -far, far, far)
            else androidx.compose.ui.geometry.Rect(-far, -far, size.width, far))
    }
}
