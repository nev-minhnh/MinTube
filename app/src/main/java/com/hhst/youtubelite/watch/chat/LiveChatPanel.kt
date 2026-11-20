package com.hhst.youtubelite.watch.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.comments.domain.CommentsLayout
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.PanelTransition
import com.hhst.youtubelite.ui.design.rememberBottomSheetDrag
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.comments.presentation.MessageRowsSkeleton
import com.hhst.youtubelite.comments.presentation.PanelStateMessage
import com.hhst.youtubelite.watch.domain.WatchLayout
import kotlin.math.roundToInt
import com.hhst.youtubelite.ui.design.appDescription
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R

/** Live chat on live watch pages: same dock/bottom-sheet geometry as the comments panel. */
@Composable
fun LiveChatPanel(
    state: LiveChatUiState,
    onAction: (LiveChatAction) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val width = maxWidth.value
        val height = maxHeight.value
        PanelTransition(fromBottom = !WatchLayout.sidebar(width, height), onDismiss = onClose) { panelMotion, close ->
        if (WatchLayout.sidebar(width, height)) {
            Row(Modifier.fillMaxSize().testTag("live-chat-rail")) {
                Spacer(Modifier.weight(1f).fillMaxHeight())
                LiveChatSurface(state, onAction, close, compact = false, listState = listState,
                    modifier = Modifier.width(WatchLayout.sidebarWidth(width).dp).fillMaxHeight()
                        .padding(top = WatchLayout.TopBarHeight.dp).then(panelMotion))
            }
        } else {
            val top = CommentsLayout.sheetTop(
                topBar = WatchLayout.TopBarHeight,
                playerHeight = WatchLayout.playerHeight(width, height),
                windowHeight = height,
            ).roundToInt().toFloat()
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier.fillMaxSize()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { close() }
                        .appDescription(R.string.a11y_close_live_chat)
                        .testTag("live-chat-scrim"),
                )
                LiveChatSurface(state, onAction, close, compact = true, listState = listState,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .height((height - top).coerceAtLeast(0f).dp).then(panelMotion))
            }
        }
        }
    }
}

@Composable
private fun LiveChatSurface(
    state: LiveChatUiState,
    onAction: (LiveChatAction) -> Unit,
    onClose: () -> Unit,
    compact: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val shape = if (compact) MinRadius.Sheet else RectangleShape
    val title = stringResource(if (state.replay) R.string.chat_replay else R.string.chat_live)
    val drag = rememberBottomSheetDrag(enabled = compact, onDismiss = onClose)
    Column(
        modifier.fillMaxSize().then(drag.panelModifier).background(DesignTokens.Panel, shape)
            .semantics { paneTitle = title }
            .testTag("live-chat"),
    ) {
        Column(Modifier.fillMaxWidth().then(drag.handleModifier).testTag("live-chat-drag-header")) {
            if (compact) {
                Box(Modifier.fillMaxWidth().height(CommentsLayout.HandleStripHeight.dp), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(CommentsLayout.HandleWidth.dp).height(CommentsLayout.HandleHeight.dp)
                        .background(DesignTokens.Ink.copy(alpha = .15f), MinRadius.Full))
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = MinSpace.lg, end = MinSpace.xs),
                verticalAlignment = Alignment.CenterVertically) {
                if (!state.replay && !state.ended) {
                    // Live marker: decorative, the pane title already names the panel.
                    Box(Modifier.size(8.dp).background(DesignTokens.Danger, CircleShape))
                    Spacer(Modifier.width(MinSpace.sm))
                }
                AppText(title, MinType.Title, Modifier.weight(1f).testTag("live-chat-title"), maxLines = 1)
                AppIconButton(AppIcon.Close, stringResource(R.string.a11y_close_live_chat), Modifier.testTag("live-chat-close"), onClick = onClose)
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val content = state.content) {
                LiveChatContent.Loading -> Box(Modifier.fillMaxSize().clipToBounds().testTag("live-chat-loading")) {
                    MessageRowsSkeleton(rows = 8, avatar = 28.dp, startInset = MinSpace.lg)
                }
                LiveChatContent.Failed -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("live-chat-failed"),
                    contentAlignment = Alignment.Center) {
                    PanelStateMessage(
                        title = stringResource(R.string.ui_couldn_t_load_the_chat),
                        icon = AppIcon.Error,
                        error = true,
                        message = stringResource(R.string.ui_please_try_again),
                        actionLabel = stringResource(R.string.retry),
                        actionTag = "live-chat-retry",
                        onAction = { onAction(LiveChatAction.Retry) },
                    )
                }
                is LiveChatContent.Ready -> {
                    if (content.messages.isEmpty()) {
                        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("live-chat-empty"),
                            contentAlignment = Alignment.Center) {
                            PanelStateMessage(
                                title = if (state.replay) stringResource(R.string.chat_replay_empty) else stringResource(R.string.ui_say_hello_in_the_chat),
                            )
                        }
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("live-chat-list"),
                        contentPadding = PaddingValues(vertical = MinSpace.sm)) {
                        items(content.messages, key = { it.id }) { message ->
                            Row(Modifier.fillMaxWidth().padding(horizontal = MinSpace.lg, vertical = 6.dp)
                                .testTag("live-chat-message")) {
                                Artwork(message.photo, Modifier.size(28.dp).clip(CircleShape))
                                Spacer(Modifier.width(MinSpace.md))
                                Column(Modifier.weight(1f)) {
                                    AppText(message.author, MinType.Caption.copy(fontWeight = FontWeight.Medium),
                                        color = DesignTokens.Secondary, maxLines = 1)
                                    Spacer(Modifier.height(MinSpace.xxs))
                                    AppText(message.text, MinType.Body)
                                }
                            }
                        }
                    }
                    // Stick to the bottom while the reader is already near it.
                    var previousLastIndex by remember(listState) { mutableIntStateOf(-1) }
                    LaunchedEffect(content.messages.lastOrNull()?.id) {
                        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                        val follow = previousLastIndex < 0 || last >= previousLastIndex - 2
                        previousLastIndex = content.messages.lastIndex
                        if (content.messages.isNotEmpty() && follow) {
                            listState.scrollToItem(content.messages.size - 1)
                        }
                    }
                }
            }
        }
        when {
            state.ended || state.replay -> ChatFooterNote(stringResource(R.string.chat_ended), "live-chat-ended")
            state.content !is LiveChatContent.Ready -> Unit
            state.signedIn && !state.canSend ->
                ChatFooterNote(stringResource(R.string.ui_posting_is_unavailable_for_this_chat), "live-chat-readonly")
            state.signedIn -> {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
                Row(Modifier.fillMaxWidth().padding(start = MinSpace.lg, end = MinSpace.sm, top = MinSpace.sm, bottom = MinSpace.sm),
                    verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).heightIn(min = MinSpace.Touch)
                        .background(DesignTokens.Surface, MinRadius.Dialog)
                        .padding(horizontal = MinSpace.lg, vertical = MinSpace.md),
                        contentAlignment = Alignment.CenterStart) {
                        if (state.draft.isEmpty()) {
                            AppText(stringResource(R.string.ui_chat_publicly), MinType.Body, color = DesignTokens.Secondary)
                        }
                        BasicTextField(state.draft, { onAction(LiveChatAction.DraftChanged(it)) },
                            Modifier.fillMaxWidth().testTag("live-chat-input"),
                            textStyle = MinType.Body.copy(color = DesignTokens.Ink),
                            cursorBrush = SolidColor(DesignTokens.Accent),
                            maxLines = 4)
                    }
                    Spacer(Modifier.width(MinSpace.xs))
                    val canSend = state.draft.isNotBlank() && !state.sending
                    Box(Modifier.size(MinSpace.Touch).testTag("live-chat-send")
                        .clickable(enabled = canSend, role = Role.Button) { onAction(LiveChatAction.Send) }
                        .appDescription(R.string.a11y_send_message), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(40.dp).background(if (canSend) DesignTokens.Accent else DesignTokens.Surface, CircleShape),
                            contentAlignment = Alignment.Center) {
                            if (state.sending) ActionProgress(color = DesignTokens.Accent)
                            else AppIcon(AppIcon.Send, Modifier.size(20.dp),
                                color = if (canSend) DesignTokens.OnAccent else DesignTokens.Secondary.copy(alpha = .5f))
                        }
                    }
                }
                state.postingRestriction?.let { reason ->
                    AppText(reason, MinType.Caption, Modifier.fillMaxWidth().padding(horizontal = MinSpace.lg, vertical = MinSpace.xs)
                        .testTag("live-chat-posting-restriction"), color = DesignTokens.Secondary)
                }
            }
            else -> {
                Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
                Row(Modifier.fillMaxWidth().padding(start = MinSpace.lg, end = MinSpace.sm, top = MinSpace.xs, bottom = MinSpace.xs),
                    verticalAlignment = Alignment.CenterVertically) {
                    AppText(stringResource(R.string.ui_sign_in_to_join_the_chat), MinType.Body,
                        Modifier.weight(1f).padding(end = MinSpace.sm), color = DesignTokens.Secondary)
                    MinButton(stringResource(R.string.ui_sign_in), { onAction(LiveChatAction.SignIn) },
                        kind = MinButtonKind.Tonal, testTag = "live-chat-signin")
                }
            }
        }
    }
}

/** Closing line under the list: the chat ended or posting is not possible. */
@Composable
private fun ChatFooterNote(text: String, tag: String) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
    AppText(text, MinType.Body, Modifier.fillMaxWidth().padding(horizontal = MinSpace.lg, vertical = MinSpace.lg).testTag(tag),
        color = DesignTokens.Secondary, align = TextAlign.Center)
}
