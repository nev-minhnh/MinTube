package com.hhst.youtubelite.account.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.hhst.youtubelite.R
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.ui.components.MinButton
import com.hhst.youtubelite.ui.components.MinButtonKind
import com.hhst.youtubelite.ui.components.MinTopBar
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.components.StateMessage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.ui.design.rememberWindowClass
import com.hhst.youtubelite.ui.noticeText
import com.hhst.youtubelite.ui.theme.AppStyle
import kotlinx.coroutines.delay

/** The device code: large, widely spaced, and read out character by character. */
private val CodeStyle = MinType.Display.copy(fontSize = 34.sp, lineHeight = 42.sp, letterSpacing = 3.sp)

/**
 * Device-code sign-in. Stateless: `SignInViewModel` owns the code request and the polling;
 * [onRetry] is its existing retry action.
 */
@Composable
fun SignInScreen(
    state: SignInUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenBrowser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val window = rememberWindowClass()
    Column(modifier.fillMaxSize().background(DesignTokens.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing).testTag("signin-screen")) {
        MinTopBar(title = stringResource(R.string.ui_sign_in), onBack = onBack, tag = "signin-topbar")
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = MinSpace.pageInset(window), vertical = MinSpace.lg),
            contentAlignment = Alignment.TopCenter) {
            when (state) {
                SignInUiState.Loading -> LoadingBlock(window)
                SignInUiState.Completing -> StatusBlock(stringResource(R.string.ui_finishing_sign_in), "signin-completing", done = false)
                SignInUiState.Success -> StatusBlock(stringResource(R.string.ui_signed_in), "signin-success", done = true)
                is SignInUiState.Error -> ErrorBlock(state.message, onRetry)
                is SignInUiState.Ready -> ReadyBlock(state, window, onOpenBrowser)
            }
        }
    }
}

/** Placeholder with the shape of the ready state while the code is requested. */
@Composable
private fun LoadingBlock(window: WindowClass) {
    Column(Modifier.widthIn(max = if (window == WindowClass.Compact) 480.dp else 840.dp).fillMaxWidth().testTag("signin-loading")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            CircularProgressIndicator(Modifier.size(18.dp), color = AppStyle.ActionFill, strokeWidth = 2.dp)
            Spacer(Modifier.width(MinSpace.md))
            AppText(stringResource(R.string.ui_contacting_youtube), MinType.Body, color = DesignTokens.Secondary)
        }
        Spacer(Modifier.height(MinSpace.xxl))
        SkeletonLine(fraction = 0.7f, height = 24.dp)
        Spacer(Modifier.height(MinSpace.xxl))
        SkeletonLine(fraction = 0.85f)
        Spacer(Modifier.height(MinSpace.md))
        SkeletonBox(Modifier.size(width = 160.dp, height = 40.dp), CircleShape)
        Spacer(Modifier.height(MinSpace.xxl))
        SkeletonLine(fraction = 0.4f)
        Spacer(Modifier.height(MinSpace.md))
        SkeletonBox(Modifier.fillMaxWidth().height(132.dp), MinRadius.Card)
        Spacer(Modifier.height(MinSpace.xxl))
        SkeletonLine(fraction = 0.9f)
    }
}

@Composable
private fun StatusBlock(text: String, tag: String, done: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = MinSpace.huge).testTag(tag)
        .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).background(DesignTokens.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
            if (done) AppIcon(AppIcon.Check, Modifier.size(32.dp), color = DesignTokens.AccentText)
            else CircularProgressIndicator(Modifier.size(32.dp), color = AppStyle.ActionFill, strokeWidth = 3.dp)
        }
        Spacer(Modifier.height(MinSpace.xl))
        AppText(text, MinType.Title, align = TextAlign.Center)
    }
}

@Composable
private fun ErrorBlock(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("signin-error"), horizontalAlignment = Alignment.CenterHorizontally) {
        StateMessage(LocalContext.current.noticeText(message), kind = StateKind.Error, tag = "signin-error-message")
        MinButton(stringResource(R.string.ui_try_again), onRetry, icon = AppIcon.Refresh, testTag = "signin-retry")
    }
}

@Composable
private fun ReadyBlock(state: SignInUiState.Ready, window: WindowClass, onOpenBrowser: (String) -> Unit) {
    val host = state.verificationUrl.toUri().host ?: state.verificationUrl
    if (window == WindowClass.Compact) {
        Column(Modifier.widthIn(max = 480.dp).fillMaxWidth().testTag("signin-ready")) {
            ReadyHeadline()
            Spacer(Modifier.height(MinSpace.xxl))
            OpenStep(host, state.verificationUrl, onOpenBrowser)
            Spacer(Modifier.height(MinSpace.xxl))
            CodeStep(state.userCode)
            Spacer(Modifier.height(MinSpace.xxl))
            WaitingStep()
        }
    } else {
        // Wide windows and landscape tablets: instructions at the start, the code beside them.
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().testTag("signin-ready")) {
            ReadyHeadline()
            Spacer(Modifier.height(MinSpace.xxl))
            Row(horizontalArrangement = Arrangement.spacedBy(MinSpace.xxl)) {
                Column(Modifier.weight(1f)) {
                    OpenStep(host, state.verificationUrl, onOpenBrowser)
                    Spacer(Modifier.height(MinSpace.xxl))
                    WaitingStep()
                }
                Column(Modifier.weight(1f)) { CodeStep(state.userCode) }
            }
        }
    }
}

@Composable
private fun ReadyHeadline() {
    AppText(stringResource(R.string.ui_sign_in_with_your_google_account), MinType.Display, Modifier.semantics { heading() })
}

@Composable
private fun OpenStep(host: String, url: String, onOpenBrowser: (String) -> Unit) {
    StepRow(1, stringResource(R.string.ui_open_1_s_on_your_phone_or_computer, host))
    Spacer(Modifier.height(MinSpace.sm))
    MinButton(stringResource(R.string.ui_open_in_browser), { onOpenBrowser(url) },
        Modifier.padding(start = StepIndent), icon = AppIcon.OpenWith, testTag = "signin-open")
}

@Composable
private fun CodeStep(code: String) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) { delay(2_000); copied = false }
    }
    StepRow(2, stringResource(R.string.ui_enter_this_code))
    Spacer(Modifier.height(MinSpace.md))
    Column(Modifier.fillMaxWidth().clip(MinRadius.Card).background(DesignTokens.Surface)
        .padding(horizontal = MinSpace.lg, vertical = MinSpace.xl),
        horizontalAlignment = Alignment.CenterHorizontally) {
        val spoken = code.toList().joinToString(" ")
        // The code never wraps: at large font scales or on narrow cards it shrinks to fit one line.
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val measurer = androidx.compose.ui.text.rememberTextMeasurer()
            val available = constraints.maxWidth
            val style = remember(code, available, measurer) {
                val natural = measurer.measure(code, CodeStyle, maxLines = 1, softWrap = false).size.width
                if (natural <= available || natural == 0) CodeStyle else {
                    val factor = available.toFloat() / natural * 0.98f
                    CodeStyle.copy(fontSize = CodeStyle.fontSize * factor, lineHeight = CodeStyle.lineHeight * factor,
                        letterSpacing = CodeStyle.letterSpacing * factor)
                }
            }
            AppText(code, style, Modifier.testTag("signin-code").semantics { contentDescription = spoken },
                maxLines = 1, align = TextAlign.Center)
        }
        Spacer(Modifier.height(MinSpace.sm))
        MinButton(
            stringResource(if (copied) R.string.copied else R.string.ui_copy_code),
            { clipboard.setText(AnnotatedString(code)); copied = true },
            kind = MinButtonKind.Tonal, icon = if (copied) AppIcon.Check else null, testTag = "signin-copy",
        )
    }
}

@Composable
private fun WaitingStep() {
    Row(Modifier.testTag("signin-waiting"), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(StepBadge), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), color = AppStyle.ActionFill, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(MinSpace.md))
        AppText(stringResource(R.string.ui_this_page_continues_on_its_own_once_you_confirm), MinType.Body,
            Modifier.weight(1f), color = DesignTokens.Secondary)
    }
}

private val StepBadge = 28.dp
private val StepIndent = StepBadge + MinSpace.md

@Composable
private fun StepRow(number: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(StepBadge).background(DesignTokens.AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
            AppText(number.toString(), MinType.Label, color = DesignTokens.AccentText)
        }
        Spacer(Modifier.width(MinSpace.md))
        AppText(text, MinType.Subtitle, Modifier.weight(1f))
    }
}
