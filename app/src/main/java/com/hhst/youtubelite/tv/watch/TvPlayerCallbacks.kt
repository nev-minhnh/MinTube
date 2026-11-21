package com.hhst.youtubelite.tv.watch

import com.hhst.youtubelite.player.PlayerViewModel

/** Player commands the TV overlay issues. Narrow on purpose: UI tests drive a fake. */
interface TvPlayerCallbacks {
    fun playPause()
    fun play()
    fun pause()
    /** Relative preview from the position when the seek started; [seekCommit] lands it. */
    fun seekPreview(offsetMs: Long)
    fun seekCommit(offsetMs: Long)
    fun seekTo(positionMs: Long)
    fun next()
    fun previous()
    fun quality(label: String?)
    fun speed(speed: Float)
    fun subtitle(trackKey: String)
    fun subtitlesOff()
    fun audioTrack(trackKey: String?)
    fun audioMenuOpened()
    fun openVideo(videoId: String)
    fun cancelAutoplay()
    fun retry()
    fun loadMoreRelated()
    fun exit()
}

/**
 * The TV overlay on the shared [PlayerViewModel]: every call is an existing phone action
 * (scrub preview/commit, quality, speed, subtitle, audio track, next/previous, retry).
 */
open class PlayerViewModelTvCallbacks(
    private val player: PlayerViewModel,
    private val onOpenVideo: (String) -> Unit,
    private val onCancelAutoplay: () -> Unit,
    private val onLoadMoreRelated: () -> Unit,
    private val onExit: () -> Unit,
) : TvPlayerCallbacks {
    override fun playPause() = player.onPlayPause()
    override fun play() {
        if (!player.uiState.value.userWantsPlay) player.onPlayPause()
    }
    override fun pause() {
        if (player.uiState.value.userWantsPlay) player.onPlayPause()
    }
    override fun seekPreview(offsetMs: Long) = player.onScrubPreview(offsetMs)
    override fun seekCommit(offsetMs: Long) = player.onScrubCommit(offsetMs)
    override fun seekTo(positionMs: Long) = player.onSeek(positionMs)
    override fun next() = player.onNext()
    override fun previous() = player.onPrevious()
    override fun quality(label: String?) = player.onQuality(label)
    override fun speed(speed: Float) = player.onSpeed(speed)
    override fun subtitle(trackKey: String) {
        val state = player.uiState.value
        // Picking the active track would toggle captions off (phone rule); keep it on instead.
        if (state.subtitleEnabled && state.subtitleKey == trackKey) return
        player.onSubtitle(trackKey)
    }
    override fun subtitlesOff() {
        val state = player.uiState.value
        // Tapping the active language turns captions off: the phone menu's rule.
        if (state.subtitleEnabled) state.subtitleKey?.let(player::onSubtitle)
    }
    override fun audioTrack(trackKey: String?) = player.onAudioTrack(trackKey)
    override fun audioMenuOpened() = player.onAudioMenuOpened()
    override fun openVideo(videoId: String) = onOpenVideo(videoId)
    override fun cancelAutoplay() = onCancelAutoplay()
    override fun retry() = player.onRetry()
    override fun loadMoreRelated() = onLoadMoreRelated()
    override fun exit() = onExit()
}
