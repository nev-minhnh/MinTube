package com.hhst.youtubelite.tv.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage

/** Placeholder for destinations whose TV screen ships in a later phase. Keeps a focusable action. */
@Composable
fun TvComingSoon(destination: TvDestination, onHome: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical),
        contentAlignment = Alignment.Center) {
        TvStateMessage(
            title = stringResource(R.string.tv_coming_soon_title, stringResource(destination.label)),
            message = stringResource(R.string.tv_coming_soon_message),
            icon = destination.icon,
            actionLabel = stringResource(R.string.tv_go_home),
            onAction = onHome,
            actionModifier = Modifier.testTag("tv-coming-soon-home"),
        )
    }
}
