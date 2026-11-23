package com.hhst.youtubelite.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.settings.presentation.SettingsGroup
import com.hhst.youtubelite.settings.presentation.SettingsLayout
import com.hhst.youtubelite.settings.presentation.SettingsPageFrame
import com.hhst.youtubelite.settings.presentation.SettingsRow
import com.hhst.youtubelite.settings.presentation.settingsInset
import com.hhst.youtubelite.ui.components.AppLinearProgressIndicator
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/**
 * About page. Stateless: `AboutActivity` owns cache clearing, the update check and the diagnostic
 * export; [busy] disables the actions that run through it and [status] is its last result.
 */
@Composable
fun AboutScreen(label: String, version: String, onClose: () -> Unit, busy: Boolean, status: String,
    onSource: () -> Unit, onUpdate: () -> Unit, onRelease: () -> Unit, onClear: () -> Unit, onExport: () -> Unit,
    onSave: () -> Unit = {}, onUpstream: () -> Unit = {},
    update: UpdateUiState = UpdateUiState.None, onUpdateInstall: () -> Unit = {}, onUpdateLater: () -> Unit = {},
    onUpdateCancel: () -> Unit = {}, onUpdateRetry: () -> Unit = {}) {
    BackHandler { onClose() }
    AboutUpdateDialog(update, onUpdateInstall, onUpdateLater, onUpdateCancel)
    SettingsPageFrame(
        title = stringResource(R.string.about),
        onBack = onClose,
        screenTag = "about-screen",
        topBarTag = "about-topbar",
        pageTag = "about-page",
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        footer = {
            if (update is UpdateUiState.Failed) AboutStatus(false, update.message, onUpdateRetry)
            else AboutStatus(busy, status)
        },
    ) {
        val inset = settingsInset()
        Row(Modifier.fillMaxWidth().padding(horizontal = inset, vertical = MinSpace.xl).testTag("about-identity"),
            verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(com.hhst.youtubelite.ui.design.mintubeMarkRes()), null, Modifier.size(64.dp), contentScale = ContentScale.Fit)
            Spacer(Modifier.width(MinSpace.lg))
            Column(Modifier.weight(1f)) {
                AppText(label, MinType.Display, Modifier.semantics { heading() })
                Spacer(Modifier.height(MinSpace.xs))
                AppText(stringResource(R.string.account_about_version, version), MinType.Body,
                    Modifier.testTag("about-version"), color = DesignTokens.Secondary)
            }
        }
        Column(Modifier.padding(horizontal = inset).fillMaxWidth().clip(MinRadius.Card)
            .background(DesignTokens.AccentContainer).padding(MinSpace.lg)) {
            AppText(stringResource(R.string.about_fork_notice), MinType.Body, Modifier.testTag("about-fork-notice"))
            Spacer(Modifier.height(MinSpace.sm))
            AppText(stringResource(R.string.about_credits), MinType.Caption, Modifier.testTag("about-credits"),
                color = DesignTokens.Secondary)
        }

        SettingsGroup(stringResource(R.string.account_about_project))
        SettingsRow(stringResource(R.string.about_source), tag = "about-source", icon = AppIcon.Link, onClick = onSource)
        SettingsRow(stringResource(R.string.about_upstream), tag = "about-upstream", icon = AppIcon.OpenWith, onClick = onUpstream)
        SettingsRow(stringResource(R.string.account_about_license), stringResource(R.string.account_about_license_name),
            tag = "about-license", icon = AppIcon.Info)

        SettingsGroup(stringResource(R.string.account_about_updates))
        SettingsRow(stringResource(R.string.about_check_update), tag = "about-check-update", icon = AppIcon.Refresh,
            enabled = !busy, onClick = onUpdate)
        SettingsRow(stringResource(R.string.about_releases), tag = "about-releases", icon = AppIcon.Globe, onClick = onRelease)

        SettingsGroup(stringResource(R.string.account_about_storage))
        SettingsRow(stringResource(R.string.about_clear_cache), stringResource(R.string.account_about_clear_cache_summary),
            tag = "about-clear-cache", icon = AppIcon.Delete, enabled = !busy, onClick = onClear)

        SettingsGroup(stringResource(R.string.account_about_diagnostics))
        Column(Modifier.testTag("about-diagnostics")) {
            SettingsRow(stringResource(R.string.about_export_logs), stringResource(R.string.account_about_export_summary),
                tag = "about-export-logs", icon = AppIcon.Share, enabled = !busy, onClick = onExport)
            SettingsRow(stringResource(R.string.about_save_logs), stringResource(R.string.account_about_save_summary),
                tag = "about-save-logs", icon = AppIcon.Download, enabled = !busy, onClick = onSave)
        }
    }
}

/** Result line of the last action, pinned under the list so it is visible wherever the list is scrolled. */
@Composable
private fun AboutStatus(busy: Boolean, status: String, onRetry: (() -> Unit)? = null) {
    if (!busy && status.isEmpty()) return
    Box(Modifier.fillMaxWidth().background(DesignTokens.Panel), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = SettingsLayout.MaxWidth).fillMaxWidth().testTag("about-status")
            .semantics { liveRegion = LiveRegionMode.Polite }) {
            if (busy) AppLinearProgressIndicator(Modifier.fillMaxWidth().testTag("about-busy-indicator"))
            else Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
            if (status.isNotEmpty()) AppText(status, MinType.Body,
                Modifier.padding(horizontal = settingsInset(), vertical = MinSpace.md))
            if (onRetry != null) com.hhst.youtubelite.ui.components.AppTextButton(onRetry,
                Modifier.padding(horizontal = settingsInset()).testTag("update-retry")) {
                androidx.compose.material3.Text(stringResource(R.string.retry))
            }
        }
    }
}
