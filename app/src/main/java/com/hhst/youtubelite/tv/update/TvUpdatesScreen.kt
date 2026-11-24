package com.hhst.youtubelite.tv.update

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.time.publishedAgeText
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvFocusRequesters
import com.hhst.youtubelite.tv.design.TvListRow
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.tvPageMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.design.tvAnchor
import com.hhst.youtubelite.tv.design.tvFocusRestorer

/** The one-line status the Settings "Updates" row shows under its title. */
@Composable
fun tvUpdatesRowSummary(update: TvUpdateState, version: String): String = when (update) {
    is TvUpdateState.Checking -> stringResource(R.string.tv_update_checking)
    is TvUpdateState.UpToDate -> stringResource(R.string.tv_updates_row_up_to_date)
    is TvUpdateState.Available -> stringResource(R.string.tv_updates_row_available, update.release.version)
    is TvUpdateState.Development -> developmentText(update)
    is TvUpdateState.Failed -> if (update.release == null) tvUpdateErrorText(update.error) else stringResource(R.string.tv_updates_row_idle, version)
    else -> stringResource(R.string.tv_updates_row_idle, version)
}

@Composable
private fun developmentText(update: TvUpdateState.Development): String =
    if (update.latest.isBlank()) stringResource(R.string.tv_updates_development)
    else stringResource(R.string.about_development_version, update.latest)

/**
 * The Updates page: an "App update" card (status, version, last check, Check for updates), the two
 * toggles, and the changelog list. Initial focus is on Check for updates; closing a panel (Later, Cancel,
 * Back) returns focus to what opened it; Back with no panel open calls [onBack].
 */
@Composable
fun TvUpdatesScreen(
    ui: TvUpdatesUi,
    currentVersion: String,
    actions: TvUpdateActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val requesters = remember { TvFocusRequesters() }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = !ui.panelOpen, onBack = onBack)

    // First focus: Check for updates (the list may still be composing for a frame).
    LaunchedEffect(Unit) {
        repeat(10) {
            withFrameNanos { }
            if (requesters.request(KEY_CHECK)) return@LaunchedEffect
        }
    }
    // A panel closed (Later, Cancel, Close, Back): focus returns to the control that opened it.
    var panelWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(ui.panelOpen) {
        if (ui.panelOpen) { panelWasOpen = true; return@LaunchedEffect }
        if (!panelWasOpen) return@LaunchedEffect
        panelWasOpen = false
        withFrameNanos { }
        if (!requesters.request(focusedKey ?: KEY_CHECK)) requesters.request(KEY_CHECK)
    }

    fun Modifier.row(key: String, tag: String): Modifier = this
        .fillMaxWidth()
        .tvAnchor(requesters.of(key))
        .onFocusChanged { if (it.isFocused) focusedKey = key }
        .testTag(tag)

    val context = LocalContext.current
    val list = rememberLazyListState()
    Box(modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
        state = list,
        modifier = Modifier.widthIn(max = tvPageMaxWidth()).fillMaxHeight().tvFocusRestorer(requesters.of(KEY_CHECK)).testTag(TAG_UPDATES_LIST),
        contentPadding = PaddingValues(start = TvSpacing.md, end = TvSpacing.md,
            top = TvSpacing.OverscanVertical, bottom = TvSpacing.OverscanVertical + 48.dp),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
    ) {
        item(key = "title") {
            Text(stringResource(R.string.account_about_updates), style = TvType.Display, color = TvColors.Ink,
                modifier = Modifier.padding(bottom = TvSpacing.md).testTag(TAG_UPDATES_TITLE))
        }
        item(key = "h:app") { SectionTitle(stringResource(R.string.tv_updates_app_update)) }
        item(key = "card") {
            val update = ui.update
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(TvColors.Surface.copy(alpha = 0.6f)).padding(TvSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(TvSpacing.sm),
            ) {
                Text(cardStatus(update, currentVersion), style = TvType.Title, color = TvColors.Ink,
                    modifier = Modifier.testTag(TAG_UPDATES_STATUS))
                if (update !is TvUpdateState.Idle) {
                    Text(stringResource(R.string.tv_updates_version, currentVersion), style = TvType.Body, color = TvColors.Secondary,
                        modifier = Modifier.testTag(TAG_UPDATES_VERSION))
                }
                Text(
                    ui.lastCheck?.let {
                        stringResource(R.string.tv_updates_last_check, DateUtils.formatDateTime(context, it,
                            DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH))
                    } ?: stringResource(R.string.tv_updates_never_checked),
                    style = TvType.Caption, color = TvColors.Secondary, modifier = Modifier.testTag(TAG_UPDATES_LAST_CHECK),
                )
                Row(Modifier.padding(top = TvSpacing.sm), horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                    TvButton(stringResource(R.string.tv_update_check), actions::check,
                        Modifier.tvAnchor(requesters.of(KEY_CHECK)).onFocusChanged { if (it.isFocused) focusedKey = KEY_CHECK }
                            .testTag(TAG_UPDATES_CHECK),
                        primary = true)
                    val offer = (update as? TvUpdateState.Available)?.release
                    if (offer != null) {
                        TvButton(stringResource(R.string.tv_updates_view), { actions.openRelease(offer) },
                            Modifier.tvAnchor(requesters.of(KEY_VIEW)).onFocusChanged { if (it.isFocused) focusedKey = KEY_VIEW }
                                .testTag(TAG_UPDATES_VIEW))
                    }
                }
            }
        }
        item(key = "h:general") { SectionTitle(stringResource(R.string.tv_updates_general)) }
        item(key = "auto") {
            TvListRow(
                title = stringResource(R.string.tv_updates_auto),
                summary = stringResource(R.string.tv_updates_auto_summary),
                checked = ui.autoCheck,
                onClick = { actions.setAutoCheck(!ui.autoCheck) },
                modifier = Modifier.row(KEY_AUTO, TAG_UPDATES_AUTO),
            )
        }
        item(key = "notify") {
            TvListRow(
                title = stringResource(R.string.tv_updates_notify),
                summary = stringResource(R.string.tv_updates_notify_summary),
                checked = ui.notify,
                onClick = { actions.setNotify(!ui.notify) },
                modifier = Modifier.row(KEY_NOTIFY, TAG_UPDATES_NOTIFY),
            )
        }
        item(key = "h:logs") { SectionTitle(stringResource(R.string.tv_updates_changelogs)) }
        when (val history = ui.history) {
            is TvHistory.Loading -> item(key = "history:loading") {
                Text(stringResource(R.string.tv_updates_history_loading), style = TvType.Body, color = TvColors.Secondary,
                    modifier = Modifier.testTag(TAG_UPDATES_HISTORY_MESSAGE))
            }
            is TvHistory.Failed -> item(key = "history:failed") {
                Column(verticalArrangement = Arrangement.spacedBy(TvSpacing.sm)) {
                    Text(stringResource(R.string.tv_updates_history_error), style = TvType.Body, color = TvColors.Secondary,
                        modifier = Modifier.testTag(TAG_UPDATES_HISTORY_MESSAGE))
                    TvButton(stringResource(R.string.retry), { actions.loadHistory(force = true) },
                        Modifier.row(KEY_HISTORY_RETRY, TAG_UPDATES_HISTORY_RETRY))
                }
            }
            is TvHistory.Loaded -> if (history.entries.isEmpty()) {
                item(key = "history:empty") {
                    Text(stringResource(R.string.tv_updates_history_empty), style = TvType.Body, color = TvColors.Secondary,
                        modifier = Modifier.testTag(TAG_UPDATES_HISTORY_MESSAGE))
                }
            } else {
                history.entries.forEach { entry ->
                    item(key = "log:${entry.release.tag}") { ChangelogRow(entry, actions, Modifier.row(logKey(entry), logTag(entry))) }
                }
            }
        }
    }

    }

    TvUpdatePanel(ui, currentVersion, actions)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = TvType.Title, color = TvColors.Accent, modifier = Modifier.padding(top = TvSpacing.xl, bottom = TvSpacing.xs))
}

@Composable
private fun cardStatus(update: TvUpdateState, version: String): String = when (update) {
    is TvUpdateState.Idle -> stringResource(R.string.tv_updates_version, version)
    is TvUpdateState.Checking -> stringResource(R.string.tv_update_checking)
    is TvUpdateState.UpToDate -> stringResource(R.string.tv_updates_row_up_to_date)
    is TvUpdateState.Available -> stringResource(R.string.tv_updates_row_available, update.release.version)
    is TvUpdateState.Development -> developmentText(update)
    is TvUpdateState.Failed -> if (update.release == null) tvUpdateErrorText(update.error) else stringResource(R.string.update_downloading)
    is TvUpdateState.Downloading, is TvUpdateState.NeedsPermission -> stringResource(R.string.update_downloading)
}

@Composable
private fun ChangelogRow(entry: TvChangelogEntry, actions: TvUpdateActions, modifier: Modifier) {
    val date = entry.date
    val meta = if (date == null) stringResource(R.string.tv_updates_release_meta_short) else {
        val age = publishedAgeText(entry.release.publishedAt)
        // A release older than a year already reads as its date: do not repeat it.
        if (age == date || age.isBlank()) "${stringResource(R.string.tv_updates_release_meta_short)} · $date"
        else stringResource(R.string.tv_updates_release_meta, date, age)
    }
    TvListRow(
        title = stringResource(R.string.tv_updates_release_title, entry.release.version),
        summary = meta,
        value = if (entry.installed) stringResource(R.string.tv_updates_installed) else null,
        onClick = { actions.openRelease(entry.release) },
        modifier = modifier,
    )
}

private const val KEY_CHECK = "check"
private const val KEY_VIEW = "view"
private const val KEY_AUTO = "auto"
private const val KEY_NOTIFY = "notify"
private const val KEY_HISTORY_RETRY = "history-retry"
private fun logKey(entry: TvChangelogEntry) = "log:${entry.release.tag}"

fun logTag(entry: TvChangelogEntry) = "$TAG_UPDATES_LOG-${entry.release.version}"

const val TAG_UPDATES_SCREEN = "tv-updates"
const val TAG_UPDATES_LIST = "tv-updates-list"
const val TAG_UPDATES_TITLE = "tv-updates-title"
const val TAG_UPDATES_STATUS = "tv-updates-status"
const val TAG_UPDATES_VERSION = "tv-updates-version"
const val TAG_UPDATES_LAST_CHECK = "tv-updates-last-check"
const val TAG_UPDATES_CHECK = "tv-updates-check"
const val TAG_UPDATES_VIEW = "tv-updates-view"
const val TAG_UPDATES_AUTO = "tv-updates-auto"
const val TAG_UPDATES_NOTIFY = "tv-updates-notify"
const val TAG_UPDATES_LOG = "tv-updates-log"
const val TAG_UPDATES_HISTORY_MESSAGE = "tv-updates-history-message"
const val TAG_UPDATES_HISTORY_RETRY = "tv-updates-history-retry"
