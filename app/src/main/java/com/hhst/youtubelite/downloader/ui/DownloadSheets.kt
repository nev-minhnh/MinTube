package com.hhst.youtubelite.downloader.ui

import com.hhst.youtubelite.ui.components.watchSheetHeight
import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import com.hhst.youtubelite.ui.components.AppAlertDialog as AlertDialog
import com.hhst.youtubelite.ui.components.AppCheckbox as Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import com.hhst.youtubelite.ui.components.AppSwitch as Switch
import androidx.compose.material3.Text
import com.hhst.youtubelite.ui.components.AppTextButton as TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.components.AppSheetHandle
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.downloader.core.BatchSelection
import com.hhst.youtubelite.downloader.core.BatchSnapshot
import com.hhst.youtubelite.downloader.core.DownloadConfig
import com.hhst.youtubelite.downloader.core.DownloadRequest
import com.hhst.youtubelite.downloader.core.DownloadTarget
import com.hhst.youtubelite.downloader.core.EnqueueResult
import com.hhst.youtubelite.downloader.resolve.DownloadCodecs
import com.hhst.youtubelite.downloader.resolve.DownloadSelector
import com.hhst.youtubelite.downloader.resolve.DownloadUnavailableReason
import com.hhst.youtubelite.player.datasource.AudioTrackIdentity
import com.hhst.youtubelite.player.datasource.StreamSelection
import com.hhst.youtubelite.player.datasource.SubtitleSelection
import com.hhst.youtubelite.ui.components.audioTrackLabel
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * Batch rows have no per-video catalog, so the subtitle picker offers common
 * language codes. Language-only keys keep the selector's fallback (human captions
 * first); per-track precision stays on the single-video sheet.
 */
private val BatchSubtitleLanguages =
    listOf("en", "ja", "ko", "zh", "zh-Hant", "de", "fr", "es", "pt", "ru", "tr", "hi", "ar")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleVideoConfirmSheet(
    videoId: String,
    title: String,
    author: String?,
    thumbnailUrl: String?,
    viewModel: DownloadViewModel,
    onDismiss: () -> Unit,
    onSubmitted: (EnqueueResult) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val last = remember(videoId) { viewModel.prefs.lastConfig() }
    var audioOnly by rememberSaveable(videoId) { mutableStateOf(last.audioOnly) }
    var quality by rememberSaveable(videoId) { mutableStateOf(last.videoQuality ?: viewModel.prefs.defaultQuality()) }
    var videoItag by rememberSaveable(videoId) { mutableStateOf<Int?>(null) }
    var more by rememberSaveable(videoId) { mutableStateOf(false) }
    var audioTrack by rememberSaveable(videoId) { mutableStateOf(last.audioTrack.orEmpty()) }
    var audioBitrate by rememberSaveable(videoId) { mutableStateOf(last.audioBitrate ?: 0) }
    var includeSubtitle by rememberSaveable(videoId) { mutableStateOf(last.includeSubtitle) }
    var subtitleLanguage by rememberSaveable(videoId) { mutableStateOf(last.subtitleLanguage.orEmpty()) }
    var includeCover by rememberSaveable(videoId) { mutableStateOf(last.includeCover) }
    var fileName by rememberSaveable(videoId) { mutableStateOf(last.fileNameTemplate.orEmpty()) }
    var attachmentsOnly by rememberSaveable(videoId) { mutableStateOf(last.attachmentsOnly) }
    var catalogState by remember(videoId) { mutableStateOf<DownloadViewModel.CatalogState?>(null) }
    var retry by remember(videoId) { mutableStateOf(0) }
    var picker by remember(videoId) { mutableStateOf<String?>(null) }
    var submitting by remember(videoId) { mutableStateOf(false) }
    LaunchedEffect(videoId, retry) {
        catalogState = null
        val loaded = viewModel.loadCatalog(videoId, fresh = retry > 0)
        currentCoroutineContext().ensureActive()
        if (loaded is DownloadViewModel.CatalogState.Ready && videoItag == null) {
            val options = DownloadPresentation.qualityOptions(loaded.catalog)
            // Last quality is a cap across videos; a new explicit choice is exact.
            if (quality !in options) quality = options.firstOrNull {
                StreamSelection.parseHeight(it) <=
                    StreamSelection.parseHeight(quality)
            } ?: options.lastOrNull().orEmpty()
            videoItag = DownloadPresentation.qualityItag(loaded.catalog, quality)
        }
        catalogState = loaded
    }
    val catalog = (catalogState as? DownloadViewModel.CatalogState.Ready)?.catalog
    val qualities = remember(catalog) { catalog?.let { DownloadPresentation.qualityOptions(it) }.orEmpty() }
    val config = DownloadConfig(
        videoQuality = quality.takeIf { it.isNotBlank() }, videoItagHint = videoItag,
        audioOnly = audioOnly && !attachmentsOnly, audioTrack = audioTrack.takeIf { it.isNotBlank() },
        audioBitrate = audioBitrate.takeIf { audioOnly && !attachmentsOnly && it > 0 },
        includeSubtitle = includeSubtitle, subtitleLanguage = subtitleLanguage.takeIf { it.isNotBlank() },
        includeCover = includeCover, fileNameTemplate = fileName.takeIf { it.isNotBlank() },
        attachmentsOnly = attachmentsOnly,
    )
    val preview = remember(catalog, config) { catalog?.let { viewModel.preview(it, config) } }
    val ready = preview as? DownloadViewModel.PreviewState.Ready
    val bitrates = remember(catalog, audioTrack) {
        catalog?.let { DownloadPresentation.audioBitrates(it, audioTrack.takeIf(String::isNotBlank)) }.orEmpty()
    }
    LaunchedEffect(bitrates) {
        if (audioBitrate > 0 && bitrates.isNotEmpty() && audioBitrate !in bitrates) {
            audioBitrate = bitrates.firstOrNull { it <= audioBitrate } ?: bitrates.last()
        }
    }
    val autoLabel = stringResource(R.string.player_quality_auto)
    val bitrateOptions = listOf("0" to autoLabel) + bitrates.map {
        it.toString() to stringResource(R.string.download_bitrate, (it + 500L) / 1000)
    }
    val qualityOptions = if (audioOnly) bitrateOptions else qualities.map { it to it }
    val qualityKey = if (audioOnly) audioBitrate.toString() else quality
    val selectedQuality = if (audioOnly) {
        bitrateOptions.firstOrNull { it.first == qualityKey }?.second ?: autoLabel
    } else quality.ifBlank { "—" }
    val reason = (catalogState as? DownloadViewModel.CatalogState.Failed)?.reason
        ?: (preview as? DownloadViewModel.PreviewState.Unavailable)?.reason
    val catalogTitle = catalog?.title?.ifBlank { title } ?: title
    val catalogAuthor = catalog?.author ?: author.orEmpty()
    val audioChoices = remember(catalog) { AudioTrackIdentity.choices(catalog?.formats.orEmpty().filter {
        it.audioOnly && DownloadCodecs.audioEnabled(it) &&
            DownloadSelector.isFileStream(it)
    }) }
    val defaultLabel = stringResource(R.string.player_audio_default)
    val audioOptions = listOf("" to defaultLabel) + audioChoices.map { choice ->
        choice.key to audioTrackLabel(choice)
    }
    val selectedAudioKey = if (audioTrack.isBlank()) "" else catalog?.formats?.firstOrNull {
        it.audioOnly && AudioTrackIdentity.matches(it, audioTrack)
    }?.let(AudioTrackIdentity::key) ?: audioTrack
    val offLabel = stringResource(R.string.download_sub_off)
    val subOptions = listOf("" to offLabel) + catalog?.subtitles.orEmpty().map {
        SubtitleSelection.key(it.languageCode, it.autoGenerated) to
            (Locale.forLanguageTag(it.languageCode).getDisplayName(Locale.getDefault()) +
                " · " + stringResource(if (it.autoGenerated) R.string.download_sub_auto else R.string.download_sub_manual))
    }.distinctBy { it.first }
    val selectedAudio = audioOptions.firstOrNull { it.first == selectedAudioKey }?.second ?: stringResource(R.string.download_selection_missing)
    val selectedSub = if (!includeSubtitle) offLabel else subOptions.firstOrNull {
        it.first == subtitleLanguage || (!subtitleLanguage.contains('|') &&
            SubtitleSelection.languageOf(it.first) == subtitleLanguage)
    }?.second ?: stringResource(R.string.download_selection_missing)
    val selectedSubKey = if (!includeSubtitle) "" else subOptions.firstOrNull {
        it.first == subtitleLanguage || (!subtitleLanguage.contains('|') && it.first.isNotBlank() &&
            SubtitleSelection.languageOf(it.first) == subtitleLanguage)
    }?.first ?: subtitleLanguage
    val existing = remember(videoId) { mutableStateOf<DownloadItemUiState?>(null) }
    LaunchedEffect(videoId) { viewModel.observeVideo(videoId).collect { existing.value = it.tasks.firstOrNull() } }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val body: @Composable () -> Unit = {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = DownloadTokens.PageInset)) {
            val scroll = if (embedded) Modifier else Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
            Column(Modifier.fillMaxWidth().then(scroll)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    DownloadThumbnail(thumbnailUrl, Modifier.width(if (landscape) 120.dp else 104.dp))
                    Spacer(Modifier.width(MinSpace.md))
                    Column(Modifier.weight(1f)) { SheetIdentity(catalogTitle, catalogAuthor) }
                }
                Spacer(Modifier.height(8.dp))
                MediaToggle(audioOnly) { audioOnly = it; attachmentsOnly = false }
                if (catalogState == null) {
                    Row(Modifier.fillMaxWidth().padding(vertical = MinSpace.md), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = DesignTokens.Accent,
                            trackColor = DesignTokens.Ghost, strokeWidth = 2.dp)
                        Text(stringResource(R.string.download_catalog_loading), style = MinType.Body, color = DesignTokens.Secondary)
                    }
                }
                val catalogLoading = catalogState == null
                SelectionRow(stringResource(R.string.download_quality), if (catalog == null) "—" else selectedQuality,
                    catalog != null && !attachmentsOnly && (if (audioOnly) bitrates.isNotEmpty() else qualities.isNotEmpty()),
                    loading = catalogLoading) { picker = "quality" }
                SelectionRow(stringResource(R.string.audio_track), selectedAudio, catalog != null && !attachmentsOnly,
                    loading = catalogLoading) { picker = "audio" }
                SelectionRow(stringResource(R.string.subtitles), selectedSub, catalog != null, loading = catalogLoading) { picker = "subtitle" }
                SizeRow(ready?.size?.kind ?: SizeKind.UNKNOWN, ready?.size?.bytes)
                val subtitleMissing = ready?.plan?.subtitleFailure == DownloadUnavailableReason.SUBTITLE_LANGUAGE_UNAVAILABLE
                if (reason != null || subtitleMissing) {
                    Text(unavailableCopy((reason ?: DownloadUnavailableReason.SUBTITLE_LANGUAGE_UNAVAILABLE).name),
                        style = MinType.Body, color = DesignTokens.Danger,
                        modifier = Modifier.padding(vertical = MinSpace.sm))
                }
                if (catalogState is DownloadViewModel.CatalogState.Failed || reason == DownloadUnavailableReason.NO_FILE_STREAMS) {
                    MinButton(stringResource(R.string.retry), { retry++ }, kind = MinButtonKind.Tonal, icon = AppIcon.Refresh,
                        testTag = "download-catalog-retry")
                }
                existing.value?.let { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(phaseString(DownloadPresentation.phaseCopy(item))), Modifier.weight(1f),
                            style = MinType.Caption, color = DesignTokens.Secondary)
                        MinButton(stringResource(R.string.download_redownload), { viewModel.redownload(DownloadTarget.Task(item.taskId)) },
                            kind = MinButtonKind.Text, icon = AppIcon.Refresh)
                    }
                }
                MoreOptionsHandle(more) { more = !more }
                if (more) MoreOptions(includeCover, { includeCover = it }, fileName, { fileName = it },
                    attachmentsOnly, { attachmentsOnly = it; if (it) audioOnly = false })
                Spacer(Modifier.height(12.dp))
            }
            DownloadCapsuleButton(text = stringResource(R.string.download), iconRes = R.drawable.ic_download,
                onClick = {
                    scope.launch {
                        submitting = true
                        try {
                            onSubmitted(viewModel.confirmSingle(DownloadRequest(videoId, catalogTitle.ifBlank { videoId },
                                catalogAuthor.ifBlank { author }, thumbnailUrl, config.copy(
                                    videoItagHint = ready?.plan?.video?.format?.itag ?: ready?.plan?.muxed?.format?.itag,
                                    audioItagHint = ready?.plan?.audio?.format?.itag))))
                        } finally { submitting = false }
                    }
                }, enabled = ready != null && !submitting, modifier = Modifier.fillMaxWidth(),
                contentDescription = stringResource(R.string.download))
            Spacer(Modifier.height(16.dp))
        }
    }
    if (embedded) body() else ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        sheetMaxWidth = DownloadTokens.SheetMaxWidth,
        containerColor = DesignTokens.Panel, contentColor = DesignTokens.Ink,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        tonalElevation = 0.dp, shape = MinRadius.Sheet) {
        Column(Modifier.fillMaxWidth().downloadOptionsSheetHeight()) { AppSheetHandle(); body() }
    }
    picker?.let { kind ->
        val options = when (kind) { "quality" -> qualityOptions; "audio" -> audioOptions; else -> subOptions }
        val selected = when (kind) { "quality" -> qualityKey; "audio" -> selectedAudioKey; else -> selectedSubKey }
        ChoiceDialog(stringResource(when (kind) { "quality" -> R.string.download_quality; "audio" -> R.string.audio_track; else -> R.string.subtitles }),
            options, selected, { picker = null }) { key ->
            when (kind) {
                "quality" -> {
                    if (audioOnly) {
                        audioBitrate = key.toInt()
                    } else {
                        quality = key
                        videoItag = catalog?.let { DownloadPresentation.qualityItag(it, key) }
                    }
                }
                "audio" -> audioTrack = key
                else -> { includeSubtitle = key.isNotBlank(); subtitleLanguage = key }
            }
            picker = null
        }
    }
}

/**
 * The options sheet opens tall instead of being confined below the watch player: about 75% of the
 * window on phones, the full window height (capped at 640dp) on wider windows. Quality, audio track,
 * subtitles and size then show without dragging; the rest stays behind "More options" and the body scrolls.
 */
@Composable
private fun Modifier.downloadOptionsSheetHeight(): Modifier {
    val windowHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp()
    }
    if (windowHeight <= 0.dp) return this
    val compact = com.hhst.youtubelite.ui.design.rememberWindowClass() == com.hhst.youtubelite.ui.design.WindowClass.Compact
    return height(if (compact) windowHeight * 0.75f else minOf(windowHeight, 640.dp))
}

@Composable
private fun SelectionRow(name: String, selected: String, enabled: Boolean, loading: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MinRadius.Field).clickable(enabled = enabled, onClick = onClick)
        .heightIn(min = DownloadTokens.MinTouch).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, Modifier.weight(1f), style = MinType.Body, color = DesignTokens.Ink)
        // While the catalog loads the value shimmers instead of reading as an empty dash.
        if (loading) Box(Modifier.weight(1.2f), contentAlignment = Alignment.CenterEnd) {
            SkeletonBox(Modifier.size(72.dp, 14.dp), MinRadius.Full)
        } else Text(selected, Modifier.weight(1.2f), maxLines = 2, overflow = TextOverflow.Ellipsis,
            style = MinType.Body, color = DesignTokens.Secondary, textAlign = TextAlign.End)
        AppIcon(AppIcon.ChevronRight, Modifier.padding(start = MinSpace.sm).size(20.dp),
            color = if (enabled) DesignTokens.Secondary else DesignTokens.Secondary.copy(alpha = .4f))
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<String, String>>, selected: String,
    onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title, style = MinType.Title, color = DesignTokens.Ink) },
        containerColor = DesignTokens.Panel, tonalElevation = 0.dp,
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                options.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().clip(MinRadius.Field).selectable(selected = selected == key,
                        role = Role.RadioButton, onClick = { onSelect(key) })
                        .heightIn(min = 48.dp).padding(vertical = MinSpace.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), style = MinType.Body,
                            color = if (selected == key) DesignTokens.AccentText else DesignTokens.Ink)
                        if (selected == key) AppIcon(AppIcon.Check, Modifier.padding(start = MinSpace.md).size(24.dp),
                            color = DesignTokens.AccentText)
                        else Spacer(Modifier.width(36.dp))
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), style = MinType.Label) } })
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MinType.Label,
        color = DesignTokens.Secondary,
        modifier = Modifier.padding(top = MinSpace.md, bottom = MinSpace.sm),
    )
}

@Composable
private fun SheetIdentity(title: String, author: String) {
    Text(
        text = title,
        maxLines = DownloadTokens.TITLE_MAX_LINES,
        overflow = TextOverflow.Ellipsis,
        style = MinType.Subtitle,
        color = DesignTokens.Ink,
    )
    if (author.isNotBlank()) {
        Text(
            text = author,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MinType.Caption,
            color = DesignTokens.Secondary,
            modifier = Modifier.padding(top = MinSpace.xxs),
        )
    }
}

@Composable
private fun MediaToggle(audioOnly: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        DownloadFilterChip(!audioOnly, stringResource(R.string.download_video), capsule = true) { onChange(false) }
        DownloadFilterChip(audioOnly, stringResource(R.string.download_audio), capsule = true) { onChange(true) }
    }
}

@Composable
private fun QualityChipRow(quality: String, options: List<String>, onPick: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
    ) {
        options.forEach { option ->
            DownloadFilterChip(option == quality, option) { onPick(option) }
        }
    }
}

@Composable
private fun SizeRow(kind: SizeKind, bytes: Long?) {
    val formatted = bytes?.let { DownloadPresentation.formatBytes(it) }
    val text = when (kind) {
        SizeKind.EXACT -> formatted ?: stringResource(R.string.download_size_unknown)
        SizeKind.ESTIMATE -> formatted?.let { stringResource(R.string.download_size_estimate, it) }
            ?: stringResource(R.string.download_size_unknown)
        SizeKind.UNKNOWN -> stringResource(R.string.download_size_unknown)
    }
    Text(
        text = text,
        style = MinType.Caption,
        color = DesignTokens.Secondary,
        modifier = Modifier.padding(top = MinSpace.md),
    )
}

@Composable
private fun MoreOptionsHandle(open: Boolean, onToggle: () -> Unit) {
    val moreLabel = stringResource(R.string.download_more_options)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DownloadTokens.MinTouch)
            .clip(MinRadius.Field)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = moreLabel },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = moreLabel,
            style = MinType.Label,
            color = DesignTokens.AccentText,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.size(DownloadTokens.MinTouch), contentAlignment = Alignment.Center) {
            AppIcon(AppIcon.ChevronDown, Modifier.size(20.dp).rotate(if (open) 180f else 0f), color = DesignTokens.AccentText)
        }
    }
}

@Composable
private fun MoreOptions(
    includeCover: Boolean,
    onCover: (Boolean) -> Unit,
    fileName: String,
    onFileName: (String) -> Unit,
    attachmentsOnly: Boolean,
    onAttachments: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        OptionSwitch(stringResource(R.string.download_cover), includeCover, onCover)
        OutlinedTextField(
            value = fileName,
            onValueChange = onFileName,
            label = { Text(stringResource(R.string.download_filename), style = MinType.Label) },
            modifier = Modifier.fillMaxWidth().padding(vertical = MinSpace.sm),
            singleLine = true,
            textStyle = MinType.Body,
            shape = MinRadius.Field,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = DesignTokens.Ink, unfocusedTextColor = DesignTokens.Ink,
                focusedBorderColor = DesignTokens.Accent, unfocusedBorderColor = DesignTokens.Border,
                focusedLabelColor = DesignTokens.AccentText, unfocusedLabelColor = DesignTokens.Secondary,
                cursorColor = DesignTokens.Accent,
                focusedContainerColor = DesignTokens.Surface, unfocusedContainerColor = DesignTokens.Surface,
            ),
        )
        OptionSwitch(stringResource(R.string.download_attachments_only), attachmentsOnly, onAttachments)
    }
}

@Composable
private fun OptionSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = DownloadTokens.MinTouch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f).padding(end = MinSpace.md), style = MinType.Body, color = DesignTokens.Ink)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
internal fun unavailableCopy(reasonKey: String?): String {
    val reason = reasonKey?.let { runCatching { DownloadUnavailableReason.valueOf(it) }.getOrNull() }
    return when (reason) {
        DownloadUnavailableReason.LIVE -> stringResource(R.string.download_reason_live)
        DownloadUnavailableReason.PREMIERE_UNSTARTED -> stringResource(R.string.download_reason_premiere)
        DownloadUnavailableReason.NO_FILE_STREAMS -> stringResource(R.string.download_reason_no_streams)
        DownloadUnavailableReason.QUALITY_UNAVAILABLE -> stringResource(R.string.download_reason_quality)
        DownloadUnavailableReason.AUDIO_TRACK_AMBIGUOUS -> stringResource(R.string.download_reason_audio_ambiguous)
        DownloadUnavailableReason.AUDIO_LANGUAGE_UNAVAILABLE -> stringResource(R.string.download_reason_audio_missing)
        DownloadUnavailableReason.SUBTITLE_LANGUAGE_UNAVAILABLE -> stringResource(R.string.download_reason_subtitle_missing)
        DownloadUnavailableReason.CODEC_NOT_ENABLED -> stringResource(R.string.download_reason_codec)
        DownloadUnavailableReason.NETWORK_ERROR -> stringResource(R.string.download_reason_network)
        DownloadUnavailableReason.SESSION_CHANGED -> stringResource(R.string.download_reason_session)
        DownloadUnavailableReason.EXTRACTION_FAILED -> stringResource(R.string.download_reason_extraction)
        null -> stringResource(R.string.download_reason_extraction)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchConfirmSheet(
    snapshot: BatchSnapshot,
    viewModel: DownloadViewModel,
    onDismiss: () -> Unit,
    onSubmitted: (EnqueueResult) -> Unit,
    onRejected: (String) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    embedded: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val reject = viewModel.validateSnapshot(snapshot)
    LaunchedEffect(reject) {
        if (reject != null) onRejected(reject.message)
    }
    if (reject != null) return

    var selected by rememberSaveable { mutableStateOf(snapshot.items.indices.toSet()) }
    val last = viewModel.prefs.lastConfig()
    var audioOnly by rememberSaveable { mutableStateOf(last.audioOnly) }
    var quality by rememberSaveable { mutableStateOf(last.videoQuality ?: viewModel.prefs.defaultQuality()) }
    var includeSubtitle by rememberSaveable { mutableStateOf(last.includeSubtitle) }
    var subtitleLanguage by rememberSaveable { mutableStateOf(last.subtitleLanguage.orEmpty()) }
    var includeCover by rememberSaveable { mutableStateOf(last.includeCover) }
    var attachmentsOnly by rememberSaveable { mutableStateOf(last.attachmentsOnly) }

    val config = DownloadConfig(
        videoQuality = quality,
        audioOnly = audioOnly && !attachmentsOnly,
        includeSubtitle = includeSubtitle,
        subtitleLanguage = subtitleLanguage.takeIf { it.isNotBlank() },
        includeCover = includeCover,
        fileNameTemplate = last.fileNameTemplate,
        attachmentsOnly = attachmentsOnly,
    )
    val downloadLabel = stringResource(R.string.download)
    val bodyScroll = if (embedded) Modifier else Modifier.verticalScroll(rememberScrollState())
    val sheetBody: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxWidth()
                .then(bodyScroll)
                .navigationBarsPadding()
                .padding(horizontal = DownloadTokens.PageInset),
        ) {
            // Name what is being downloaded, as the single-video sheet does with its title.
            if (snapshot.name.isNotBlank()) SheetIdentity(snapshot.name, author = "")
            Text(
                stringResource(R.string.download_batch_meta, snapshot.items.size, snapshot.source.name),
                style = MinType.Caption,
                color = DesignTokens.Secondary,
                modifier = Modifier.padding(top = MinSpace.xxs),
            )
            Row(
                Modifier.fillMaxWidth().heightIn(min = DownloadTokens.MinTouch),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = selected.size == snapshot.items.size,
                    onCheckedChange = {
                        selected = if (it) snapshot.items.indices.toSet() else emptySet()
                    },
                )
                Text(stringResource(R.string.download_select_all), Modifier.weight(1f), style = MinType.Body, color = DesignTokens.Ink)
                Text(
                    stringResource(R.string.download_selected_count, selected.size, snapshot.items.size),
                    style = MinType.Caption,
                    color = DesignTokens.Secondary,
                )
            }
            Column(
                Modifier
                    .heightIn(max = 280.dp)
                    .clip(MinRadius.Field)
                    .verticalScroll(rememberScrollState()),
            ) {
                snapshot.items.forEachIndexed { index, request ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = DownloadTokens.MinTouch),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = index in selected,
                            onCheckedChange = {
                                selected = if (it) selected + index else selected - index
                            },
                        )
                        DownloadThumbnail(request.thumbnailUrl, Modifier.width(64.dp))
                        Spacer(Modifier.width(MinSpace.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                request.title.ifBlank { request.videoId },
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = MinType.Body,
                                color = DesignTokens.Ink,
                            )
                            request.author?.let {
                                Text(
                                    it,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MinType.Caption,
                                    color = DesignTokens.Secondary,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            MediaToggle(audioOnly) { audioOnly = it; attachmentsOnly = false }
            SectionLabel(stringResource(R.string.download_quality))
            QualityChipRow(quality, listOf("144p", "360p", "480p", "720p", "1080p", "1440p", "2160p")) {
                quality = it
            }
            OptionSwitch(stringResource(R.string.subtitles), includeSubtitle) {
                includeSubtitle = it
                if (it && subtitleLanguage.isBlank()) subtitleLanguage = BatchSubtitleLanguages.first()
            }
            if (includeSubtitle) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(MinSpace.sm),
                ) {
                    BatchSubtitleLanguages.forEach { language ->
                        DownloadFilterChip(subtitleLanguage == language, language) { subtitleLanguage = language }
                    }
                }
            }
            OptionSwitch(stringResource(R.string.download_cover), includeCover) { includeCover = it }
            OptionSwitch(stringResource(R.string.download_attachments_only), attachmentsOnly) {
                attachmentsOnly = it
                if (it) audioOnly = false
            }
            Spacer(Modifier.height(12.dp))
            DownloadCapsuleButton(
                text = downloadLabel,
                iconRes = R.drawable.ic_download,
                onClick = {
                    scope.launch {
                        val frozen = snapshot.copy(config = config, items = snapshot.items.map { it.copy(config = config) })
                        val result = viewModel.confirmBatch(frozen, BatchSelection(selected))
                        onSubmitted(result)
                    }
                },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                contentDescription = downloadLabel,
            )
            Spacer(Modifier.height(16.dp))
        }
    }
    if (embedded) {
        sheetBody()
    } else {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            sheetMaxWidth = DownloadTokens.SheetMaxWidth,
            containerColor = DesignTokens.Panel,
            contentColor = DesignTokens.Ink,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            tonalElevation = 0.dp,
            shape = MinRadius.Sheet,
        ) {
            Column(Modifier.fillMaxWidth().watchSheetHeight()) { AppSheetHandle(); sheetBody() }
        }
    }
}
