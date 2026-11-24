package com.hhst.youtubelite.library.presentation

import com.hhst.youtubelite.core.time.publishedAgeText
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.localLabel
import com.hhst.youtubelite.dearrow.VideoArtwork
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.library.domain.HistoryEntry
import com.hhst.youtubelite.library.domain.LibraryRow
import com.hhst.youtubelite.library.domain.RemovedHistoryEntry
import com.hhst.youtubelite.playlist.domain.PlaylistSummary
import com.hhst.youtubelite.ui.components.DurationBadge
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.PlaylistCard
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.components.ThumbnailProgress
import com.hhst.youtubelite.ui.components.VideoRow
import com.hhst.youtubelite.ui.components.VideoRowSkeleton
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.labelResource
import com.hhst.youtubelite.ui.design.messageCardShadow

/** Widest a single-column library list grows; wider windows center it. */
internal val LibraryContentMaxWidth = 960.dp

@Composable
internal fun LibraryArtwork(asset: String, modifier: Modifier = Modifier) {
    Artwork(asset, modifier)
}

/**
 * Full-page empty or signed-out state. [tag] stays on the scrolling page so pull-to-refresh and
 * the existing tests keep one node per state; the action is the caller's existing action.
 */
@Composable
internal fun LibraryStateMessage(
    title: String, message: String, tag: String, icon: AppIcon = AppIcon.VideoOutline,
    actionLabel: String? = null, onAction: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StateMessage(libraryMessageText(title), message = libraryMessageText(message), icon = icon,
            actionLabel = actionLabel?.let { libraryMessageText(it) }, onAction = onAction, tag = "$tag-message")
    }
}

/** Same page with a caller-drawn action slot; kept for the screens outside this package that use it. */
@Composable
internal fun LibraryMessageCard(title: String, message: String, tag: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StateMessage(libraryMessageText(title), message = libraryMessageText(message), tag = "$tag-message")
        if (action != null) {
            action()
            Spacer(Modifier.height(MinSpace.xxxl))
        }
    }
}

@Composable
internal fun LibraryError(tag: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        StateMessage(stringResource(R.string.ui_something_went_wrong), message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error, actionLabel = stringResource(R.string.retry), onAction = onRetry, tag = "$tag-message")
    }
}

/** Placeholder for the You page: account header, entry tiles and one shelf. */
@Composable
internal fun LibrarySkeleton(tag: String, window: WindowClass = WindowClass.Compact) {
    val inset = MinSpace.pageInset(window)
    Column(Modifier.fillMaxSize().testTag(tag).clearAndSetSemantics {}.padding(horizontal = inset, vertical = MinSpace.lg),
        verticalArrangement = Arrangement.spacedBy(MinSpace.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeletonBox(Modifier.size(56.dp), CircleShape)
            Spacer(Modifier.width(MinSpace.lg))
            Column(Modifier.weight(1f)) {
                SkeletonLine(fraction = 0.5f, height = 18.dp)
                Spacer(Modifier.height(MinSpace.sm))
                SkeletonLine(fraction = 0.3f, height = 12.dp)
            }
        }
        val columns = libraryTileColumns(window, androidx.compose.ui.platform.LocalDensity.current.fontScale)
        repeat(if (columns == 2) 3 else 2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                repeat(columns) { SkeletonBox(Modifier.weight(1f).height(56.dp), MinRadius.Field) }
            }
        }
        SkeletonLine(fraction = 0.35f, height = 18.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
            repeat(columns + 1) { SkeletonBox(Modifier.weight(1f).aspectRatio(16f / 9f), MinRadius.Field) }
        }
    }
}

/** Placeholder for the History and Playlists lists: rows shaped like the loaded rows. */
@Composable
internal fun LibraryListSkeleton(tag: String, window: WindowClass = WindowClass.Compact) {
    Box(Modifier.fillMaxSize().testTag(tag).clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = LibraryContentMaxWidth).fillMaxWidth()
            .padding(horizontal = MinSpace.pageInset(window), vertical = MinSpace.lg),
            verticalArrangement = Arrangement.spacedBy(MinSpace.lg)) {
            SkeletonLine(fraction = 0.3f, height = 18.dp)
            repeat(6) { VideoRowSkeleton(thumbnailWidth = libraryThumbnailWidth(window)) }
        }
    }
}

/**
 * Two tiles per line on phones, three from medium width. At large font scales (>= 1.5) phones use
 * one tile per line so a label never breaks mid-word ("Download/s" at font scale 2.0).
 */
internal fun libraryTileColumns(window: WindowClass, fontScale: Float = 1f): Int = when {
    window != WindowClass.Compact -> 3
    fontScale >= 1.5f -> 1
    else -> 2
}

internal fun libraryThumbnailWidth(window: WindowClass): Dp = if (window == WindowClass.Compact) 160.dp else 200.dp

/** One entry of the You page (History, Playlists, Watch later, ...): a tonal tile, 56dp or taller. */
@Composable
internal fun LibraryRowItem(row: LibraryRow, icon: AppIcon, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(modifier.heightIn(min = 56.dp).clip(MinRadius.Field).background(DesignTokens.Surface)
        .clickable(role = Role.Button, onClick = onOpen)
        .testTag("library-row-${row.name}").padding(horizontal = MinSpace.md, vertical = MinSpace.sm),
        verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, Modifier.size(24.dp), color = DesignTokens.AccentText)
        Spacer(Modifier.width(MinSpace.md))
        AppText(stringResource(row.labelResource), MinType.Label, Modifier.weight(1f), maxLines = 2)
    }
}

/** A plain full-width entry with a leading icon, used below the shelves. */
@Composable
internal fun LibraryLinkRow(label: String, icon: AppIcon, tag: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onOpen).testTag(tag)
        .padding(vertical = MinSpace.sm), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(icon, Modifier.size(24.dp))
        Spacer(Modifier.width(MinSpace.lg))
        AppText(label, MinType.Body, Modifier.weight(1f), maxLines = 2)
        AppIcon(AppIcon.ChevronRight, Modifier.size(20.dp), color = DesignTokens.Secondary)
    }
}

/** Section title inside a list; keeps the text a TalkBack heading. */
@Composable
internal fun LibrarySectionTitle(title: String, modifier: Modifier = Modifier) {
    AppText(title, MinType.Title, modifier.semantics { heading() }, maxLines = 2)
}

@Composable
internal fun HistoryCard(entry: HistoryEntry, thumbnailWidth: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit, onMenu: () -> Unit) {
    VideoRow(
        title = rememberDeArrowTitle(entry.videoId, entry.title),
        metadata = entry.author,
        artwork = entry.artwork,
        onClick = onOpen,
        modifier = modifier.testTag("history-item-${entry.id}"),
        secondaryMetadata = listOf(com.hhst.youtubelite.core.format.viewsText(entry.views), publishedAgeText(entry.published)).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotEmpty),
        durationLabel = entry.duration,
        live = entry.duration == "LIVE",
        progress = entry.progress,
        onMenu = onMenu,
        menuDescription = stringResource(R.string.a11y_more_actions_for, entry.title),
        videoId = entry.videoId,
        thumbnailWidth = thumbnailWidth,
    )
}

/** Playlist row for the Playlists list: cover with the count badge, title, owner and count. */
@Composable
internal fun PlaylistListRow(playlist: PlaylistSummary, thumbnailWidth: Dp, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    Row(modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen).testTag("playlists-item-${playlist.id}"),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(thumbnailWidth).aspectRatio(16f / 9f).clip(MinRadius.Field)) {
            LibraryArtwork(playlist.artwork, Modifier.fillMaxSize())
            val count = playlist.videoCount?.toString() ?: playlist.countLabel
            if (!count.isNullOrBlank()) DurationBadge(count)
        }
        Spacer(Modifier.width(MinSpace.md))
        Column(Modifier.weight(1f).padding(vertical = MinSpace.xs)) {
            AppText(playlist.title, MinType.Subtitle, maxLines = 2)
            val metadata = listOfNotNull(playlist.owner.takeIf(String::isNotBlank)?.let { localLabel(it) }, playlist.countLabel).joinToString(" · ")
            if (metadata.isNotBlank()) AppText(metadata, MinType.Caption, Modifier.padding(top = MinSpace.xs),
                color = DesignTokens.Secondary, maxLines = 2)
        }
        AppIcon(AppIcon.ChevronRight, Modifier.padding(start = MinSpace.sm).size(20.dp), color = DesignTokens.Secondary)
    }
}

@Composable
internal fun PlaylistShelf(playlists: List<PlaylistSummary>, inset: Dp, cardWidth: Dp, onOpen: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("library-playlists-shelf"),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.md), contentPadding = PaddingValues(horizontal = inset)) {
        items(playlists, key = { it.id }) { playlist ->
            PlaylistCard(
                title = playlist.title,
                metadata = localLabel(playlist.owner),
                artwork = playlist.artwork,
                onClick = { onOpen(playlist.id) },
                modifier = Modifier.width(cardWidth).testTag("library-playlist-${playlist.id}"),
                countLabel = playlist.videoCount?.toString() ?: playlist.countLabel,
            )
        }
    }
}

@Composable
internal fun HistoryShelf(entries: List<HistoryEntry>, inset: Dp, cardWidth: Dp, onOpen: (String) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().testTag("library-history-shelf"),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.md), contentPadding = PaddingValues(horizontal = inset)) {
        items(entries, key = { it.id }) { entry ->
            Column(Modifier.width(cardWidth).testTag("library-history-${entry.id}")
                .clickable(role = Role.Button) { onOpen(entry.videoId) }) {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(MinRadius.Field)) {
                    VideoArtwork(entry.videoId, entry.artwork, Modifier.fillMaxSize())
                    if (entry.duration.isNotBlank()) DurationBadge(entry.duration, live = entry.duration == "LIVE")
                    entry.progress?.let { ThumbnailProgress(it) }
                }
                AppText(rememberDeArrowTitle(entry.videoId, entry.title), MinType.Body, Modifier.padding(top = MinSpace.sm), maxLines = 2)
                if (entry.author.isNotBlank()) AppText(entry.author, MinType.Caption, Modifier.padding(top = MinSpace.xxs),
                    color = DesignTokens.Secondary, maxLines = 1)
            }
        }
    }
}

/** A failed optional shelf retries independently while the other shelves stay usable. */
@Composable
internal fun ShelfRetry(message: String, loading: Boolean, modifier: Modifier = Modifier, onRetry: () -> Unit) {
    Row(modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(AppIcon.Error, Modifier.size(20.dp), color = DesignTokens.Danger)
        Spacer(Modifier.width(MinSpace.sm))
        AppText(message, MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
        if (loading) Box(Modifier.size(MinSpace.Touch), contentAlignment = Alignment.Center) { LibrarySpinner() }
        else MinButton(stringResource(R.string.retry), onRetry, kind = MinButtonKind.Tonal)
    }
}

/**
 * Transient prompt after a device-history removal: undo restores that one
 * entry, dismiss drops the record. Anchor-based restore happens in the store,
 * so entries the user changed after the delete are never overwritten.
 */
@Composable
fun HistoryUndoBanner(removed: RemovedHistoryEntry, modifier: Modifier = Modifier,
                      onUndo: () -> Unit, onDismiss: () -> Unit) {
    Row(modifier.widthIn(max = 480.dp).messageCardShadow().clip(MinRadius.Card).background(DesignTokens.Ink).testTag("history-undo")
        .padding(start = MinSpace.lg, end = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        AppText(stringResource(R.string.history_entry_removed), MinType.Body, Modifier.weight(1f).padding(vertical = MinSpace.md),
            color = DesignTokens.Background, maxLines = 3)
        Box(Modifier.heightIn(min = MinSpace.Touch).clip(CircleShape).clickable(role = Role.Button, onClick = onUndo)
            .padding(horizontal = MinSpace.md), contentAlignment = Alignment.Center) {
            AppText(stringResource(R.string.undo), MinType.Label, color = DesignTokens.AccentContainer, maxLines = 1)
        }
        AppIconButton(AppIcon.Close, stringResource(R.string.a11y_close_undo),
            modifier = Modifier.testTag("history-undo-dismiss"), color = DesignTokens.Background) { onDismiss() }
    }
}

/** Indeterminate progress for reads (appends, shelf retries). */
@Composable
internal fun LibrarySpinner(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.a11y_loading)
    androidx.compose.material3.CircularProgressIndicator(modifier.size(24.dp).semantics { contentDescription = label },
        color = DesignTokens.Accent, trackColor = DesignTokens.Ghost, strokeWidth = 2.5.dp)
}

@Composable
internal fun LibraryAppendRow(append: AppendState, onRetry: () -> Unit) {
    when (append) {
        AppendState.Loading -> Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("library-append-loading"),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            LibrarySpinner()
        }
        AppendState.Failed -> Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("library-append-failed"),
            verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(R.string.ui_couldn_t_load_more_entries), MinType.Body, Modifier.weight(1f), color = DesignTokens.Secondary)
            MinButton(stringResource(R.string.retry), onRetry, kind = MinButtonKind.Text)
        }
        AppendState.Idle -> Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun libraryMessageText(text: String): String = when (text) {
    "Don't miss new videos" -> stringResource(R.string.ui_don_t_miss_new_videos)
    "Sign in to see updates from your favorite YouTube channels" -> stringResource(R.string.ui_sign_in_to_see_updates_from_your_favorite_youtube_channels)
    "Keep track of what you watch" -> stringResource(R.string.ui_keep_track_of_what_you_watch)
    "Watch and like videos to see your history here." -> stringResource(R.string.ui_watch_and_like_videos_to_see_your_history_here)
    "Keep your favorite videos together" -> stringResource(R.string.ui_keep_your_favorite_videos_together)
    "Sign in to create and manage playlists." -> stringResource(R.string.ui_sign_in_to_create_and_manage_playlists)
    "No videos" -> stringResource(R.string.ui_no_videos)
    "Videos you watch will show up here." -> stringResource(R.string.ui_videos_you_watch_will_show_up_here)
    "No playlists" -> stringResource(R.string.ui_no_playlists)
    "Playlists you create will show up here." -> stringResource(R.string.ui_playlists_you_create_will_show_up_here)
    "Sign in" -> stringResource(R.string.ui_sign_in)
    else -> text
}
