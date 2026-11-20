package com.hhst.youtubelite.channel.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.home.domain.AppendState
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appDescription

/*
 * Small state pieces shared by the channel, post and subscriptions screens. They only draw:
 * every retry or load is the caller's existing ViewModel action.
 */

/** Indeterminate progress for "more rows are coming"; first loads use skeletons instead. */
@Composable
internal fun LoadingIndicator(modifier: Modifier = Modifier) {
    CircularProgressIndicator(
        modifier.size(24.dp).appDescription(R.string.a11y_loading),
        color = DesignTokens.Accent, trackColor = DesignTokens.Ghost, strokeWidth = 2.5.dp,
    )
}

/** Centers a `StateMessage` and lets it scroll when the window is shorter than the message. */
@Composable
internal fun StatePage(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, content = content,
    )
}

/** Last row of a paged list: progress while appending, a message with Retry after a failure. */
@Composable
internal fun AppendFooter(
    append: AppendState,
    failedMessage: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val tagged = if (tag != null) modifier.testTag(tag) else modifier
    when (append) {
        AppendState.Idle -> Box(tagged.fillMaxWidth().heightIn(min = 1.dp))
        AppendState.Loading -> Box(tagged.fillMaxWidth().heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
        AppendState.Failed -> Row(
            tagged.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = MinSpace.lg, vertical = MinSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppText(failedMessage, MinType.Body, Modifier.weight(1f).padding(end = MinSpace.sm), color = DesignTokens.Secondary)
            MinButton(stringResource(R.string.retry), onRetry, kind = MinButtonKind.Tonal)
        }
    }
}

/**
 * A state that lives inside a page section (one channel tab, a post page): the same parts as the
 * shared `StateMessage` in a smaller size, and the action keeps the tag its section already had.
 */
@Composable
internal fun SectionState(
    title: String,
    tag: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    kind: StateKind = StateKind.Empty,
    icon: AppIcon = if (kind == StateKind.Error) AppIcon.Error else AppIcon.VideoOutline,
    actionLabel: String? = null,
    actionTag: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().testTag(tag).padding(horizontal = MinSpace.xxl, vertical = MinSpace.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).background(if (kind == StateKind.Empty) DesignTokens.AccentContainer else DesignTokens.Surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, Modifier.size(28.dp), color = if (kind == StateKind.Empty) DesignTokens.AccentText else DesignTokens.Danger)
        }
        AppText(title, MinType.Title, Modifier.widthIn(max = 480.dp).padding(top = MinSpace.lg), align = TextAlign.Center)
        if (message != null) AppText(message, MinType.Body, Modifier.widthIn(max = 480.dp).padding(top = MinSpace.xs),
            color = DesignTokens.Secondary, align = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            MinButton(actionLabel, onAction, Modifier.padding(top = MinSpace.md), testTag = actionTag)
        }
    }
}
