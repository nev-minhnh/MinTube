package com.hhst.youtubelite.ui.extension

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.diagnostics.AppLog
import com.hhst.youtubelite.diagnostics.DiagnosticOperation
import com.hhst.youtubelite.diagnostics.DiagnosticOutcome
import com.hhst.youtubelite.downloader.core.DownloadPrefs
import com.hhst.youtubelite.extension.ExtensionManager
import com.hhst.youtubelite.extension.SettingsBackup
import com.hhst.youtubelite.extension.SettingsBackupData
import com.hhst.youtubelite.settings.presentation.SettingsGroup
import com.hhst.youtubelite.settings.presentation.SettingsNote
import com.hhst.youtubelite.settings.presentation.SettingsPageFrame
import com.hhst.youtubelite.settings.presentation.SettingsRow
import com.hhst.youtubelite.ui.components.AppAlertDialog
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinType
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun SettingsDataScreen(
    onClose: () -> Unit,
    manager: ExtensionManager = koinInject(),
    downloadPrefs: DownloadPrefs = koinInject(),
    onReloadPages: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<SettingsBackupData?>(null) }
    var importDone by remember { mutableStateOf(false) }
    val fileOperations = remember { arrayOfNulls<DiagnosticOperation>(2) }

    BackHandler { onClose() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val operation = fileOperations[0].also { fileOperations[0] = null }
        if (uri == null) {
            operation?.finish(DiagnosticOutcome.CANCELLED, "file_picker_cancelled")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val written = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        it.write(SettingsBackup.export(manager, downloadPrefs).toByteArray())
                    } != null
                }.getOrDefault(false)
            }
            operation?.finish(if (written) DiagnosticOutcome.SUCCESS else DiagnosticOutcome.FAILURE,
                if (written) "settings_saved" else "file_write_failed")
            Toast.makeText(
                context,
                if (written) R.string.settings_export_done else R.string.settings_export_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** Reads at most [maxChars]; null when oversized or unreadable. */
    suspend fun readBoundedText(context: android.content.Context, uri: android.net.Uri,
        maxChars: Int = 512 * 1024): String? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val out = StringBuilder()
                val reader = java.io.InputStreamReader(stream, Charsets.UTF_8)
                val buf = CharArray(8192)
                while (true) {
                    val n = reader.read(buf)
                    if (n < 0) break
                    out.append(buf, 0, n)
                    if (out.length > maxChars) return@runCatching null
                }
                out.toString()
            }
        }.getOrNull()
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val operation = fileOperations[1].also { fileOperations[1] = null }
        if (uri == null) {
            operation?.finish(DiagnosticOutcome.CANCELLED, "file_picker_cancelled")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val json = readBoundedText(context, uri)
            val backup = json?.let { SettingsBackup.parse(it) }
            operation?.finish(if (backup == null) DiagnosticOutcome.FAILURE else DiagnosticOutcome.SUCCESS,
                if (backup == null) "settings_read_failed" else "settings_read",
                fields = mapOf("bytes" to (json?.length ?: -1)))
            if (backup == null) {
                Toast.makeText(context, R.string.settings_import_failed, Toast.LENGTH_SHORT).show()
            } else {
                pendingImport = backup
            }
        }
    }

    SettingsPageFrame(
        title = stringResource(R.string.settings_backup_restore),
        onBack = onClose,
        screenTag = "settings-data-screen",
        topBarTag = "settings-data-topbar",
        pageTag = "settings-data-page",
    ) {
        SettingsNote(stringResource(R.string.account_backup_note), Modifier.testTag("settings-data-note"))
        SettingsGroup(stringResource(R.string.settings_backup_restore))
        SettingsRow(
            title = stringResource(R.string.settings_export),
            summary = stringResource(R.string.settings_export_summary),
            tag = "settings-data-export",
            icon = AppIcon.Share,
            onClick = {
                fileOperations[0]?.finish(DiagnosticOutcome.SUPERSEDED, "picker_reopened")
                fileOperations[0] = AppLog.operation(AppLog.Category.STORAGE, "settings_export")
                exportLauncher.launch("litube-settings.json")
            },
        )
        SettingsRow(
            title = stringResource(R.string.settings_import),
            summary = stringResource(R.string.settings_import_summary),
            tag = "settings-data-import",
            icon = AppIcon.Download,
            onClick = {
                fileOperations[1]?.finish(DiagnosticOutcome.SUPERSEDED, "picker_reopened")
                fileOperations[1] = AppLog.operation(AppLog.Category.STORAGE, "settings_import_read")
                importLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
        )
    }

    pendingImport?.let { backup ->
        AppAlertDialog(
            onDismissRequest = { pendingImport = null },
            modifier = Modifier.testTag("settings-data-confirm"),
            title = { AppText(stringResource(R.string.settings_import_confirm_title), MinType.Title) },
            text = {
                AppText(stringResource(R.string.settings_import_confirm_message), MinType.Body, color = DesignTokens.Secondary)
            },
            confirmButton = {
                MinButton(
                    stringResource(R.string.confirm),
                    onClick = {
                        SettingsBackup.apply(backup, manager, downloadPrefs)
                        pendingImport = null
                        importDone = true
                    },
                    testTag = "settings-data-confirm-apply",
                )
            },
            dismissButton = {
                MinButton(stringResource(R.string.cancel), { pendingImport = null }, kind = MinButtonKind.Text)
            },
        )
    }

    if (importDone) {
        AppAlertDialog(
            onDismissRequest = { importDone = false },
            modifier = Modifier.testTag("settings-data-done"),
            title = { AppText(stringResource(R.string.settings_import_done), MinType.Title) },
            text = {
                AppText(stringResource(R.string.settings_import_reload_message), MinType.Body, color = DesignTokens.Secondary)
            },
            confirmButton = {
                MinButton(stringResource(R.string.settings_reload_now), { importDone = false; onReloadPages() },
                    kind = MinButtonKind.Tonal)
            },
            dismissButton = {
                MinButton(stringResource(R.string.settings_reload_later), { importDone = false }, kind = MinButtonKind.Text)
            },
        )
    }
}
