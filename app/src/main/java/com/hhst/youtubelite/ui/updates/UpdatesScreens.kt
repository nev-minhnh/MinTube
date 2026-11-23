package com.hhst.youtubelite.ui.updates

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.time.publishedAgeText
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.AppUpdater
import com.hhst.youtubelite.settings.presentation.SettingsGroup
import com.hhst.youtubelite.settings.presentation.SettingsPageFrame
import com.hhst.youtubelite.settings.presentation.SettingsRow
import com.hhst.youtubelite.settings.presentation.SettingsToggle
import com.hhst.youtubelite.settings.presentation.settingsInset
import com.hhst.youtubelite.ui.components.AppLinearProgressIndicator
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

internal const val KEY_AUTO = "updates-auto"
internal const val KEY_NOTIFY = "updates-notify"

/** Updates page: App update card, general toggles, share and the changelog list. Stateless. */
@Composable
fun UpdatesScreen(
    state: UpdatesUiState,
    onBack: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenRelease: (AppRelease) -> Unit,
    onAutoCheck: (Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onShareApk: () -> Unit,
    onRetryHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageFrame(
        title = stringResource(R.string.settings_updates_title),
        onBack = onBack,
        screenTag = "updates-screen",
        topBarTag = "updates-topbar",
        pageTag = "updates-page",
        modifier = modifier,
    ) {
        val available = state.status as? UpdateStatus.Available
        SettingsRow(
            stringResource(R.string.updates_app_card_title),
            if (available != null) stringResource(R.string.updates_app_card_summary_available, available.release.version)
            else stringResource(R.string.updates_app_card_summary_current, state.currentVersion),
            tag = "updates-app-card",
            icon = if (available != null) AppIcon.Download else AppIcon.CheckCircle,
            chevron = true,
            onClick = onOpenApp,
        )

        SettingsGroup(stringResource(R.string.updates_group_general))
        val toggles = mapOf(KEY_AUTO to state.autoCheck, KEY_NOTIFY to state.notify)
        val onToggle: (String, Boolean) -> Unit = { key, value -> if (key == KEY_AUTO) onAutoCheck(value) else onNotify(value) }
        SettingsToggle(stringResource(R.string.updates_auto_check), stringResource(R.string.updates_auto_check_summary),
            KEY_AUTO, toggles, onToggle, AppIcon.Refresh)
        SettingsToggle(stringResource(R.string.updates_notifications), stringResource(R.string.updates_notifications_summary),
            KEY_NOTIFY, toggles, onToggle, AppIcon.Bell)
        SettingsRow(stringResource(R.string.updates_notification_settings),
            stringResource(R.string.updates_notification_settings_summary),
            tag = "updates-notification-settings", icon = AppIcon.Settings, chevron = true, onClick = onNotificationSettings)

        SettingsGroup(stringResource(R.string.updates_group_share))
        SettingsRow(stringResource(R.string.updates_share_apk), stringResource(R.string.updates_share_apk_summary),
            tag = "updates-share-apk", icon = AppIcon.Share, onClick = onShareApk)

        SettingsGroup(stringResource(R.string.updates_group_changelogs))
        when (val history = state.history) {
            HistoryState.Loading -> NoteLine(stringResource(R.string.updates_changelogs_loading), "updates-changelogs-loading")
            HistoryState.Failed -> {
                NoteLine(stringResource(R.string.updates_changelogs_error), "updates-changelogs-error")
                SettingsRow(stringResource(R.string.retry), tag = "updates-changelogs-retry", icon = AppIcon.Refresh,
                    onClick = onRetryHistory)
            }
            is HistoryState.Loaded -> history.releases.forEach { release ->
                val date = UpdateFormat.releaseDate(release.publishedAt)
                SettingsRow(
                    stringResource(R.string.updates_release_title, release.version),
                    if (date == null) stringResource(R.string.updates_release_summary_undated)
                    else stringResource(R.string.updates_release_summary, date, publishedAgeText(release.publishedAt)),
                    tag = "updates-release-${release.version}",
                    icon = AppIcon.Info,
                    chevron = true,
                    onClick = { onOpenRelease(release) },
                )
            }
        }
    }
}

@Composable
private fun NoteLine(text: String, tag: String) {
    AppText(text, MinType.Body, Modifier.padding(horizontal = settingsInset(), vertical = MinSpace.sm).testTag(tag),
        color = DesignTokens.Secondary)
}

@Composable
private fun InfoCard(text: String, tag: String) {
    Box(Modifier.padding(horizontal = settingsInset(), vertical = MinSpace.xs).fillMaxWidth()
        .clip(MinRadius.Card).background(DesignTokens.Surface).padding(MinSpace.lg).testTag(tag)) {
        AppText(text, MinType.Subtitle)
    }
}

/** App update page: big status, version cards, notes and install, and the check button pinned at the bottom. */
@Composable
fun AppUpdateScreen(
    state: UpdatesUiState,
    onBack: () -> Unit,
    onCheck: () -> Unit,
    onInstall: (AppRelease) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val checking = state.status == UpdateStatus.Checking
    SettingsPageFrame(
        title = stringResource(R.string.updates_app_card_title),
        onBack = onBack,
        screenTag = "app-update-screen",
        topBarTag = "app-update-topbar",
        pageTag = "app-update-page",
        modifier = modifier,
        footer = {
            Box(Modifier.fillMaxWidth().background(DesignTokens.Background)
                .padding(horizontal = settingsInset(), vertical = MinSpace.md)) {
                val available = state.status as? UpdateStatus.Available
                Column(Modifier.fillMaxWidth()) {
                    // With an update on offer the primary action is installing it, always in view (as in MinMusic).
                    if (available != null) {
                        InstallActions(available.release, state.install, onInstall, onCancel, onRetry)
                        Spacer(Modifier.height(MinSpace.sm))
                    }
                    MinButton(stringResource(R.string.updates_check), onCheck, Modifier.fillMaxWidth(),
                        icon = AppIcon.Refresh, enabled = !checking, pending = checking, testTag = "app-update-check",
                        kind = if (available != null) MinButtonKind.Tonal else MinButtonKind.Primary)
                }
            }
        },
    ) {
        val inset = settingsInset()
        val headline = when (state.status) {
            UpdateStatus.Unknown, UpdateStatus.Checking -> R.string.updates_status_checking
            is UpdateStatus.UpToDate -> R.string.updates_status_current
            is UpdateStatus.Available -> R.string.updates_status_available
            is UpdateStatus.Development -> R.string.updates_status_development
            UpdateStatus.Failed -> R.string.updates_status_failed
        }
        AppText(stringResource(headline), MinType.Display,
            Modifier.padding(horizontal = inset, vertical = MinSpace.xl).testTag("app-update-status")
                .semantics { heading(); liveRegion = LiveRegionMode.Polite })
        (state.status as? UpdateStatus.Development)?.latest?.let {
            AppText(stringResource(R.string.about_development_version, it), MinType.Body,
                Modifier.padding(horizontal = inset).testTag("app-update-development"), color = DesignTokens.Secondary)
            Spacer(Modifier.height(MinSpace.md))
        }
        InfoCard(stringResource(R.string.updates_version_card, state.currentVersion), "app-update-version")
        InfoCard(
            state.lastCheck?.let { stringResource(R.string.updates_last_check, UpdateFormat.lastCheck(it)) }
                ?: stringResource(R.string.updates_last_check_never),
            "app-update-last-check",
        )
        (state.status as? UpdateStatus.Available)?.let { available ->
            Spacer(Modifier.height(MinSpace.lg))
            InstallSection(available.release, state.currentVersion)
        }
    }
}

/** Release page of one changelog entry; offers install when that release is newer than the running build. */
@Composable
fun ReleaseNotesScreen(
    release: AppRelease,
    currentVersion: String,
    newer: Boolean,
    installable: Boolean,
    install: InstallState,
    onBack: () -> Unit,
    onInstall: (AppRelease) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageFrame(
        title = stringResource(R.string.updates_release_title, release.version),
        onBack = onBack,
        screenTag = "release-screen",
        topBarTag = "release-topbar",
        pageTag = "release-page",
        modifier = modifier,
        footer = if (newer && installable && release.apkUrl.isNotBlank()) {
            {
                Box(Modifier.fillMaxWidth().background(DesignTokens.Background)
                    .padding(horizontal = settingsInset(), vertical = MinSpace.md)) {
                    InstallActions(release, install, onInstall, onCancel, onRetry)
                }
            }
        } else null,
    ) {
        val inset = settingsInset()
        val date = UpdateFormat.releaseDate(release.publishedAt)
        AppText(stringResource(R.string.updates_release_title, release.version), MinType.Display,
            Modifier.padding(horizontal = inset, vertical = MinSpace.xl).semantics { heading() })
        if (date != null) {
            AppText(stringResource(R.string.updates_release_date, date, publishedAgeText(release.publishedAt)), MinType.Body,
                Modifier.padding(horizontal = inset).testTag("release-date"), color = DesignTokens.Secondary)
            Spacer(Modifier.height(MinSpace.md))
        }
        if (newer && installable && release.apkUrl.isNotBlank()) {
            InstallSection(release, currentVersion)
        } else {
            val note = if (release.version == currentVersion) R.string.updates_release_installed else R.string.updates_release_not_newer
            AppText(stringResource(note), MinType.Body, Modifier.padding(horizontal = inset).testTag("release-installed-note"),
                color = DesignTokens.Secondary)
            Spacer(Modifier.height(MinSpace.md))
            ReleaseNotes(release)
        }
    }
}

@Composable
private fun ReleaseNotes(release: AppRelease) {
    val notes = remember(release.notes) { AppUpdater.plainNotes(release.notes) }
    AppText(notes.ifEmpty { stringResource(R.string.updates_release_no_notes) }, MinType.Body,
        Modifier.padding(horizontal = settingsInset(), vertical = MinSpace.sm).fillMaxWidth().testTag("update-notes"))
}

/** Version change, size and notes; the install action sits in the page footer ([InstallActions]). */
@Composable
private fun InstallSection(
    release: AppRelease,
    currentVersion: String,
) {
    val inset = settingsInset()
    val size = Formatter.formatShortFileSize(LocalContext.current, release.apkSize)
    AppText(stringResource(R.string.update_version_change, currentVersion, release.version), MinType.Subtitle,
        Modifier.padding(horizontal = inset).testTag("update-version"))
    AppText(stringResource(R.string.update_size, size), MinType.Caption,
        Modifier.padding(horizontal = inset).testTag("update-size"), color = DesignTokens.Secondary)
    Spacer(Modifier.height(MinSpace.sm))
    ReleaseNotes(release)
}

/** Download and install, or its progress with Cancel, or the error with Retry; kept in the footer. */
@Composable
private fun InstallActions(
    release: AppRelease,
    install: InstallState,
    onInstall: (AppRelease) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
        when {
            install is InstallState.Downloading && install.release.tag == release.tag -> {
                val percent = (install.progress * 100).toInt().coerceIn(0, 100)
                AppLinearProgressIndicator({ install.progress }, Modifier.fillMaxWidth().testTag("update-progress"))
                Spacer(Modifier.height(MinSpace.sm))
                AppText(stringResource(R.string.update_progress_percent, percent), MinType.Body, Modifier.testTag("update-percent"))
                Spacer(Modifier.height(MinSpace.sm))
                MinButton(stringResource(R.string.cancel), onCancel, Modifier.fillMaxWidth(), kind = MinButtonKind.Tonal,
                    testTag = "update-cancel")
            }
            else -> {
                if (install is InstallState.Failed && install.release.tag == release.tag) {
                    AppText(install.message, MinType.Body, Modifier.testTag("update-error")
                        .semantics { liveRegion = LiveRegionMode.Polite }, color = DesignTokens.Danger)
                    Spacer(Modifier.height(MinSpace.sm))
                    MinButton(stringResource(R.string.retry), onRetry, Modifier.fillMaxWidth(), kind = MinButtonKind.Tonal,
                        testTag = "update-retry")
                    Spacer(Modifier.height(MinSpace.sm))
                }
                MinButton(stringResource(R.string.update_download_install), { onInstall(release) },
                    Modifier.fillMaxWidth(), icon = AppIcon.Download, testTag = "update-install")
            }
        }
    }
}
