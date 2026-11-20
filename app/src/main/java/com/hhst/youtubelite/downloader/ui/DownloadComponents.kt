package com.hhst.youtubelite.downloader.ui

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import com.hhst.youtubelite.ui.components.AppCheckbox
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import com.hhst.youtubelite.ui.components.AppLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.HapticsController
import com.hhst.youtubelite.downloader.core.DownloadPhase
import com.hhst.youtubelite.downloader.core.DownloadStatus
import com.hhst.youtubelite.downloader.resolve.DownloadUnavailableReason
import com.hhst.youtubelite.ui.YoutubeThumb
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import org.koin.compose.koinInject

/** Real thumbnail with the placeholder box as the load-failure fallback. */
@Composable
fun DownloadThumbnail(url: String?, modifier: Modifier = Modifier) {
    YoutubeThumb(
        url = url,
        modifier = modifier
            .aspectRatio(DownloadTokens.ThumbAspect)
            .clip(MinRadius.Field)
            .background(DesignTokens.Surface),
    )
}

/** The primary action of the download sheets: a full-width Mint primary pill (Accent fill). */
@Composable
fun DownloadCapsuleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
    iconRes: Int = 0,
) {
    val desc = contentDescription ?: text
    val haptic = com.hhst.youtubelite.ui.design.rememberHapticAction()
    Button(
        onClick = { haptic(); onClick() },
        enabled = enabled,
        modifier = modifier
            .heightIn(min = DownloadTokens.MinTouch)
            .semantics { this.contentDescription = desc },
        shape = MinRadius.Full,
        colors = ButtonDefaults.buttonColors(
            containerColor = DesignTokens.Accent,
            contentColor = DesignTokens.OnAccent,
            disabledContainerColor = DesignTokens.Surface,
            disabledContentColor = DesignTokens.Secondary,
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        contentPadding = PaddingValues(horizontal = MinSpace.xl, vertical = MinSpace.sm),
    ) {
        if (iconRes != 0) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(MinSpace.sm))
        }
        Text(text, style = MinType.Label, color = LocalContentColor.current)
    }
}

/**
 * A Mint chip: a 32dp pill inside a 48dp target, AccentContainer when selected. It keeps the
 * tab role and `selected` semantics that the manager filters and the media toggle expose.
 */
@Composable
fun DownloadFilterChip(
    selected: Boolean,
    label: String,
    compact: Boolean = false,
    capsule: Boolean = false,
    onClick: () -> Unit,
) {
    val haptics: HapticsController = koinInject()
    Box(
        Modifier.heightIn(min = DownloadTokens.MinTouch).selectable(selected = selected, role = Role.Tab,
            onClick = { if (!selected) haptics.perform(HapticsController.Event.SELECTION); onClick() }),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.heightIn(min = if (compact) 32.dp else 36.dp)
            .clip(MinRadius.Full)
            .background(if (selected) DesignTokens.AccentContainer else DesignTokens.Surface)
            .padding(horizontal = if (capsule) MinSpace.lg else MinSpace.md, vertical = MinSpace.xs),
            contentAlignment = Alignment.Center) {
            Text(label, style = MinType.Label,
                color = if (selected) DesignTokens.AccentText else DesignTokens.Ink)
        }
    }
}

@Composable
fun DownloadHairline() {
    HorizontalDivider(thickness = 1.dp, color = DesignTokens.Border)
}

@Composable
fun DownloadItemRow(
    item: DownloadItemUiState,
    onOpen: () -> Unit,
    onAction: (DownloadRowAction) -> Unit,
    onLongClick: (() -> Unit)? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    var moreExpanded by remember(item.itemId ?: item.taskId) { mutableStateOf(false) }
    val haptic = com.hhst.youtubelite.ui.design.rememberHapticAction()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DownloadTokens.MinTouch)
            .combinedClickable(onClick = { if (selectionMode) haptic(); onOpen() }, onLongClick = onLongClick)
            .semantics { if (selectionMode) this.selected = selected }
            .background(if (selected) DesignTokens.Ghost else androidx.compose.ui.graphics.Color.Transparent)
            .padding(start = DownloadTokens.PageInset, end = MinSpace.xs, top = MinSpace.sm, bottom = MinSpace.sm),
    ) {
        val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadThumbnail(item.thumbnailUrl, Modifier.width(if (stacked) 96.dp else 120.dp))
                Spacer(Modifier.width(MinSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        minLines = 1,
                        modifier = overflowMarquee(),
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = MinType.Body,
                        color = DesignTokens.Ink,
                    )
                    Text(
                        text = item.author.orEmpty(),
                        modifier = overflowMarquee(),
                        minLines = 1,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        style = MinType.Caption,
                        color = DesignTokens.Secondary,
                    )
                    if (!stacked) DownloadRowDetails(item, stacked = false)
                }
                if (selectionMode) AppCheckbox(selected, { onOpen() }, Modifier.size(DownloadTokens.MinTouch))
                val actions = DownloadPresentation.actionsFor(item)
                val moreLabel = stringResource(R.string.download_more_actions)
                if (!selectionMode) Box {
                    AppIconButton(AppIcon.More, moreLabel,
                        Modifier.width(DownloadTokens.RowActionWidth).height(DownloadTokens.MinTouch)) { moreExpanded = true }
                    DropdownMenu(
                        expanded = moreExpanded,
                        onDismissRequest = { moreExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp, max = 280.dp).heightIn(max = 360.dp),
                        shape = MinRadius.Field,
                        containerColor = DesignTokens.Panel,
                        shadowElevation = 8.dp,
                        tonalElevation = 0.dp,
                    ) {
                        actions.forEach { action ->
                            val label = actionLabel(action)
                            // Delete is the one destructive entry; it reads in Danger.
                            val tint = if (action == DownloadRowAction.DELETE) DesignTokens.Danger else DesignTokens.Ink
                            DropdownMenuItem(
                                text = { Text(label, style = MinType.Body, color = tint) },
                                leadingIcon = {
                                    Icon(painterResource(actionIcon(action)), contentDescription = null,
                                        modifier = Modifier.size(DownloadTokens.Icon), tint = tint)
                                },
                                onClick = {
                                    moreExpanded = false
                                    onAction(action)
                                },
                                modifier = Modifier.heightIn(min = DownloadTokens.MinTouch)
                                    .semantics { contentDescription = label },
                            )
                        }
                    }
                }
            }
            if (stacked) {
                Spacer(Modifier.height(MinSpace.sm))
                DownloadRowDetails(item, stacked = true)
            }
        }
    }
}

@Composable
private fun DownloadRowDetails(item: DownloadItemUiState, stacked: Boolean) {
    val phase = DownloadPresentation.phaseCopy(item)
    val phaseText = stringResource(phaseString(phase))
    val quality = item.qualityLabel.orEmpty()
    val size = item.expectedBytes?.let { DownloadPresentation.formatBytes(it) }.orEmpty()
    val kind = when {
        item.attachmentsOnly -> stringResource(R.string.download_kind_attachments)
        item.audioOnly -> stringResource(R.string.download_audio)
        else -> ""
    }
    val progressText = DownloadPresentation.progressText(item.status, item.progressBytes, item.expectedBytes)
    val failure = DownloadPresentation.failureReason(item)?.let { failureCopy(it) }
    // A finished or failed row reads its state in color; everything else stays secondary.
    val phaseColor = when (phase) {
        PhaseCopy.COMPLETE -> DesignTokens.AccentText
        PhaseCopy.FAILED, PhaseCopy.FILE_NOT_FOUND -> DesignTokens.Danger
        else -> DesignTokens.Secondary
    }
    Column(Modifier.fillMaxWidth()) {
        val meta = listOf(kind, quality, size).filter { it.isNotBlank() }.joinToString(" · ")
        // Preserve normal row height across status changes; stacked rows grow with their text.
        if (stacked && meta.isNotBlank()) Text(
            text = meta,
            modifier = overflowMarquee(),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MinType.Caption,
            color = DesignTokens.Secondary,
        )
        Text(
            text = if (stacked) phaseText else listOf(meta, phaseText).filter { it.isNotBlank() }.joinToString(" · "),
            modifier = overflowMarquee(),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MinType.Caption,
            color = if (stacked) phaseColor else DesignTokens.Secondary,
        )
        if (!stacked || failure != null || !progressText.isNullOrBlank()) Text(
            text = failure ?: progressText.orEmpty(),
            modifier = overflowMarquee(),
            minLines = 1,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style = MinType.Caption,
            color = if (failure != null) DesignTokens.Danger else DesignTokens.Secondary,
        )
        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp)) {
            if (DownloadPresentation.isActivePhase(item.phase) &&
                item.status != DownloadStatus.FAILED && item.status != DownloadStatus.CANCELLED &&
                (item.status == DownloadStatus.RUNNING || item.progressBytes > 0L)) {
                val fraction = if (item.phase == DownloadPhase.TRANSFER) {
                    DownloadPresentation.progressFraction(item.progressBytes, item.expectedBytes)
                } else null
                val barModifier = Modifier.fillMaxWidth().height(3.dp).clip(MinRadius.Full)
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = barModifier,
                        color = DesignTokens.Accent, trackColor = DesignTokens.Ghost)
                } else if (item.status == DownloadStatus.RUNNING) {
                    LinearProgressIndicator(modifier = barModifier,
                        color = DesignTokens.Accent, trackColor = DesignTokens.Ghost)
                }
            }
        }
    }
}

@Composable
private fun overflowMarquee(): Modifier = if (com.hhst.youtubelite.ui.design.LocalMotionScale.current > 0f) {
    Modifier.fillMaxWidth().basicMarquee(iterations = 3, initialDelayMillis = 1_500, velocity = 24.dp)
} else Modifier.fillMaxWidth()

@Composable
internal fun actionLabel(action: DownloadRowAction): String = stringResource(
    when (action) {
        DownloadRowAction.PAUSE -> R.string.action_pause
        DownloadRowAction.RESUME -> R.string.download_resume
        DownloadRowAction.CANCEL -> R.string.cancel
        DownloadRowAction.RETRY -> R.string.retry
        DownloadRowAction.REDOWNLOAD -> R.string.download_redownload
        DownloadRowAction.OPEN -> R.string.download_open
        DownloadRowAction.SHARE -> R.string.share
        DownloadRowAction.COPY_ID -> R.string.download_copy_id
        DownloadRowAction.DELETE -> R.string.download_delete
    },
)

@Composable
private fun failureCopy(reason: String): String = when {
    reason == "ENOSPC" -> stringResource(R.string.download_reason_storage)
    reason == "ENOMEM" || reason.startsWith("Failed to allocate") -> stringResource(R.string.download_reason_memory)
    reason == "403" || reason == "http-403" -> stringResource(R.string.download_reason_forbidden)
    reason.startsWith("429:") || reason == "http-429" -> stringResource(R.string.download_reason_rate_limit)
    DownloadUnavailableReason.entries.any { it.name == reason } ->
        unavailableCopy(reason)
    else -> reason
}

internal fun actionIcon(action: DownloadRowAction): Int = when (action) {
    DownloadRowAction.PAUSE -> R.drawable.ic_pause
    DownloadRowAction.RESUME -> R.drawable.ic_play
    DownloadRowAction.RETRY, DownloadRowAction.REDOWNLOAD -> R.drawable.ic_replay
    DownloadRowAction.OPEN -> R.drawable.ic_play
    DownloadRowAction.SHARE -> R.drawable.ic_share
    DownloadRowAction.COPY_ID -> R.drawable.ic_copy
    DownloadRowAction.CANCEL -> R.drawable.ic_close
    DownloadRowAction.DELETE -> R.drawable.ic_delete
}

internal fun phaseString(phase: PhaseCopy): Int = when (phase) {
    PhaseCopy.RESOLVING -> R.string.download_phase_resolve
    PhaseCopy.TRANSFERRING -> R.string.download_phase_transfer
    PhaseCopy.WAITING_PROCESS -> R.string.download_phase_waiting_process
    PhaseCopy.WAITING_NETWORK -> R.string.download_waiting_network
    PhaseCopy.MERGING -> R.string.download_phase_merging
    PhaseCopy.SAVING -> R.string.download_phase_saving
    PhaseCopy.COMPLETE -> R.string.download_complete
    PhaseCopy.FILE_NOT_FOUND -> R.string.download_file_not_found
    PhaseCopy.PAUSED -> R.string.download_paused
    PhaseCopy.FAILED -> R.string.download_failed
    PhaseCopy.CANCELLED -> R.string.download_cancelled
    PhaseCopy.QUEUED -> R.string.download_queued
}
