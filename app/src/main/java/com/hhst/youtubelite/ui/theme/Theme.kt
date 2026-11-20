package com.hhst.youtubelite.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.hhst.youtubelite.ui.design.DarkAppPalette
import com.hhst.youtubelite.ui.design.LightAppPalette
import com.hhst.youtubelite.ui.design.LocalAppColors
import com.hhst.youtubelite.ui.design.LocalMotionScale
import com.hhst.youtubelite.ui.design.readMotionScale

// Specify container roles too: Material defaults otherwise reintroduce purple
// on dialogs, menus and selected controls. Values mirror ui/design/AppPalette.kt.
private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnSurface,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightSecondary,
    onTertiary = LightOnSecondary,
    tertiaryContainer = LightSurfaceVariant,
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceDim = LightSurfaceVariant,
    surfaceBright = LightSurface,
    surfaceContainerLowest = LightBackground,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = LightSurface,
    surfaceContainerHighest = LightSurfaceHighest,
    inverseSurface = DarkSurface,
    inverseOnSurface = DarkOnSurface,
    inversePrimary = DarkPrimary,
    outline = LightOutline,
    outlineVariant = LightSurfaceHighest,
    error = LightError,
    onError = LightOnError,
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnSurface,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkSecondary,
    onTertiary = DarkOnSecondary,
    tertiaryContainer = DarkSurfaceVariant,
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = Color.Transparent,
    surfaceDim = DarkBackground,
    surfaceBright = DarkSurfaceHighest,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurfaceVariant,
    surfaceContainer = DarkSurfaceVariant,
    // M3 dialog/menu fallback; explicit app overlays use AppStyle.Panel.
    surfaceContainerHigh = DarkSurface,
    surfaceContainerHighest = DarkSurfaceHighest,
    inverseSurface = LightSurface,
    inverseOnSurface = LightOnSurface,
    inversePrimary = LightPrimary,
    outline = DarkOutline,
    outlineVariant = DarkSurfaceHighest,
    error = DarkError,
    onError = DarkOnError,
)

/**
 * The app theme. The Material3 scheme, the Mint palette ([LocalAppColors]) and the system motion
 * scale ([LocalMotionScale]) all come from this one function, so every host (activities, the
 * mini-player overlay, tests) resolves the same tokens. [dynamicColor] (API 31+) maps the
 * wallpaper scheme onto the Material roles only; it is off by default.
 */
@Composable
fun AppTheme(
    darkTheme: Boolean = AppAppearance.dark ?: isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    // Re-read on every resume: the system animator scale may change while the process lives.
    val motion = remember { mutableStateOf(readMotionScale(context.contentResolver)) }
    LifecycleResumeEffect(Unit) {
        motion.value = readMotionScale(context.contentResolver)
        onPauseOrDispose { }
    }
    CompositionLocalProvider(
        LocalAppColors provides if (darkTheme) DarkAppPalette else LightAppPalette,
        LocalMotionScale provides motion.value,
    ) {
        com.hhst.youtubelite.ui.design.AppHapticFeedback { MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppStyle.Shapes,
            content = content,
        ) }
    }
}
