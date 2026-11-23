package com.hhst.youtubelite.tv.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val TvColorScheme = darkColorScheme(
    primary = TvColors.Accent,
    onPrimary = TvColors.OnAccent,
    primaryContainer = TvColors.AccentContainer,
    onPrimaryContainer = TvColors.Ink,
    secondary = TvColors.Accent,
    onSecondary = TvColors.OnAccent,
    background = TvColors.Background,
    onBackground = TvColors.Ink,
    surface = TvColors.Panel,
    onSurface = TvColors.Ink,
    surfaceVariant = TvColors.Surface,
    onSurfaceVariant = TvColors.Secondary,
    inverseSurface = TvColors.Ink,
    inverseOnSurface = TvColors.Background,
    error = TvColors.Danger,
    border = TvColors.Accent,
    borderVariant = TvColors.Ghost,
    scrim = TvColors.Scrim,
)

/** Dark-only TV theme. Every TV screen composes inside it. */
@Composable
fun TvTheme(content: @Composable () -> Unit) {
    TvUiScale.ensureLoaded(androidx.compose.ui.platform.LocalContext.current)
    val scale = TvUiScale.current
    val base = androidx.compose.ui.platform.LocalDensity.current
    // Interface size: a smaller density draws every dp and sp of the TV UI proportionally smaller.
    val density = if (scale == 1f) base else androidx.compose.ui.unit.Density(base.density * scale, base.fontScale)
    CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides density) {
        MaterialTheme(colorScheme = TvColorScheme) {
            CompositionLocalProvider(LocalContentColor provides TvColors.Ink, content = content)
        }
    }
}

/** Theme plus the #0A0A0A canvas, for screens and tests. */
@Composable
fun TvCanvas(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    TvTheme {
        Box(modifier.fillMaxSize().background(TvColors.Background)) { content() }
    }
}
