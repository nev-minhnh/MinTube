package com.hhst.youtubelite.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity

/**
 * A [Box] that also acts as the window for the content inside it.
 *
 * Mint screens take their window class, feed columns and navigation form from
 * [LocalConfiguration] (the real window), as Material window size classes do. A test that
 * simulates a 390x844 phone with `requiredSize` inside the 1600x1200 suite window must
 * therefore report that size as the configuration too, or the screen lays out for the
 * host window. When the box matches the host window this changes nothing.
 */
@Composable
fun TestWindowBox(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier, contentAlignment = contentAlignment) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val base = LocalConfiguration.current
            val density = LocalDensity.current
            val width = (constraints.maxWidth / density.density).toInt()
            val height = (constraints.maxHeight / density.density).toInt()
            val configuration = remember(base, width, height, density.fontScale) {
                Configuration(base).apply {
                    screenWidthDp = width
                    screenHeightDp = height
                    smallestScreenWidthDp = minOf(width, height)
                    orientation = if (width > height) Configuration.ORIENTATION_LANDSCAPE
                        else Configuration.ORIENTATION_PORTRAIT
                    fontScale = density.fontScale
                }
            }
            CompositionLocalProvider(LocalConfiguration provides configuration) {
                Box(Modifier.fillMaxSize(), contentAlignment = contentAlignment) { content() }
            }
        }
    }
}
