package com.hhst.youtubelite.ui.design

import androidx.activity.compose.setContent

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.*
import com.hhst.youtubelite.R
import androidx.annotation.StringRes
import kotlin.math.ceil

object DesignTokens {
    // Colors resolve through the shared palette so every screen follows one theme entry.
    val Background: Color @Composable get() = LocalAppColors.current.background
    /** Bars, sheets, cards. Same value as `MaterialTheme.colorScheme.surface`. */
    val Panel: Color @Composable get() = LocalAppColors.current.panel
    val Ink: Color @Composable get() = LocalAppColors.current.ink
    val Secondary: Color @Composable get() = LocalAppColors.current.secondary
    /** Grey inset containers (chips, fields, cards); use [Panel] for an overlay's body. */
    val Surface: Color @Composable get() = LocalAppColors.current.surface
    val Ghost: Color @Composable get() = LocalAppColors.current.ghost
    val Border: Color @Composable get() = LocalAppColors.current.border
    val NavigationDivider: Color @Composable get() = LocalAppColors.current.navigationDivider
    val Scrim: Color @Composable get() = LocalAppColors.current.scrim
    val MenuScrim: Color @Composable get() = LocalAppColors.current.menuScrim
    val StateLayer: Color @Composable get() = LocalAppColors.current.stateLayer
    /** Brand accent: fills, icons, text of 18sp or more. Use [AccentText] for small text. */
    val Accent: Color @Composable get() = LocalAppColors.current.accent
    val OnAccent: Color @Composable get() = LocalAppColors.current.onAccent
    val AccentContainer: Color @Composable get() = LocalAppColors.current.accentContainer
    /** Accent tone that keeps 4.5:1 for body-size text on every neutral container. */
    val AccentText: Color @Composable get() = LocalAppColors.current.accentText
    val Danger: Color @Composable get() = LocalAppColors.current.danger
    val OnDanger: Color @Composable get() = LocalAppColors.current.onDanger
    @Deprecated("The brand accent is mint, not red. Use Accent (or Danger for errors and live badges).", ReplaceWith("DesignTokens.Accent"))
    val Red: Color @Composable get() = LocalAppColors.current.accent
    val Link: Color @Composable get() = LocalAppColors.current.link
    /** Body family (Roboto). Titles use [MinFonts.Display]; prefer the named styles in [MinType]. */
    val Font = MinFonts.Body
    val TopBarHeight = 48.dp
    val BottomBarHeight = 56.dp
    val GridGap = 16.dp
    @Composable
    fun text(size: Int = 14, line: Int = 20, weight: FontWeight = FontWeight.Normal, color: Color = Ink) = TextStyle(
        color = color, fontFamily = MinFonts.familyFor(size, weight), fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    )
}

/** The one theme entry: follows the appearance setting; palette and motion come from the shared theme. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val darkTheme = com.hhst.youtubelite.ui.theme.AppAppearance.dark ?: androidx.compose.foundation.isSystemInDarkTheme()
    CompositionLocalProvider(LocalHideShorts provides rememberHideShorts()) {
        com.hhst.youtubelite.ui.theme.AppTheme(darkTheme = darkTheme) { content() }
    }
}

fun androidx.activity.ComponentActivity.setAppContent(content: @Composable () -> Unit) {
    setContent {
        AppTheme {
            Box(Modifier.fillMaxSize().background(DesignTokens.Background)) { content() }
        }
    }
}

/** Draws the message card shadow without platform elevation. */
fun Modifier.messageCardShadow() = drawWithCache {
    // Convert the Gaussian standard deviation to BlurMaskFilter's radius.
    val sigma = 8.dp.toPx()
    val blur = ((sigma - 0.5f) / 0.57735f).coerceAtLeast(0f)
    val padding = ceil(blur * 2).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(ceil(size.width).toInt() + 2 * padding,
        ceil(size.height).toInt() + 2 * padding, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val left = padding.toFloat()
    val top = padding + 4.dp.toPx()
    android.graphics.Canvas(bitmap).drawRoundRect(left, top, left + size.width, top + size.height, 12.dp.toPx(), 12.dp.toPx(), paint)
    val shadow = bitmap.asImageBitmap()
    onDrawBehind { drawImage(shadow, topLeft = androidx.compose.ui.geometry.Offset(-left, -left)) }
}

@Composable
fun AppText(
    text: String, modifier: Modifier = Modifier, size: Int = 14, line: Int = 20,
    weight: FontWeight = FontWeight.Normal, color: Color = DesignTokens.Ink,
    maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null,
    exactLineHeight: TextUnit? = null,
) {
    BasicText(text, modifier, style = DesignTokens.text(size, line, weight, color).copy(textAlign = align ?: TextAlign.Start,
        lineHeight = exactLineHeight ?: line.sp),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Text in a named [MinType] style, e.g. `AppText(title, MinType.Title)`. */
@Composable
fun AppText(
    text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = DesignTokens.Ink,
    maxLines: Int = Int.MAX_VALUE, align: TextAlign? = null,
) {
    BasicText(text, modifier, style = style.copy(color = color, textAlign = align ?: TextAlign.Start),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

fun Modifier.headerShadow() = drawWithCache {
    val blur = ((1.dp.toPx() - 0.5f) / 0.57735f).coerceAtLeast(0.01f)
    val padding = ceil(blur * 2).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(ceil(size.width).toInt() + 2 * padding,
        ceil(size.height).toInt() + 2 * padding + 4.dp.roundToPx(), android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33000000
        maskFilter = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    val origin = padding.toFloat()
    android.graphics.Canvas(bitmap).drawRect(origin + 2.dp.toPx(), origin + 6.dp.toPx(),
        origin + size.width - 2.dp.toPx(), origin + size.height + 2.dp.toPx(), paint)
    val shadow = bitmap.asImageBitmap()
    onDrawBehind { drawImage(shadow, topLeft = androidx.compose.ui.geometry.Offset(-origin, -origin)) }
}

/** Shorts shelf marker: the filled Shorts glyph in the brand accent. */
@Composable
fun ShortsLogo(modifier: Modifier = Modifier) {
    AppIcon(AppIcon.ShortsFilled, modifier, color = DesignTokens.Accent)
}

/** Localized content description for plain tap targets; keeps a11y labels out of code strings. */
@Composable
fun Modifier.appDescription(@StringRes id: Int, vararg args: Any): Modifier {
    val description = stringResource(id, *args)
    return semantics { contentDescription = description }
}

@Composable
fun Modifier.appPaneTitle(@StringRes id: Int, vararg args: Any): Modifier {
    val title = stringResource(id, *args)
    return semantics { paneTitle = title }
}

@Composable
fun AppIcon(icon: AppIcon, modifier: Modifier = Modifier, color: Color = DesignTokens.Ink) {
    val paths = remember(icon) { icon.paths.map { raw ->
        val path = PathParser().parsePathString(raw).toPath().asAndroidPath()
        if (icon.sourceUnit != 24f) {
            // Map a non-24 source viewport (e.g. viewBox "0 -960 960 960") into 24 units:
            val matrix = android.graphics.Matrix()
            matrix.postScale(24f / icon.sourceUnit, 24f / icon.sourceUnit)
            matrix.postTranslate(0f, -icon.sourceTop * 24f / icon.sourceUnit)
            path.transform(matrix)
        }
        path
    } }
    val mirrored = icon.mirrorsInRtl && LocalLayoutDirection.current == LayoutDirection.Rtl
    Spacer(modifier.size(24.dp).drawWithCache {
        // Cache at the displayed pixel size so vector edges retain anti-aliasing on every renderer.
        val bitmap = android.graphics.Bitmap.createBitmap(ceil(size.width).toInt().coerceAtLeast(1),
            ceil(size.height).toInt().coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            if (icon.strokeWidth > 0f) {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = icon.strokeWidth
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
            }
        }
        val canvas = android.graphics.Canvas(bitmap).apply { scale(size.width / 24f, size.height / 24f) }
        paths.forEach { canvas.drawPath(it, paint) }
        val raster = bitmap.asImageBitmap()
        onDrawBehind {
            if (mirrored) scale(scaleX = -1f, scaleY = 1f) { drawImage(raster) } else drawImage(raster)
        }
    })
}

@Composable
fun AppIconButton(
    icon: AppIcon, description: String, modifier: Modifier = Modifier,
    diameter: Dp = 48.dp, background: Color = Color.Transparent, color: Color = DesignTokens.Ink,
    enabled: Boolean = true, pending: Boolean = false, onClick: () -> Unit,
) {
    val interactions = rememberAppInteractions()
    val haptic = rememberHapticAction()
    Box(modifier.size(diameter).clip(CircleShape)
        .then(if (background != Color.Transparent) Modifier.background(background) else Modifier)
        .appStateLayer(interactions, diameter / 2)
        .hoverable(interactions, enabled = enabled)
        .clickable(interactionSource = interactions, indication = null, enabled = enabled, role = Role.Button, onClick = { haptic(); onClick() })
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        if (pending) ActionProgress(color = color)
        else AppIcon(icon, color = if (enabled) color else color.copy(alpha = 0.4f))
    }
}

/** The MinTube mark: mint rounded square with the notched play triangle. */
@Composable
fun MinTubeMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.ic_mintube_mark), contentDescription = stringResource(R.string.a11y_litube), modifier = modifier)
}

/** Mark plus wordmark. Follows the system font scale like every other text. */
@Composable
fun MinTubeBrand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        MinTubeMark(Modifier.size(28.dp))
        Spacer(Modifier.width(8.dp))
        AppText(stringResource(R.string.app_name), MinType.Wordmark, maxLines = 1)
    }
}
