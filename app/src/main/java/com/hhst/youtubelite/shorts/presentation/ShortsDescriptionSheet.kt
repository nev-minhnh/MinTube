package com.hhst.youtubelite.shorts.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.player.surface.PlayerModalSheet
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

@Composable
internal fun ShortsDescriptionSheet(
    title: String,
    author: String?,
    description: String,
    onDismiss: () -> Unit,
) {
    PlayerModalSheet(onDismiss = onDismiss) {
        Column(
            Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState())
                .padding(start = MinSpace.xl, end = MinSpace.xl, bottom = MinSpace.xxl),
        ) {
            AppText(title, MinType.Subtitle, Modifier.semantics { heading() })
            author?.takeIf { it.isNotBlank() }?.let {
                AppText(it, MinType.Caption, Modifier.padding(top = MinSpace.xs), color = DesignTokens.Secondary)
            }
            AppText(description, MinType.Body, Modifier.padding(top = MinSpace.md).testTag("shorts-description"))
        }
    }
}
