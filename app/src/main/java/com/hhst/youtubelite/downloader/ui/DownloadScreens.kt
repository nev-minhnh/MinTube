package com.hhst.youtubelite.downloader.ui
import com.hhst.youtubelite.ui.design.CollapsingAppBar
import com.hhst.youtubelite.ui.design.rememberAppBarCollapseState
import androidx.compose.ui.input.nestedscroll.nestedScroll

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import com.hhst.youtubelite.ui.components.AppAlertDialog as AlertDialog
import com.hhst.youtubelite.ui.components.AppCheckbox as Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.hhst.youtubelite.ui.components.AppLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import com.hhst.youtubelite.ui.components.AppSlider as Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.hhst.youtubelite.ui.components.AppSwitch as Switch
import androidx.compose.material3.Text
import com.hhst.youtubelite.ui.components.AppTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.HapticsController
import com.hhst.youtubelite.downloader.core.DownloadSettings
import com.hhst.youtubelite.downloader.core.DownloadStatus
import com.hhst.youtubelite.downloader.core.DownloadTarget
import com.hhst.youtubelite.downloader.core.RemoveMode
import com.hhst.youtubelite.ui.components.ConstrainedPage
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

sealed class DownloadDest {
    data object List : DownloadDest()
    data class Batch(val batchId: String) : DownloadDest()
    data object Settings : DownloadDest()
    data object History : DownloadDest()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    viewModel: DownloadViewModel,
    initialBatchId: String?,
    newBatchRequests: kotlinx.coroutines.flow.Flow<String> = kotlinx.coroutines.flow.emptyFlow(),
    onClose: () -> Unit,
) {
    var dest by rememberSaveable(stateSaver = Saver(
        save = { d ->
            when (d) {
                DownloadDest.List -> "list"
                is DownloadDest.Batch -> "batch:${d.batchId}"
                DownloadDest.Settings -> "settings"
                DownloadDest.History -> "history"
            }
        },
        restore = { raw ->
            when {
                raw == "settings" -> DownloadDest.Settings
                raw == "history" -> DownloadDest.History
                raw.startsWith("batch:") -> DownloadDest.Batch(raw.removePrefix("batch:"))
                else -> DownloadDest.List
            }
        },
    )) {
        mutableStateOf(
            if (!initialBatchId.isNullOrBlank()) DownloadDest.Batch(initialBatchId) else DownloadDest.List,
        )
    }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val haptics: HapticsController = koinInject()
    var pendingDelete by remember { mutableStateOf<DownloadItemUiState?>(null) }
    // Hoisted so returning from a batch detail restores the list scroll position.
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // Keep one live snapshot while opening details/settings. A new cold Room
    // subscription in each pane briefly replaced existing downloads with an empty state.
    val downloads = remember(viewModel) { viewModel.observeDownloads() }
    val downloadItems = downloads.collectAsStateWithLifecycle<List<DownloadItemUiState>?>(null)
    var filter by rememberSaveable { mutableStateOf(ManagerFilter.ALL) }
    LaunchedEffect(newBatchRequests) {
        newBatchRequests.collect { dest = DownloadDest.Batch(it) }
    }

    fun handle(item: DownloadItemUiState, action: DownloadRowAction) {
        // Rows reference tasks through items: address them by itemId so the
        // coordinator's owned/skipped boundary holds across shared batches.
        val target = when (val id = item.itemId) {
            null -> DownloadTarget.Task(item.taskId)
            else -> DownloadTarget.Item(id)
        }
        if (item.skipped && action != DownloadRowAction.DELETE && action != DownloadRowAction.COPY_ID) {
            return
        }
        if (action in listOf(DownloadRowAction.PAUSE, DownloadRowAction.RESUME, DownloadRowAction.CANCEL, DownloadRowAction.RETRY, DownloadRowAction.REDOWNLOAD))
            haptics.perform(HapticsController.Event.CONFIRM)
        when (action) {
            DownloadRowAction.PAUSE -> viewModel.pause(target)
            DownloadRowAction.RESUME -> viewModel.resume(target)
            DownloadRowAction.CANCEL -> viewModel.cancel(target)
            DownloadRowAction.RETRY -> viewModel.retryFailed(target)
            DownloadRowAction.REDOWNLOAD -> viewModel.redownload(target)
            DownloadRowAction.OPEN -> openUri(context, item.publishedUris.firstOrNull())
            DownloadRowAction.SHARE -> shareUri(context, item.publishedUris.firstOrNull(), item.title)
            DownloadRowAction.COPY_ID -> copyId(context, item.videoId)
            DownloadRowAction.DELETE -> pendingDelete = item
        }
    }

    BackHandler {
        if (dest != DownloadDest.List) dest = DownloadDest.List else onClose()
    }

    val appBar = rememberAppBarCollapseState(dest to filter,
        enabled = dest != DownloadDest.Settings && !downloadItems.value.isNullOrEmpty())
    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(appBar.connection),
        containerColor = DesignTokens.Background,
        contentColor = DesignTokens.Ink,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CollapsingAppBar(appBar) {
                MinTopBar(
                    // Edge to edge: the bar keeps below the status bar (Scaffold pads only the content).
                    modifier = Modifier.windowInsetsPadding(
                        androidx.compose.foundation.layout.WindowInsets.safeDrawing.only(
                            androidx.compose.foundation.layout.WindowInsetsSides.Top + androidx.compose.foundation.layout.WindowInsetsSides.Horizontal)),
                    title = when (dest) {
                        DownloadDest.Settings -> stringResource(R.string.download_settings)
                        DownloadDest.History -> stringResource(R.string.download_history)
                        is DownloadDest.Batch -> stringResource(R.string.download_batch_details)
                        DownloadDest.List -> stringResource(R.string.downloads)
                    },
                    onBack = { if (dest != DownloadDest.List) dest = DownloadDest.List else onClose() },
                    backDescription = stringResource(R.string.navigate_back),
                    tag = "download-topbar",
                ) {
                    if (dest is DownloadDest.List) {
                        AppIconButton(AppIcon.Settings, stringResource(R.string.download_settings),
                            Modifier.testTag("download-settings")) { dest = DownloadDest.Settings }
                    }
                }
            }
        },
    ) { padding ->
        ConstrainedPage {
            when (val current = dest) {
                DownloadDest.List -> DownloadListPane(
                    listState = listState,
                    downloadItems = downloadItems,
                    filter = filter,
                    onFilter = { filter = it },
                    padding = padding,
                    onOpenBatch = { dest = DownloadDest.Batch(it) },
                    onRemove = { item, mode -> viewModel.remove(item.target(), mode) },
                    onAction = ::handle,
                )
                is DownloadDest.Batch -> DownloadBatchPane(
                    batchId = current.batchId,
                    viewModel = viewModel,
                    padding = padding,
                    onAction = ::handle,
                )
                DownloadDest.Settings -> DownloadSettingsPane(
                    viewModel = viewModel,
                    padding = padding,
                )
                DownloadDest.History -> DownloadHistoryPane(
                    viewModel = viewModel,
                    downloadItems = downloadItems,
                    padding = padding,
                    onAction = ::handle,
                    snackbar = snackbar,
                )
            }
        }
    }

    pendingDelete?.let { item ->
        DeleteDialog(
            onDismiss = { pendingDelete = null },
            onConfirm = { mode ->
                val target = when (val id = item.itemId) {
                    null -> DownloadTarget.Task(item.taskId)
                    else -> DownloadTarget.Item(id)
                }
                haptics.perform(HapticsController.Event.CONFIRM)
                viewModel.remove(target, mode)
                pendingDelete = null
            },
        )
    }
}

@Composable
private fun DownloadListPane(
    listState: androidx.compose.foundation.lazy.LazyListState,
    downloadItems: State<List<DownloadItemUiState>?>,
    filter: ManagerFilter,
    onFilter: (ManagerFilter) -> Unit,
    padding: PaddingValues,
    onOpenBatch: (String) -> Unit,
    onRemove: (DownloadItemUiState, RemoveMode) -> Unit,
    onAction: (DownloadItemUiState, DownloadRowAction) -> Unit,
) {
    val loaded = downloadItems.value
    val items = remember(loaded, filter) {
        val phases = DownloadPresentation.managerFilter(filter).phases
        loaded.orEmpty().filter { phases == null || it.phase in phases }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("download-filters")
                .padding(start = DownloadTokens.PageInset, end = DownloadTokens.PageInset, bottom = MinSpace.xs),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
        ) {
            DownloadFilterChip(filter == ManagerFilter.ALL, stringResource(R.string.download_filter_all), compact = true) {
                onFilter(ManagerFilter.ALL)
            }
            DownloadFilterChip(filter == ManagerFilter.IN_PROGRESS, stringResource(R.string.download_filter_in_progress), compact = true) {
                onFilter(ManagerFilter.IN_PROGRESS)
            }
            DownloadFilterChip(filter == ManagerFilter.COMPLETED, stringResource(R.string.download_filter_completed), compact = true) {
                onFilter(ManagerFilter.COMPLETED)
            }
        }
        DownloadHairline()
        if (loaded == null) {
            DownloadsLoading()
        } else if (items.isEmpty()) {
            // The share-to-download entry is explained only on the full empty list, not per filter.
            EmptyDownloads(showShareHint = loaded.isEmpty())
        } else {
            SelectableDownloads(items, listState = listState,
                onOpen = { it.batchId?.let(onOpenBatch) }, onAction = onAction, onRemove = onRemove)
        }
    }
}

/** Download rows in shimmer; held back 150ms so a fast local read never flashes it. */
@Composable
private fun DownloadsLoading() {
    var showSkeleton by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(150)
        showSkeleton = true
    }
    val loading = stringResource(R.string.a11y_loading)
    Column(
        Modifier.fillMaxSize().testTag("download-skeleton").clearAndSetSemantics { contentDescription = loading }
            .padding(horizontal = DownloadTokens.PageInset, vertical = MinSpace.md),
        verticalArrangement = Arrangement.spacedBy(MinSpace.lg),
    ) {
        if (showSkeleton) repeat(6) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.width(120.dp).aspectRatio(DownloadTokens.ThumbAspect), MinRadius.Field)
                Spacer(Modifier.width(MinSpace.md))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                    SkeletonLine(fraction = 0.9f)
                    SkeletonLine(fraction = 0.5f, height = 12.dp)
                    SkeletonLine(fraction = 0.7f, height = 12.dp)
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloads(showShareHint: Boolean = false) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("download-empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateMessage(
            stringResource(R.string.download_empty),
            message = if (showShareHint) stringResource(R.string.library_download_share_hint) else null,
            icon = AppIcon.Download,
            tag = "download-empty-message",
        )
    }
}

/** An emitted null is a missing batch; no emission yet is still loading. */
private data class LoadedBatch(val value: BatchUiState?)

@Composable
private fun DownloadBatchPane(
    batchId: String,
    viewModel: DownloadViewModel,
    padding: PaddingValues,
    onAction: (DownloadItemUiState, DownloadRowAction) -> Unit,
) {
    val batchFlow = remember(viewModel, batchId) { viewModel.observeBatch(batchId).map(::LoadedBatch) }
    val loaded = key(batchId) { batchFlow.collectAsStateWithLifecycle<LoadedBatch?>(null).value }
    val batch = loaded?.value
    val items = batch?.items.orEmpty()
    val stats = batch?.stats
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text(
            text = batch?.name.orEmpty(),
            modifier = Modifier.padding(horizontal = DownloadTokens.PageInset, vertical = MinSpace.md)
                .semantics { heading() },
            style = MinType.Title,
            color = DesignTokens.Ink,
        )
        if (stats != null && stats.total > 0) {
            val completedText = stringResource(R.string.download_batch_progress, stats.completed, stats.total)
            val failedText = if (stats.failed > 0) {
                stringResource(R.string.download_batch_failed_count, stats.failed)
            } else null
            Row(Modifier.padding(horizontal = DownloadTokens.PageInset)) {
                Text(completedText, style = MinType.Caption, color = DesignTokens.Secondary)
                if (failedText != null) Text(" · $failedText", style = MinType.Caption, color = DesignTokens.Danger)
            }
            LinearProgressIndicator(
                progress = { stats.completed.toFloat() / stats.total.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = DownloadTokens.PageInset, vertical = MinSpace.sm)
                    .height(4.dp)
                    .clip(MinRadius.Full),
                color = DesignTokens.Accent,
                trackColor = DesignTokens.Ghost,
            )
        }
        // Batch controls mirror the per-row semantics offered by actionsFor.
        val target = { item: DownloadItemUiState ->
            item.itemId?.let(DownloadTarget::Item) ?: DownloadTarget.Task(item.taskId)
        }
        val pausable = remember(items) { items.filter {
            !it.skipped && it.status in setOf(
                DownloadStatus.RUNNING,
                DownloadStatus.PAUSING,
                DownloadStatus.WAITING_SYSTEM,
            )
        } }
        val resumable = remember(items) { items.filter {
            !it.skipped && it.status in setOf(
                DownloadStatus.PAUSED,
                DownloadStatus.WAITING_NETWORK,
                DownloadStatus.QUEUED,
            )
        } }
        if (items.isNotEmpty() && (pausable.isNotEmpty() || resumable.isNotEmpty())) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = DownloadTokens.PageInset, vertical = MinSpace.xs),
                horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
            ) {
                if (pausable.isNotEmpty()) {
                    MinButton(stringResource(R.string.download_pause_all), { pausable.forEach { viewModel.pause(target(it)) } },
                        kind = MinButtonKind.Tonal, icon = AppIcon.Pause, testTag = "download-pause-all")
                }
                if (resumable.isNotEmpty()) {
                    MinButton(stringResource(R.string.download_resume_all), { resumable.forEach { viewModel.resume(target(it)) } },
                        kind = MinButtonKind.Tonal, icon = AppIcon.PlayFilled, testTag = "download-resume-all")
                }
            }
        }
        DownloadHairline()
        if (loaded == null) {
            DownloadsLoading()
        } else if (items.isEmpty()) {
            EmptyDownloads()
        } else {
            SelectableDownloads(items,
                onOpen = { onAction(it, DownloadRowAction.OPEN) }, onAction = onAction,
                onRemove = { item, mode -> viewModel.remove(item.target(), mode) })
        }
    }

}

@Composable
private fun DownloadSettingsPane(
    viewModel: DownloadViewModel,
    padding: PaddingValues,
) {
    var wifi by remember { mutableStateOf(viewModel.prefs.wifiOnly()) }
    var quality by remember { mutableStateOf(viewModel.prefs.defaultQuality()) }
    var connections by remember { mutableIntStateOf(viewModel.prefs.maxConnections()) }
    val qualities = listOf("144p", "240p", "360p", "480p", "720p", "1080p", "1440p", "2160p")
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(padding)
            .padding(horizontal = DownloadTokens.PageInset),
    ) {
        SettingsRow(
            title = stringResource(R.string.download_wifi_only),
            subtitle = null,
            onClick = { wifi = !wifi; viewModel.setWifiOnly(wifi) },
            trailing = {
                Switch(checked = wifi, onCheckedChange = {
                    wifi = it
                    viewModel.setWifiOnly(it)
                })
            },
        )
        DownloadHairline()
        Text(
            text = stringResource(R.string.download_quality),
            modifier = Modifier.padding(top = MinSpace.lg, bottom = MinSpace.xs).semantics { heading() },
            style = MinType.Subtitle,
            color = DesignTokens.Ink,
        )
        Text(
            text = stringResource(R.string.download_quality_hint),
            style = MinType.Caption,
            color = DesignTokens.Secondary,
            modifier = Modifier.padding(bottom = MinSpace.sm),
        )
        qualities.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = DownloadTokens.MinTouch)
                    .clip(MinRadius.Field)
                    .selectable(selected = quality == option, role = Role.RadioButton) {
                        quality = option
                        viewModel.setDefaultQuality(option)
                    }
                    .padding(horizontal = MinSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(option, Modifier.weight(1f), style = MinType.Body, color = DesignTokens.Ink)
                if (quality == option) AppIcon(AppIcon.Check, Modifier.size(24.dp), color = DesignTokens.AccentText)
                else Spacer(Modifier.size(24.dp))
            }
        }
        DownloadHairline()
        Text(
            text = stringResource(R.string.download_connections, connections),
            modifier = Modifier.padding(top = MinSpace.lg),
            style = MinType.Subtitle,
            color = DesignTokens.Ink,
        )
        Slider(
            value = connections.toFloat(),
            onValueChange = {
                connections = it.toInt().coerceIn(DownloadSettings.MIN_CONNECTIONS, DownloadSettings.MAX_CONNECTIONS)
                viewModel.setMaxConnections(connections)
            },
            valueRange = DownloadSettings.MIN_CONNECTIONS.toFloat()..DownloadSettings.MAX_CONNECTIONS.toFloat(),
            steps = DownloadSettings.MAX_CONNECTIONS - DownloadSettings.MIN_CONNECTIONS - 1,
        )
        Spacer(Modifier.height(MinSpace.xxl))
    }
}

@Composable
private fun DownloadHistoryPane(
    viewModel: DownloadViewModel,
    downloadItems: State<List<DownloadItemUiState>?>,
    padding: PaddingValues,
    onAction: (DownloadItemUiState, DownloadRowAction) -> Unit,
    snackbar: SnackbarHostState,
) {
    val items = downloadItems.value
    val terminal = remember(items) { items.orEmpty().filter(DownloadPresentation::isTerminal) }
    val scope = rememberCoroutineScope()
    var confirmClear by remember { mutableStateOf(false) }
    var deleteFiles by remember { mutableStateOf(false) }
    val clearedMessage = stringResource(R.string.download_history_cleared)
    val failedMessage = stringResource(R.string.download_failed)
    Column(Modifier.fillMaxSize().padding(padding)) {
        MinButton(stringResource(R.string.download_clear_history), { confirmClear = true },
            Modifier.padding(horizontal = MinSpace.sm), kind = MinButtonKind.Text, icon = AppIcon.Delete, danger = true)
        DownloadHairline()
        if (items == null) {
            DownloadsLoading()
        } else if (terminal.isEmpty()) {
            EmptyDownloads()
        } else {
            LazyColumn {
                items(terminal, key = { it.taskId }) { item ->
                    DownloadItemRow(
                        item = item,
                        onOpen = { onAction(item, DownloadRowAction.OPEN) },
                        onAction = { onAction(item, it) },
                    )
                    DownloadHairline()
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            containerColor = DesignTokens.Panel,
            tonalElevation = 0.dp,
            title = { Text(stringResource(R.string.download_clear_history), style = MinType.Title, color = DesignTokens.Ink) },
            text = {
                Column {
                    Text(stringResource(R.string.download_clear_history_message), style = MinType.Body, color = DesignTokens.Secondary)
                    Row(Modifier.padding(top = MinSpace.sm).heightIn(min = DownloadTokens.MinTouch),
                        verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                        Text(stringResource(R.string.download_delete_files), Modifier.padding(start = MinSpace.sm),
                            style = MinType.Body, color = DesignTokens.Ink)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        val outcome = runCatching { viewModel.clearHistory(deleteFiles) }
                        snackbar.showSnackbar(if (outcome.isSuccess) clearedMessage else failedMessage)
                    }
                }) { Text(stringResource(R.string.confirm), style = MinType.Label, color = DesignTokens.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel), style = MinType.Label) }
            },
        )
    }
}


@Composable
private fun SettingsRow(
    title: String,
    subtitle: String?,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DownloadTokens.MinTouch)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = MinSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = MinSpace.md)) {
            Text(title, style = MinType.Body, color = DesignTokens.Ink)
            if (subtitle != null) {
                Text(subtitle, style = MinType.Caption, color = DesignTokens.Secondary)
            }
        }
        trailing?.invoke()
    }
}

@Composable
internal fun DeleteDialog(
    onDismiss: () -> Unit,
    onConfirm: (RemoveMode) -> Unit,
) {
    var files by remember { mutableStateOf(false) }
    val haptic = com.hhst.youtubelite.ui.design.rememberHapticAction()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DesignTokens.Panel,
        tonalElevation = 0.dp,
        title = { Text(stringResource(R.string.download_delete), style = MinType.Title, color = DesignTokens.Ink) },
        text = {
            Row(
                Modifier.fillMaxWidth().heightIn(min = DownloadTokens.MinTouch).clip(MinRadius.Field)
                    .toggleable(value = files, role = Role.Checkbox,
                        onValueChange = { haptic(); files = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = files, onCheckedChange = null)
                Text(stringResource(R.string.download_delete_local_file), Modifier.padding(start = MinSpace.sm),
                    style = MinType.Body, color = DesignTokens.Ink)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(if (files) RemoveMode.RECORD_AND_FILES else RemoveMode.RECORD_ONLY)
            }) { Text(stringResource(R.string.download_delete), style = MinType.Label, color = DesignTokens.Danger) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = MinType.Label) }
        },
    )
}

private fun openUri(context: Context, uri: String?) {
    val parsed = DownloadFileShare.parseOpenable(uri) ?: return
    runCatching { context.startActivity(DownloadFileShare.viewIntent(parsed)) }
}

private fun shareUri(context: Context, uri: String?, title: String) {
    val parsed = DownloadFileShare.parseOpenable(uri) ?: return
    runCatching {
        context.startActivity(
            Intent.createChooser(
                DownloadFileShare.sendIntent(parsed, title),
                title,
            ).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
        )
    }
}

private fun copyId(context: Context, videoId: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("videoId", videoId))
    Toast.makeText(context, context.getString(R.string.copied), Toast.LENGTH_SHORT).show()
}
