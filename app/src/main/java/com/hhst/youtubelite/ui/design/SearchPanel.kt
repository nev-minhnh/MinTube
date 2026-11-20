package com.hhst.youtubelite.ui.design

import com.hhst.youtubelite.ui.design.appPaneTitle
import androidx.compose.foundation.*
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import kotlinx.coroutines.launch

@Composable
internal fun SearchPanel(
    query: String,
    suggestions: List<String>,
    onQueryChanged: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    onVoiceSearch: () -> Unit,
    /** Live-mode local history rows (matching entries first); empty in fixture builds. */
    history: List<String> = emptyList(),
    onRemoveHistory: ((String) -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query, TextRange(query.length)))
    }
    if (field.text != query) field = TextFieldValue(query, TextRange(query.length))
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val motionScale = LocalMotionScale.current
    val reveal = remember { Animatable(if (motionScale <= 0f) 1f else 0f) }
    val scope = rememberCoroutineScope()
    val currentOnClose by rememberUpdatedState(onClose)
    var closing by remember { mutableStateOf(false) }
    /**
     * Closing only clears focus and hands the event to the host; the IME hides
     * through [onDispose]. An explicit hide here must not be issued while the
     * window may still be animating an earlier IME transition (recreation on
     * older APIs): that synchronous call can wedge the main thread before
     * [onClose] runs, leaving the overlay stuck open.
     */
    val close: () -> Unit = {
        if (!closing) {
            closing = true
            focusManager.clearFocus()
            scope.launch {
                reveal.animateTo(0f, tween(motionDuration(80, motionScale)))
                currentOnClose()
            }
        }
    }
    BackHandler(onBack = close)
    LaunchedEffect(Unit) {
        if (!closing) { focus.requestFocus(); keyboard?.show() }
        reveal.animateTo(1f, tween(motionDuration(100, motionScale), easing = LinearOutSlowInEasing))
    }
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    val submit: (String) -> Unit = { text -> keyboard?.hide(); onSubmit(text) }
    // The scrim also covers a navigation rail, which sits outside this screen's bounds.
    com.hhst.youtubelite.ui.components.CoverNavigationRail(DesignTokens.Scrim, { reveal.value }, close)
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }
        .background(DesignTokens.Scrim)
        .clickable(onClick = close).appDescription(R.string.a11y_close_search))
    Column(Modifier.fillMaxWidth().graphicsLayer {
        alpha = reveal.value
        translationY = -4.dp.toPx() * (1f - reveal.value)
    }.background(DesignTokens.Background).testTag("search-overlay").appPaneTitle(R.string.a11y_search_youtube)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).background(DesignTokens.Panel)
                .padding(start = MinSpace.xs, end = MinSpace.sm, top = MinSpace.xs, bottom = MinSpace.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIconButton(AppIcon.Back, stringResource(R.string.a11y_back), onClick = close, modifier = Modifier.testTag("search-panel-back"))
            // One pill: the field, a clear button while there is text, and the submit button.
            Row(
                Modifier.weight(1f).heightIn(min = MinSpace.Touch).clip(MinRadius.Full).background(DesignTokens.Surface)
                    .padding(start = MinSpace.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(field, { field = it; onQueryChanged(it.text) },
                    Modifier.weight(1f).padding(vertical = MinSpace.sm).focusRequester(focus).testTag("search-input")
                        .appDescription(R.string.a11y_search_youtube),
                    textStyle = MinType.Body.copy(color = DesignTokens.Ink), singleLine = true, cursorBrush = SolidColor(DesignTokens.Accent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(query) }),
                    decorationBox = { inner -> Box {
                        if (query.isEmpty()) AppText(stringResource(R.string.a11y_search_youtube), MinType.Body, color = DesignTokens.Secondary, maxLines = 1)
                        inner()
                    } })
                if (query.isNotEmpty()) AppIconButton(AppIcon.Close, stringResource(R.string.a11y_clear_search), color = DesignTokens.Secondary) {
                    onQueryChanged(""); focus.requestFocus(); keyboard?.show()
                }
                AppIconButton(AppIcon.Search, stringResource(R.string.a11y_search)) { submit(query) }
            }
            if (query.isEmpty()) AppIconButton(AppIcon.Voice, stringResource(R.string.a11y_voice_search),
                Modifier.padding(start = MinSpace.sm), background = DesignTokens.Surface) { onVoiceSearch() }
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = MinSpace.xs).testTag("search-suggestions")) {
            history.forEachIndexed { index, entry ->
                SearchPanelRow(AppIcon.History, AnnotatedString(entry), { submit(entry) }, Modifier.testTag("history-$index")) {
                    if (onRemoveHistory != null) AppIconButton(AppIcon.Close,
                        stringResource(R.string.a11y_remove_from_search_history, entry), color = DesignTokens.Secondary) {
                        onRemoveHistory(entry)
                    }
                }
            }
            suggestions.forEachIndexed { index, suggestion ->
                val prefixQuery = query.trim()
                val text = buildAnnotatedString {
                    val prefix = if (suggestion.startsWith(prefixQuery, ignoreCase = true)) prefixQuery.length else suggestion.length
                    append(suggestion.take(prefix))
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(suggestion.drop(prefix)) }
                }
                SearchPanelRow(AppIcon.Search, text, { submit(suggestion) }, Modifier.testTag("suggestion-$index")) {
                    // Fill-in arrow: puts the suggestion in the field to keep typing.
                    AppIconButton(AppIcon.ArrowUpLeft, stringResource(R.string.a11y_use_suggestion, suggestion), color = DesignTokens.Secondary) {
                        onQueryChanged(suggestion); focus.requestFocus(); keyboard?.show()
                    }
                }
            }
        }
    }
}

/** A 48dp history or suggestion row: leading glyph, up to two lines of text, one trailing action. */
@Composable
private fun SearchPanelRow(icon: AppIcon, text: AnnotatedString, onClick: () -> Unit, modifier: Modifier,
    trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).clickable(role = Role.Button, onClick = onClick).then(modifier)
            .padding(start = MinSpace.lg, end = MinSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, Modifier.size(20.dp), color = DesignTokens.Secondary)
        BasicText(text, Modifier.weight(1f).padding(horizontal = MinSpace.lg, vertical = MinSpace.sm),
            style = MinType.Body.copy(color = DesignTokens.Ink), maxLines = 2, overflow = TextOverflow.Ellipsis)
        trailing()
    }
}
