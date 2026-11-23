package com.hhst.youtubelite.ui.about

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.update.AppRelease
import com.hhst.youtubelite.core.update.AppUpdater
import com.hhst.youtubelite.ui.components.AppAlertDialog
import com.hhst.youtubelite.ui.components.AppLinearProgressIndicator
import com.hhst.youtubelite.ui.components.AppTextButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/** What the About page shows for an in-app update. [Failed] renders in the status line with Retry. */
sealed interface UpdateUiState {
    data object None : UpdateUiState
    data class Available(val current: String, val release: AppRelease) : UpdateUiState
    data class Downloading(val release: AppRelease, val progress: Float) : UpdateUiState
    data class Failed(val release: AppRelease, val message: String) : UpdateUiState
}

/** Dialog for [UpdateUiState.Available] and [UpdateUiState.Downloading]; renders nothing for other states. */
@Composable
fun AboutUpdateDialog(state: UpdateUiState, onInstall: () -> Unit, onLater: () -> Unit, onCancel: () -> Unit) {
    when (state) {
        is UpdateUiState.Available -> {
            val size = Formatter.formatShortFileSize(LocalContext.current, state.release.apkSize)
            val notes = remember(state.release.notes) { AppUpdater.plainNotes(state.release.notes) }
            AppAlertDialog(
                onDismissRequest = onLater,
                modifier = Modifier.testTag("update-dialog"),
                title = { Text(stringResource(R.string.update_available_title)) },
                text = {
                    Column {
                        AppText(stringResource(R.string.update_version_change, state.current, state.release.version),
                            MinType.Body, Modifier.testTag("update-version"))
                        AppText(stringResource(R.string.update_size, size), MinType.Caption,
                            Modifier.testTag("update-size"), color = DesignTokens.Secondary)
                        if (notes.isNotEmpty()) {
                            Spacer(Modifier.height(MinSpace.md))
                            AppText(notes, MinType.Body, Modifier.fillMaxWidth().heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()).testTag("update-notes"))
                        }
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = onInstall, modifier = Modifier.testTag("update-install")) {
                        Text(stringResource(R.string.update_download_install))
                    }
                },
                dismissButton = {
                    AppTextButton(onClick = onLater, modifier = Modifier.testTag("update-later")) {
                        Text(stringResource(R.string.update_later))
                    }
                },
            )
        }
        is UpdateUiState.Downloading -> {
            val percent = (state.progress * 100).toInt().coerceIn(0, 100)
            AppAlertDialog(
                onDismissRequest = {},
                modifier = Modifier.testTag("update-downloading"),
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    Column {
                        AppLinearProgressIndicator({ state.progress }, Modifier.fillMaxWidth().testTag("update-progress"))
                        Spacer(Modifier.height(MinSpace.sm))
                        AppText(stringResource(R.string.update_progress_percent, percent), MinType.Body,
                            Modifier.testTag("update-percent"))
                    }
                },
                confirmButton = {
                    AppTextButton(onClick = onCancel, modifier = Modifier.testTag("update-cancel")) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
        else -> Unit
    }
}
