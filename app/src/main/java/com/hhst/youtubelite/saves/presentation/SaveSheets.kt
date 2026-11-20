package com.hhst.youtubelite.saves.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.saves.domain.NewPlaylistDraft
import com.hhst.youtubelite.saves.domain.SaveChoice
import com.hhst.youtubelite.saves.domain.SaveDestination
import com.hhst.youtubelite.saves.domain.SavePlaylist
import com.hhst.youtubelite.ui.components.AppCheckbox
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MenuSheetLayout
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appDescription
import com.hhst.youtubelite.ui.design.appPaneTitle
import com.hhst.youtubelite.ui.design.rememberHapticAction

/** Server-driven state for the live save sheet; null [choices] means the list is still loading. */
class SaveSheetLive(
    val choices: List<SaveChoice>?,
    val failed: Boolean,
    val pending: Boolean,
    val onToggle: (SaveChoice) -> Unit,
    val onRetry: () -> Unit,
)

@Composable
fun SaveToPlaylistSheet(
    playlists: List<SavePlaylist>,
    onDismiss: () -> Unit,
    onSelect: (SaveDestination) -> Unit,
    onNewPlaylist: () -> Unit,
    live: SaveSheetLive? = null,
) {
    if (live != null) {
        val choices = live.choices
        val items = listOf(when {
            live.failed -> MenuSheetItem(AppIcon.Refresh, stringResource(R.string.ui_couldn_t_load_playlists_tap_to_retry), "save-menu-retry")
            choices == null -> MenuSheetItem(AppIcon.Refresh, stringResource(R.string.ui_loading), "save-menu-loading")
            else -> MenuSheetItem(AppIcon.Add, stringResource(R.string.ui_new_playlist), "save-menu-new-playlist")
        })
        MenuBottomSheet(
            items = items,
            onDismiss = { if (!live.pending) onDismiss() },
            onSelect = {
                if (live.pending) return@MenuBottomSheet
                when {
                    live.failed -> live.onRetry()
                    choices == null -> Unit
                    else -> onNewPlaylist()
                }
            },
            tag = "save-to-playlist",
            paneTitle = stringResource(R.string.save_to_playlist),
            dismissEnabled = !live.pending,
            actionsEnabled = !live.pending,
            header = {
                // The title row keeps its height while a write is pending, so the rows never move.
                SaveSheetTitle(pending = live.pending)
                if (!live.failed) choices?.forEach { choice ->
                    SaveChoiceRow(choice, enabled = !live.pending) { live.onToggle(choice) }
                }
            },
        )
        return
    }
    val destinations = buildList {
        add(SaveDestination.WatchLater)
        playlists.forEach { add(SaveDestination.Playlist(it.id, it.title)) }
    }
    MenuBottomSheet(
        items = buildList {
            add(MenuSheetItem(AppIcon.Clock, stringResource(R.string.ui_watch_later), "save-menu-watch-later"))
            playlists.forEach {
                add(MenuSheetItem(AppIcon.Playlist, it.title, "save-menu-playlist-${it.id}"))
            }
            add(MenuSheetItem(AppIcon.Add, stringResource(R.string.ui_new_playlist), "save-menu-new-playlist"))
        },
        onDismiss = onDismiss,
        onSelect = { index ->
            if (index < destinations.size) onSelect(destinations[index]) else onNewPlaylist()
        },
        tag = "save-to-playlist",
        paneTitle = stringResource(R.string.save_to_playlist),
        header = { SaveSheetTitle(pending = false) },
    )
}

@Composable
private fun SaveSheetTitle(pending: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).padding(horizontal = MenuSheetLayout.RowStart.dp),
        verticalAlignment = Alignment.CenterVertically) {
        AppText(stringResource(R.string.save_to_playlist), MinType.Title, Modifier.weight(1f).semantics { heading() }, maxLines = 2)
        if (pending) ActionProgress()
    }
}

/** One playlist with its membership as a checkbox; the whole row toggles. */
@Composable
private fun SaveChoiceRow(choice: SaveChoice, enabled: Boolean, onToggle: () -> Unit) {
    val haptic = rememberHapticAction()
    Row(Modifier.fillMaxWidth().heightIn(min = MenuSheetLayout.RowHeight.dp).testTag("save-menu-playlist-${choice.id}")
        .toggleable(value = choice.contains, enabled = enabled, role = Role.Checkbox) { haptic(); onToggle() }
        .padding(horizontal = MenuSheetLayout.RowStart.dp, vertical = MenuSheetLayout.RowVertical.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { AppCheckbox(choice.contains, null, enabled = enabled) }
        Spacer(Modifier.width(MenuSheetLayout.IconGap.dp))
        AppText(choice.title, MinType.Subtitle, Modifier.weight(1f), maxLines = 2)
    }
}

/** The form behind the sheet's "New playlist" row: a title, an optional description, Create. */
@Composable
fun NewPlaylistSheet(
    draft: NewPlaylistDraft,
    onDismiss: () -> Unit,
    onDraftChange: (NewPlaylistDraft) -> Unit,
    onSubmit: () -> Unit,
    pending: Boolean = false,
) {
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        withFrameNanos {}
        runCatching { focus.requestFocus() }
        keyboard?.show()
    }
    DisposableEffect(Unit) {
        onDispose {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    Box(
        Modifier.fillMaxSize().background(DesignTokens.MenuScrim).clickable(enabled = !pending) { onDismiss() }
            .appDescription(R.string.a11y_close_new_playlist),
    )
    BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.padding(bottom = MenuSheetLayout.BottomInset.dp)
                .width(MenuSheetLayout.sheetWidth(maxWidth.value, maxHeight.value).dp)
                .heightIn(max = (maxHeight - MenuSheetLayout.BottomInset.dp).coerceAtLeast(0.dp))
                .clip(RoundedCornerShape(MenuSheetLayout.Corner.dp)).background(DesignTokens.Panel)
                .verticalScroll(rememberScrollState())
                .testTag("new-playlist-sheet").appPaneTitle(R.string.ui_new_playlist),
        ) {
            AppText(stringResource(R.string.ui_new_playlist), MinType.Title,
                Modifier.padding(start = MinSpace.lg, end = MinSpace.lg, top = MinSpace.lg).semantics { heading() }, maxLines = 2)
            AppText(
                stringResource(R.string.ui_private), MinType.Caption,
                Modifier.padding(start = MinSpace.lg, end = MinSpace.lg, top = MinSpace.xxs),
                color = DesignTokens.Secondary,
            )
            SheetField(
                value = draft.title,
                placeholder = stringResource(R.string.player_info_title),
                tag = "new-playlist-title",
                description = stringResource(R.string.ui_playlist_title),
                focus = focus,
                onValue = { if (!pending) onDraftChange(draft.copy(title = it)) },
                readOnly = pending,
                modifier = Modifier.padding(start = MinSpace.lg, end = MinSpace.lg, top = MinSpace.md),
            )
            SheetField(
                value = draft.description,
                placeholder = stringResource(R.string.ui_description_optional),
                tag = "new-playlist-description",
                description = stringResource(R.string.ui_playlist_description),
                onValue = { if (!pending) onDraftChange(draft.copy(description = it)) },
                readOnly = pending,
                modifier = Modifier.padding(start = MinSpace.lg, end = MinSpace.lg, top = MinSpace.sm),
            )
            Row(
                Modifier.fillMaxWidth().padding(start = MinSpace.md, end = MinSpace.md, top = MinSpace.md, bottom = MinSpace.sm),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MinButton(stringResource(R.string.cancel), onDismiss, kind = MinButtonKind.Text, enabled = !pending, testTag = "new-playlist-cancel")
                Spacer(Modifier.width(MinSpace.sm))
                // A playlist needs a title; the pending button shows the write and ignores taps.
                MinButton(stringResource(R.string.ui_create), onSubmit, enabled = draft.title.isNotBlank(), pending = pending,
                    testTag = "new-playlist-create")
            }
        }
    }
}

@Composable
private fun SheetField(
    value: String,
    placeholder: String,
    tag: String,
    description: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    focus: FocusRequester? = null,
    readOnly: Boolean = false,
) {
    BasicTextField(
        value, onValue,
        readOnly = readOnly,
        modifier = modifier.heightIn(min = MinSpace.Touch).fillMaxWidth()
            .clip(MinRadius.Field).background(DesignTokens.Surface)
            .padding(horizontal = MinSpace.lg, vertical = MinSpace.md)
            .then(if (focus != null) Modifier.focusRequester(focus) else Modifier)
            .testTag(tag)
            .semantics { contentDescription = description },
        textStyle = MinType.Body.copy(color = DesignTokens.Ink),
        cursorBrush = SolidColor(DesignTokens.Accent),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) AppText(placeholder, MinType.Body, color = DesignTokens.Secondary)
                inner()
            }
        },
    )
}
