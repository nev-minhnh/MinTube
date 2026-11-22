package com.hhst.youtubelite.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.components.AppAlertDialog
import com.hhst.youtubelite.ui.components.AppRadioButton
import com.hhst.youtubelite.ui.components.AppSwitch
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.rememberHapticAction
import com.hhst.youtubelite.ui.design.rememberWindowClass

/** Layout constants shared by Settings, Advanced, Backup and About. */
internal object SettingsLayout {
    /** Reading width of a settings list on wide windows. */
    val MaxWidth = 720.dp
    /** Rows grow with the font scale; this is only the floor. */
    val RowMinHeight = 56.dp
    val RowVertical = MinSpace.sm
    val IconSlot = 40.dp
}

/**
 * Page frame of every settings-style screen: Mint top bar and one scrolling list held to
 * [SettingsLayout.MaxWidth]. [pageKey] gives each page its own scroll position.
 */
@Composable
internal fun SettingsPageFrame(
    title: String,
    onBack: () -> Unit,
    screenTag: String,
    topBarTag: String,
    pageTag: String,
    modifier: Modifier = Modifier,
    privateBrowsing: Boolean = false,
    pageKey: Any? = null,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxSize().background(DesignTokens.Background).testTag(screenTag)) {
        MinTopBar(title = title, onBack = onBack, privateBrowsing = privateBrowsing, tag = topBarTag)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            key(pageKey) {
                // The scroll spans the window so the side strips scroll too; rows keep the reading width.
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(pageTag), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.widthIn(max = SettingsLayout.MaxWidth).fillMaxWidth()) {
                        content()
                        Spacer(Modifier.height(MinSpace.xxl))
                    }
                }
            }
        }
        footer?.invoke()
    }
}

/** Horizontal inset of rows: 16dp on phones, 24dp from 600dp width. */
@Composable
internal fun settingsInset(): Dp = MinSpace.pageInset(rememberWindowClass())

/** Section header of a grouped list. */
@Composable
internal fun SettingsGroup(label: String) {
    val inset = settingsInset()
    AppText(label, MinType.Label,
        Modifier.padding(start = inset, end = inset, top = MinSpace.xl, bottom = MinSpace.sm).semantics { heading() },
        color = DesignTokens.AccentText)
}

@Composable
internal fun SettingsToggle(
    title: String,
    summary: String,
    key: String,
    preferences: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    icon: AppIcon? = null,
) {
    val enabled = preferences[key] == true
    val haptic = rememberHapticAction()
    val inset = settingsInset()
    Row(
        Modifier.fillMaxWidth().heightIn(min = SettingsLayout.RowMinHeight)
            .toggleable(value = enabled, role = Role.Switch) { haptic(); onToggle(key, it) }
            .testTag("settings-toggle-$key").padding(horizontal = inset, vertical = SettingsLayout.RowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) SettingsLeadingIcon(icon)
        SettingsLabels(title, summary, Modifier.weight(1f))
        Spacer(Modifier.width(MinSpace.lg))
        AppSwitch(enabled, onCheckedChange = null, modifier = Modifier.testTag("settings-toggle-state-$key"))
    }
}

/**
 * One row of a settings list. [value] is the current choice of a picker row and is shown under the
 * title, so a long value never squeezes the title at large font scales. [chevron] marks rows that
 * open another page.
 */
@Composable
internal fun SettingsRow(
    title: String,
    summary: String? = null,
    value: String? = null,
    tag: String? = null,
    icon: AppIcon? = null,
    chevron: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val haptic = rememberHapticAction()
    val inset = settingsInset()
    Row(
        Modifier.fillMaxWidth().heightIn(min = SettingsLayout.RowMinHeight)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.Button) { haptic(); onClick() } else Modifier)
            .padding(horizontal = inset, vertical = SettingsLayout.RowVertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) SettingsLeadingIcon(icon)
            Column(Modifier.weight(1f)) {
                AppText(title, MinType.Subtitle)
                if (!summary.isNullOrBlank()) AppText(summary, MinType.Caption, color = DesignTokens.Secondary)
                if (!value.isNullOrBlank()) AppText(value, MinType.Caption, color = DesignTokens.AccentText)
            }
            if (chevron) {
                Spacer(Modifier.width(MinSpace.sm))
                AppIcon(AppIcon.ChevronRight, Modifier.size(20.dp), color = DesignTokens.Secondary)
            }
        }
    }
}

@Composable
internal fun SettingsLeadingIcon(icon: AppIcon) {
    Box(Modifier.size(SettingsLayout.IconSlot).background(DesignTokens.Surface, CircleShape), contentAlignment = Alignment.Center) {
        AppIcon(icon, Modifier.size(20.dp))
    }
    Spacer(Modifier.width(MinSpace.lg))
}

@Composable
internal fun SettingsLabels(title: String, summary: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        AppText(title, MinType.Subtitle)
        if (!summary.isNullOrBlank()) AppText(summary, MinType.Caption, color = DesignTokens.Secondary)
    }
}

/** Explanatory paragraph inside a settings list. */
@Composable
internal fun SettingsNote(text: String, modifier: Modifier = Modifier) {
    val inset = settingsInset()
    AppText(text, MinType.Body, modifier.padding(horizontal = inset, vertical = MinSpace.sm), color = DesignTokens.Secondary)
}

/** Single-choice picker. The option the user already has stays selectable so the dialog closes on it. */
@Composable
internal fun RadioDialog(title: String, options: List<Pair<String, Boolean>>, tag: String,
                        onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    val haptic = rememberHapticAction()
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { AppText(title, MinType.Title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag(tag)) {
                options.forEachIndexed { index, (label, selected) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) haptic(); onSelect(index) })
                        .testTag("$tag-option-$index").padding(vertical = MinSpace.xs),
                        verticalAlignment = Alignment.CenterVertically) {
                        AppRadioButton(selected, onClick = null)
                        Spacer(Modifier.width(MinSpace.md))
                        AppText(label, MinType.Body, Modifier.weight(1f),
                            color = if (selected) DesignTokens.AccentText else DesignTokens.Ink)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            MinButton(stringResource(R.string.cancel), onDismiss, kind = MinButtonKind.Text, testTag = "$tag-cancel")
        })
}
