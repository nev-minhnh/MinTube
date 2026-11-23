package com.hhst.youtubelite.tv.update

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.update.AppUpdater
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSidePanel
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvType
import kotlinx.coroutines.launch

/** Actions of the update UI; the controller wires them to [TvUpdateController]. */
interface TvUpdateActions {
    fun check()
    fun download()
    fun retry()
    fun dismiss()

    object None : TvUpdateActions {
        override fun check() = Unit
        override fun download() = Unit
        override fun retry() = Unit
        override fun dismiss() = Unit
    }
}

@Composable
fun tvUpdateErrorText(error: TvUpdateError): String = stringResource(when (error) {
    TvUpdateError.Check -> R.string.tv_update_error_check
    TvUpdateError.Network -> R.string.update_error_network
    TvUpdateError.Checksum -> R.string.update_error_checksum
    TvUpdateError.NoChecksum -> R.string.update_error_no_checksum
    TvUpdateError.NoPackage -> R.string.update_no_package
    TvUpdateError.Install -> R.string.update_install_failed
})

/**
 * The panel over Settings for every state that needs a decision or shows progress. It is a
 * focus-trapping dialog: Back always calls [TvUpdateActions.dismiss] (Later / Cancel / Close).
 */
@Composable
fun TvUpdatePanel(state: TvUpdateState, actions: TvUpdateActions) {
    if (!state.isPanel) return
    val title = stringResource(if (state is TvUpdateState.Downloading) R.string.update_downloading else R.string.update_available_title)
    TvSidePanel(title, actions::dismiss, Modifier.testTag(TAG_UPDATE_PANEL), width = 640.dp) {
        when (state) {
            is TvUpdateState.Available -> AvailableContent(state, actions)
            is TvUpdateState.Downloading -> DownloadingContent(state, actions)
            is TvUpdateState.Failed -> MessageContent(tvUpdateErrorText(state.error), actions)
            is TvUpdateState.NeedsPermission -> MessageContent(stringResource(
                if (state.settingsOpened) R.string.tv_update_permission_needed else R.string.tv_update_permission_manual), actions)
            else -> Unit
        }
    }
}

@Composable
private fun FocusOnShow(requester: FocusRequester) {
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { requester.requestFocus() }
    }
}

@Composable
private fun ColumnScope.AvailableContent(state: TvUpdateState.Available, actions: TvUpdateActions) {
    val context = LocalContext.current
    val release = state.release
    Text(stringResource(R.string.update_version_change, "MinTube TV ${state.current}", release.version),
        style = TvType.Body, color = TvColors.Ink, modifier = Modifier.testTag(TAG_UPDATE_VERSION))
    if (release.apkSize > 0) {
        Text(stringResource(R.string.update_size, Formatter.formatFileSize(context, release.apkSize)),
            style = TvType.Caption, color = TvColors.Secondary)
    }
    val download = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
        TvButton(stringResource(R.string.update_download_install), actions::download,
            Modifier.focusRequester(download).testTag(TAG_UPDATE_DOWNLOAD), primary = true)
        TvButton(stringResource(R.string.update_later), actions::dismiss, Modifier.testTag(TAG_UPDATE_LATER))
    }
    val notes = remember(release.notes) { AppUpdater.plainNotes(release.notes) }
    if (notes.isNotBlank()) ScrollableNotes(notes, Modifier.weight(1f))
    FocusOnShow(download)
}

/** Release notes the D-pad scrolls: Down/Up scroll while there is more, then focus moves on. */
@Composable
private fun ScrollableNotes(text: String, modifier: Modifier) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    var focused by remember { mutableStateOf(false) }
    val step = with(LocalDensity.current) { 96.dp.toPx() }
    Box(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) TvColors.Surface else TvColors.Background)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> (scroll.value < scroll.maxValue).also {
                        if (it) scope.launch { scroll.animateScrollBy(step) }
                    }
                    Key.DirectionUp -> (scroll.value > 0).also {
                        if (it) scope.launch { scroll.animateScrollBy(-step) }
                    }
                    else -> false
                }
            }
            .focusable()
            .testTag(TAG_UPDATE_NOTES),
    ) {
        Column(Modifier.verticalScroll(scroll).padding(TvSpacing.md)) {
            Text(text, style = TvType.Body, color = TvColors.Secondary)
        }
    }
}

@Composable
private fun DownloadingContent(state: TvUpdateState.Downloading, actions: TvUpdateActions) {
    val percent = (state.progress * 100).toInt().coerceIn(0, 100)
    Text("MinTube TV ${state.release.version}", style = TvType.Body, color = TvColors.Ink)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(TvColors.Surface)
        .testTag(TAG_UPDATE_PROGRESS)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(state.progress.coerceIn(0f, 1f)).background(TvColors.Accent))
    }
    Text(stringResource(R.string.update_progress_percent, percent), style = TvType.Body, color = TvColors.Ink,
        modifier = Modifier.testTag(TAG_UPDATE_PERCENT))
    val cancel = remember { FocusRequester() }
    TvButton(stringResource(R.string.cancel), actions::dismiss, Modifier.focusRequester(cancel).testTag(TAG_UPDATE_CANCEL))
    FocusOnShow(cancel)
}

@Composable
private fun MessageContent(message: String, actions: TvUpdateActions) {
    Text(message, style = TvType.Body, color = TvColors.Ink, modifier = Modifier.testTag(TAG_UPDATE_MESSAGE))
    val retry = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
        TvButton(stringResource(R.string.retry), actions::retry, Modifier.focusRequester(retry).testTag(TAG_UPDATE_RETRY), primary = true)
        TvButton(stringResource(R.string.close), actions::dismiss, Modifier.testTag(TAG_UPDATE_CLOSE))
    }
    FocusOnShow(retry)
}

const val TAG_UPDATE_PANEL = "tv-update-panel"
const val TAG_UPDATE_VERSION = "tv-update-version"
const val TAG_UPDATE_DOWNLOAD = "tv-update-download"
const val TAG_UPDATE_LATER = "tv-update-later"
const val TAG_UPDATE_NOTES = "tv-update-notes"
const val TAG_UPDATE_PROGRESS = "tv-update-progress"
const val TAG_UPDATE_PERCENT = "tv-update-percent"
const val TAG_UPDATE_CANCEL = "tv-update-cancel"
const val TAG_UPDATE_MESSAGE = "tv-update-message"
const val TAG_UPDATE_RETRY = "tv-update-retry"
const val TAG_UPDATE_CLOSE = "tv-update-close"
const val TAG_UPDATE_STATUS = "tv-update-status"
const val TAG_UPDATE_INLINE_RETRY = "tv-update-inline-retry"
