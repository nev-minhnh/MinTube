package com.hhst.youtubelite.comments.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/**
 * Empty / error state sized for a sheet or side pane: the full-page `StateMessage` headline is too
 * large for a half-height panel. The caller owns the action; nothing here loads or retries.
 */
@Composable
internal fun PanelStateMessage(
    title: String,
    modifier: Modifier = Modifier,
    icon: AppIcon = AppIcon.Comment,
    error: Boolean = false,
    message: String? = null,
    titleTag: String? = null,
    actionLabel: String? = null,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = MinSpace.xxl, vertical = MinSpace.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(56.dp).background(if (error) DesignTokens.Surface else DesignTokens.AccentContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, color = if (error) DesignTokens.Danger else DesignTokens.AccentText)
        }
        Spacer(Modifier.height(MinSpace.md))
        AppText(
            title, MinType.Subtitle,
            Modifier.widthIn(max = 360.dp).then(if (titleTag != null) Modifier.testTag(titleTag) else Modifier),
            align = TextAlign.Center,
        )
        if (message != null) {
            Spacer(Modifier.height(MinSpace.xs))
            AppText(message, MinType.Body, Modifier.widthIn(max = 360.dp), color = DesignTokens.Secondary, align = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(MinSpace.sm))
            MinButton(actionLabel, onAction, kind = MinButtonKind.Tonal, icon = AppIcon.Refresh, testTag = actionTag)
        }
    }
}

/** Placeholder rows shaped like a comment or chat message: avatar, author line, two body lines. */
@Composable
internal fun MessageRowsSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 5,
    avatar: Dp = 48.dp,
    startInset: Dp = MinSpace.md,
) {
    Column(modifier.fillMaxWidth()) {
        repeat(rows) { index ->
            Row(Modifier.fillMaxWidth().padding(start = startInset, end = MinSpace.xl, top = MinSpace.lg)) {
                SkeletonBox(Modifier.size(avatar), CircleShape)
                Spacer(Modifier.width(MinSpace.sm))
                Column(Modifier.weight(1f).padding(top = MinSpace.xs)) {
                    SkeletonLine(fraction = 0.35f, height = 12.dp)
                    Spacer(Modifier.height(MinSpace.sm))
                    SkeletonLine(fraction = 0.95f)
                    Spacer(Modifier.height(MinSpace.sm))
                    SkeletonLine(fraction = if (index % 2 == 0) 0.7f else 0.5f)
                }
            }
        }
        Spacer(Modifier.height(MinSpace.lg))
    }
}
