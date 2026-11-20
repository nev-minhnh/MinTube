package com.hhst.youtubelite.home.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.HomeAction
import com.hhst.youtubelite.home.domain.HomeContent
import com.hhst.youtubelite.home.domain.HomeNavigationRequest
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.appDescription

/**
 * Nothing to show. On a first visit ([firstVisit]) the page points at search, Explore and voice
 * search; a live catalog chip that came back empty ([catalog]) offers the existing refresh.
 */
@Composable
internal fun EmptyHome(content: HomeContent.Empty, firstVisit: Boolean, catalog: Boolean, onAction: (HomeAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home-empty"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(
            title = content.title.ifBlank { stringResource(R.string.home_empty_title) },
            message = if (catalog) null else content.message.ifBlank { stringResource(R.string.home_empty_message) },
            kind = StateKind.Empty,
            icon = if (firstVisit) AppIcon.Search else AppIcon.VideoOutline,
            actionLabel = when {
                firstVisit -> stringResource(R.string.a11y_search)
                catalog -> stringResource(R.string.retry)
                else -> null
            },
            onAction = { onAction(if (firstVisit) HomeAction.OpenSearch else HomeAction.Refresh) },
            secondaryActionLabel = if (firstVisit) stringResource(R.string.a11y_explore) else null,
            onSecondaryAction = { onAction(HomeAction.Navigate(HomeNavigationRequest.Explore)) },
            tag = "empty-message",
        )
        if (firstVisit) MinButton(stringResource(R.string.a11y_voice_search),
            { onAction(HomeAction.Navigate(HomeNavigationRequest.VoiceSearch)) },
            kind = MinButtonKind.Text, icon = AppIcon.Voice, testTag = "home-empty-voice")
        Spacer(Modifier.height(MinSpace.xxl))
    }
}

@Composable
internal fun ErrorHome(onAction: (HomeAction) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("home-error"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(
            title = stringResource(R.string.ui_something_went_wrong),
            message = stringResource(R.string.ui_please_try_again),
            kind = StateKind.Error,
            actionLabel = stringResource(R.string.retry),
            onAction = { onAction(HomeAction.Refresh) },
            tag = "home-error-message",
        )
    }
}

/** First-load placeholder on the feed grid: same columns, insets and card anatomy as the real cards. */
@Composable
internal fun HomeSkeleton(window: WindowClass, topInset: Dp) {
    val inset = MinSpace.pageInset(window)
    LazyVerticalGrid(GridCells.Fixed(window.feedColumns), Modifier.fillMaxSize().testTag("home-skeleton").clearAndSetSemantics {},
        contentPadding = PaddingValues(start = inset, end = inset, top = topInset + MinSpace.sm, bottom = MinSpace.xxl),
        horizontalArrangement = Arrangement.spacedBy(MinSpace.lg), verticalArrangement = Arrangement.spacedBy(MinSpace.xxl),
        userScrollEnabled = false) {
        items(12) { index ->
            Column(Modifier.testTag("skeleton-card-$index")) {
                SkeletonBox(Modifier.fillMaxWidth().aspectRatio(16f / 9f).testTag("skeleton-thumb-$index"), MinRadius.Card)
                Row(Modifier.padding(top = MinSpace.md)) {
                    SkeletonBox(Modifier.size(36.dp).testTag("skeleton-avatar-$index"), CircleShape)
                    Spacer(Modifier.width(MinSpace.md))
                    Column(Modifier.weight(1f)) {
                        SkeletonLine(fraction = 0.9f, height = 16.dp)
                        Spacer(Modifier.height(MinSpace.sm))
                        SkeletonLine(fraction = 0.55f, height = 12.dp)
                    }
                }
            }
        }
    }
}

/** Quiet secondary pill. Other features still import it; new Home code uses `MinButton`. */
@Composable
internal fun PillButton(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(CircleShape).background(DesignTokens.Surface).clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        AppText(label, weight = FontWeight.Medium)
    }
}

/** Indeterminate 32dp ring: accent arc on the Ghost track, one turn every 800ms. */
@Composable
internal fun LoadingSpinner() {
    val rotation by rememberInfiniteTransition(label = "loading").animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)), label = "rotation",
    )
    val track = DesignTokens.Ghost
    val arc = DesignTokens.Accent
    Canvas(Modifier.size(32.dp).appDescription(R.string.a11y_loading)) {
        val stroke = 2.dp.toPx()
        val style = Stroke(stroke)
        drawCircle(track, radius = size.minDimension / 2 - stroke / 2, style = style)
        drawArc(arc, rotation + 225f, 90f, false,
            topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), style = style)
    }
}
