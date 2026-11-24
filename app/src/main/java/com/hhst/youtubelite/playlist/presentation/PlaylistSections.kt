package com.hhst.youtubelite.playlist.presentation

import com.hhst.youtubelite.core.time.publishedAgeText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.localLabel
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.library.presentation.LibrarySpinner
import com.hhst.youtubelite.playlist.domain.PlaylistAction
import com.hhst.youtubelite.playlist.domain.PlaylistEntry
import com.hhst.youtubelite.playlist.domain.PlaylistEnumeration
import com.hhst.youtubelite.playlist.domain.PlaylistUiState
import com.hhst.youtubelite.ui.components.DurationBadge
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appStateLayer
import com.hhst.youtubelite.ui.design.rememberAppInteractions
import com.hhst.youtubelite.ui.design.rememberHapticAction

/**
 * Owner line of the header. A device-stored list has no channel to jump to and no
 * artwork-as-avatar: its owner renders as plain text, never a clickable identity row.
 */
@Composable
internal fun PlaylistOwner(owner: String, artwork: String, local: Boolean, onOpen: () -> Unit) {
    if (local) {
        AppText(localLabel(owner), MinType.Label, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary, maxLines = 1)
    } else {
        Row(Modifier.heightIn(min = MinSpace.Touch).clip(MinRadius.Badge).clickable(role = Role.Button, onClick = onOpen),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(DesignTokens.Surface)) { PlaylistArtwork(artwork, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(MinSpace.sm))
            AppText(owner, MinType.Label, Modifier.padding(end = MinSpace.sm), maxLines = 1)
        }
    }
}

/**
 * Play all and Shuffle as visible buttons, then save, share and the overflow (download lives
 * there, as before). [stretch] lets the two main buttons share a phone-width row; with a large
 * font or a wide window everything wraps instead of clipping.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlaylistButtons(onAction: (PlaylistAction) -> Unit, stretch: Boolean, local: Boolean = false, empty: Boolean = false) {
    val split = stretch && LocalDensity.current.fontScale <= 1.3f
    val secondary: @Composable () -> Unit = {
        // Saving/sharing a device-stored list has no server to talk to.
        if (!local) {
            AppIconButton(AppIcon.Bookmark, stringResource(R.string.a11y_save_playlist), background = DesignTokens.Surface) { onAction(PlaylistAction.Save) }
            AppIconButton(AppIcon.Share, stringResource(R.string.a11y_share_playlist), background = DesignTokens.Surface) { onAction(PlaylistAction.Share) }
        }
        AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_playlist_actions), background = DesignTokens.Surface) { onAction(PlaylistAction.OpenMenu) }
    }
    if (split) Column(Modifier.fillMaxWidth().padding(top = MinSpace.md).testTag("playlist-actions"),
        verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        if (!empty) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
            PlaylistPill(stringResource(R.string.ui_play_all), AppIcon.PlayFilled, primary = true, tag = "playlist-play-all",
                modifier = Modifier.weight(1f)) { onAction(PlaylistAction.PlayAll) }
            PlaylistPill(stringResource(R.string.playback_end_shuffle), AppIcon.Shuffle, primary = false, tag = "playlist-shuffle-button",
                modifier = Modifier.weight(1f)) { onAction(PlaylistAction.Shuffle) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) { secondary() }
    } else FlowRow(Modifier.fillMaxWidth().padding(top = MinSpace.md).testTag("playlist-actions"),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm), verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        if (!empty) {
            MinButton(stringResource(R.string.ui_play_all), { onAction(PlaylistAction.PlayAll) }, icon = AppIcon.PlayFilled, testTag = "playlist-play-all")
            MinButton(stringResource(R.string.playback_end_shuffle), { onAction(PlaylistAction.Shuffle) }, kind = MinButtonKind.Tonal,
                icon = AppIcon.Shuffle, testTag = "playlist-shuffle-button")
        }
        secondary()
    }
}

/** A MinButton-styled pill that fills the width its [modifier] gives it (MinButton wraps its content). */
@Composable
private fun PlaylistPill(text: String, icon: AppIcon, primary: Boolean, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val container = if (primary) DesignTokens.Accent else DesignTokens.AccentContainer
    val content = if (primary) DesignTokens.OnAccent else DesignTokens.Ink
    val interactions = rememberAppInteractions()
    val haptic = rememberHapticAction()
    Box(modifier.heightIn(min = MinSpace.Touch).testTag(tag)
        .clickable(interactionSource = interactions, indication = null, role = Role.Button) { haptic(); onClick() },
        contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clip(CircleShape).background(container).appStateLayer(interactions, 20.dp)
            .padding(horizontal = MinSpace.lg, vertical = MinSpace.sm),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            AppIcon(icon, Modifier.size(18.dp), color = content)
            Spacer(Modifier.width(MinSpace.sm))
            AppText(text, MinType.Label, color = content, maxLines = 1)
        }
    }
}

/**
 * One video of the playlist, drawn like the shared `VideoRow`. It stays a private composable
 * because the cover, the title and the overflow each carry their own test tag and click target.
 */
@Composable
internal fun PlaylistVideoRow(entry: PlaylistEntry, onAction: (PlaylistAction) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(start = PlaylistInset, end = MinSpace.xs)
        .padding(vertical = MinSpace.sm).testTag("playlist-entry-${entry.id}")) {
        // A large font keeps a readable title column by giving the thumbnail less of the row.
        val thumbnailWidth = if (LocalDensity.current.fontScale > 1.3f) minOf(160.dp, maxWidth * 0.3f) else 160.dp
        val open = { onAction(PlaylistAction.OpenVideo(entry.id)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(thumbnailWidth, thumbnailWidth * 9f / 16f).clip(MinRadius.Field).background(DesignTokens.Surface)
                .clickable(role = Role.Button, onClick = open).testTag("playlist-entry-cover-${entry.id}")) {
                PlaylistArtwork(entry.artwork, Modifier.fillMaxSize())
                if (entry.duration.isNotBlank()) DurationBadge(entry.duration, live = entry.duration == "LIVE")
            }
            Spacer(Modifier.width(MinSpace.md))
            Column(Modifier.weight(1f).padding(top = MinSpace.xxs).clickable(role = Role.Button, onClick = open)) {
                AppText(entry.title, MinType.Body, Modifier.fillMaxWidth().testTag("playlist-entry-title-${entry.id}"), maxLines = 2)
                if (entry.channel.isNotBlank()) AppText(entry.channel, MinType.Caption, Modifier.padding(top = MinSpace.xs),
                    color = DesignTokens.Secondary, maxLines = 1)
                val metadata = listOf(com.hhst.youtubelite.core.format.viewsText(entry.views), publishedAgeText(entry.age)).filter(String::isNotBlank).joinToString(" · ")
                if (metadata.isNotEmpty()) AppText(metadata, MinType.Caption, color = DesignTokens.Secondary, maxLines = 1)
            }
            AppIconButton(AppIcon.More, stringResource(R.string.a11y_more_actions_for, entry.title),
                modifier = Modifier.testTag("playlist-entry-menu-${entry.id}")) {
                onAction(PlaylistAction.OpenVideoMenu(entry.id))
            }
        }
    }
}

/** Tail of the list: full-enumeration progress (Play all / Shuffle / Download), then paging state. */
@Composable
internal fun PlaylistAppendRow(state: PlaylistUiState, onAction: (PlaylistAction) -> Unit) {
    val row = Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = PlaylistInset, end = MinSpace.xs)
    when {
        state.enumeration is PlaylistEnumeration.Running -> Row(row.testTag("playlist-enumeration"), verticalAlignment = Alignment.CenterVertically) {
            LibrarySpinner()
            AppText(stringResource(R.string.ui_playlist_loading_count, state.enumeration.loaded), MinType.Body,
                Modifier.weight(1f).padding(start = MinSpace.md), color = DesignTokens.Secondary)
            MinButton(stringResource(R.string.cancel), { onAction(PlaylistAction.CancelEnumeration) }, kind = MinButtonKind.Text)
        }
        state.enumeration is PlaylistEnumeration.Failed -> Row(row.testTag("playlist-enumeration-failed"), verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(R.string.ui_couldn_t_load_the_full_playlist), MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
            MinButton(stringResource(R.string.retry), { onAction(PlaylistAction.RetryEnumeration) }, kind = MinButtonKind.Text)
        }
        state.append == AppendState.Loading -> Row(row.testTag("playlist-append-loading"),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LibrarySpinner()
        }
        state.append == AppendState.Failed -> Row(row.testTag("playlist-append-failed"), verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(R.string.ui_couldn_t_load_more_entries), MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
            MinButton(stringResource(R.string.retry), { onAction(PlaylistAction.LoadNext) }, kind = MinButtonKind.Text)
        }
        else -> Spacer(Modifier.height(1.dp))
    }
}
