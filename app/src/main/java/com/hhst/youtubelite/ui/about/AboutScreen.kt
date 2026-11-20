package com.hhst.youtubelite.ui.about

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.hhst.youtubelite.ui.components.AppTopAppBar as TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.components.ConstrainedPage
import com.hhst.youtubelite.ui.theme.SettingsTokens
import com.hhst.youtubelite.ui.theme.settingsAppBarHeight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(label: String, version: String, onClose: () -> Unit, busy: Boolean, status: String,
    onSource: () -> Unit, onUpdate: () -> Unit, onRelease: () -> Unit, onClear: () -> Unit, onExport: () -> Unit,
    onSave: () -> Unit = {}, onUpstream: () -> Unit = {}) {
    BackHandler { onClose() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.about), style = MaterialTheme.typography.titleLarge) },
            expandedHeight = settingsAppBarHeight(),
            navigationIcon = { IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.navigate_back)) } })
    }) { padding ->
        ConstrainedPage(Modifier.padding(padding), maxWidth = 720.dp) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(SettingsTokens.GroupGap)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_mintube_mark), label, Modifier.size(48.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(label, style = com.hhst.youtubelite.ui.design.MinType.Title)
                        Text(version, fontSize = 12.sp, lineHeight = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.about_fork_notice), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).testTag("about-fork-notice"))
                AboutRow(stringResource(R.string.about_upstream), onClick = onUpstream, tag = "about-upstream")
                AboutRow(stringResource(R.string.about_source), onClick = onSource)
                AboutRow(stringResource(R.string.about_check_update), !busy, onUpdate)
                AboutRow(stringResource(R.string.about_releases), onClick = onRelease)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                AboutRow(stringResource(R.string.about_clear_cache), !busy, onClear)
                AboutRow(stringResource(R.string.about_export_logs), !busy, onExport)
                AboutRow(stringResource(R.string.about_save_logs), !busy, onSave)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
                Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp).testTag("about-credits"))
                if (busy) Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                    com.hhst.youtubelite.home.presentation.LoadingSpinner()
                }
                if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, enabled: Boolean = true, onClick: () -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, lineHeight = 22.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .38f))
    }
}
