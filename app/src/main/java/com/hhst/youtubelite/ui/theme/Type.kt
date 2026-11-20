package com.hhst.youtubelite.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hhst.youtubelite.ui.design.MinType

private fun TextStyle.sized(size: Int, line: Int) = copy(fontSize = size.sp, lineHeight = line.sp)

// Material3 roles resolve to the named Mint scale (ui/design/MinType.kt), so Material components
// and AppText share one set of styles. The settings title keeps its own size token.
val AppTypography = Typography(
    displayLarge = MinType.Display, displayMedium = MinType.Display.sized(22, 28),
    displaySmall = MinType.Display.sized(20, 26), headlineLarge = MinType.Display.sized(20, 26),
    headlineMedium = MinType.Title.sized(SettingsTokens.TitleSize, SettingsTokens.TitleLine),
    headlineSmall = MinType.Title.sized(SettingsTokens.TitleSize, SettingsTokens.TitleLine),
    titleLarge = MinType.Title.sized(SettingsTokens.TitleSize, SettingsTokens.TitleLine),
    titleMedium = MinType.Body.copy(fontWeight = FontWeight.Medium),
    titleSmall = MinType.Caption.sized(SettingsTokens.DetailSize, SettingsTokens.DetailLine),
    bodyLarge = MinType.Body,
    bodyMedium = MinType.Body,
    bodySmall = MinType.Caption.sized(SettingsTokens.DetailSize, SettingsTokens.DetailLine),
    labelLarge = MinType.Body.copy(fontWeight = FontWeight.Medium),
    labelMedium = MinType.Caption,
    labelSmall = MinType.Micro,
)
