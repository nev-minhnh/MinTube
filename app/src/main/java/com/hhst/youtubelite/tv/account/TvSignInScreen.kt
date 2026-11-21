package com.hhst.youtubelite.tv.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.hhst.youtubelite.R
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvColors
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.tv.design.TvType
import com.hhst.youtubelite.tv.qr.TvQrImage
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.noticeText

/** Text helpers for the sign-in screen (unit-tested). */
object TvSignInText {
    /** "https://www.youtube.com/activate" → "youtube.com/activate": what a person types. */
    fun displayUrl(url: String): String = url.trim()
        .removePrefix("https://").removePrefix("http://").removePrefix("www.")
        .trimEnd('/')
        .ifBlank { DEFAULT_URL }

    const val DEFAULT_URL = "youtube.com/activate"
}

/**
 * Device-code sign-in at ten feet: the code very large, the address to type, a QR code of the
 * address for a phone camera, and a live "waiting" line while the view model polls. Error
 * (including an expired code) shows a focused "Try again" that fetches a new code.
 */
@Composable
fun TvSignInScreen(state: SignInUiState, onRetry: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxSize().background(TvColors.Background)
            // One overscan margin vertically: at 1080p (540 dp) a double margin pushed the buttons off the page.
            .padding(horizontal = TvSpacing.OverscanHorizontal * 2, vertical = TvSpacing.OverscanVertical)
            .testTag(TAG_SIGN_IN),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is SignInUiState.Error -> Column(horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(TvSpacing.lg)) {
                TvStateMessage(
                    title = stringResource(R.string.tv_sign_in_failed_title),
                    message = androidx.compose.ui.platform.LocalContext.current.noticeText(state.message),
                    icon = AppIcon.Error,
                    actionLabel = stringResource(R.string.ui_try_again),
                    onAction = onRetry,
                    actionModifier = Modifier.testTag(TAG_SIGN_IN_RETRY),
                    requestFocus = true,
                )
                TvButton(stringResource(R.string.cancel), onCancel, Modifier.testTag(TAG_SIGN_IN_CANCEL))
            }
            SignInUiState.Success -> TvStateMessage(
                title = stringResource(R.string.ui_signed_in),
                icon = AppIcon.CheckCircle,
                // Keeps a focus target for the moment before the screen closes.
                actionLabel = stringResource(R.string.tv_done),
                onAction = onCancel,
                requestFocus = true,
            )
            else -> CodeLayout(state, onRetry, onCancel)
        }
    }
}

@Composable
private fun CodeLayout(state: SignInUiState, onRetry: () -> Unit, onCancel: () -> Unit) {
    val ready = state as? SignInUiState.Ready
    val url = ready?.verificationUrl ?: "https://${TvSignInText.DEFAULT_URL}"
    val primary = remember { FocusRequester() }
    Row(horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            Text(stringResource(R.string.ui_sign_in_with_your_google_account), style = TvType.Display, color = TvColors.Ink)
            Text(stringResource(R.string.ui_open_1_s_on_your_phone_or_computer, TvSignInText.displayUrl(url)),
                style = TvType.Title, color = TvColors.Secondary, modifier = Modifier.testTag(TAG_SIGN_IN_URL))
            Spacer(Modifier.height(TvSpacing.sm))
            Text(stringResource(R.string.ui_enter_this_code), style = TvType.Body, color = TvColors.Secondary)
            if (ready != null) {
                Text(
                    ready.userCode,
                    style = TvType.Display.copy(fontSize = 72.sp, lineHeight = 84.sp, letterSpacing = 6.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                    color = TvColors.Accent,
                    maxLines = 1,
                    modifier = Modifier.testTag(TAG_SIGN_IN_CODE),
                )
            } else {
                // Skeleton of the code while it loads.
                Box(Modifier.width(420.dp).height(84.dp).clip(RoundedCornerShape(12.dp)).background(TvColors.Ghost)
                    .clearAndSetSemantics { })
            }
            StatusLine(state)
            Row(horizontalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
                TvButton(stringResource(R.string.tv_sign_in_new_code), onRetry,
                    Modifier.focusRequester(primary).testTag(TAG_SIGN_IN_RETRY), icon = AppIcon.Refresh)
                TvButton(stringResource(R.string.cancel), onCancel, Modifier.testTag(TAG_SIGN_IN_CANCEL))
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).testTag(TAG_SIGN_IN_QR)) {
                TvQrImage(url, stringResource(R.string.tv_qr_description, TvSignInText.displayUrl(url)), size = 260.dp)
            }
            Text(stringResource(R.string.tv_sign_in_scan), style = TvType.Caption, color = TvColors.Secondary)
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { primary.requestFocus(FocusDirection.Enter) }
    }
}

@Composable
private fun StatusLine(state: SignInUiState) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.testTag(TAG_SIGN_IN_STATUS)) {
        androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = TvColors.Accent, strokeWidth = 3.dp)
        Spacer(Modifier.width(TvSpacing.md))
        Column {
            Text(stringResource(when (state) {
                SignInUiState.Loading -> R.string.ui_contacting_youtube
                SignInUiState.Completing -> R.string.ui_finishing_sign_in
                else -> R.string.tv_sign_in_waiting
            }), style = TvType.BodyStrong, color = TvColors.Ink)
            if (state is SignInUiState.Ready) {
                Text(stringResource(R.string.tv_sign_in_expiry_hint), style = TvType.Caption, color = TvColors.Secondary)
            }
        }
    }
}

const val TAG_SIGN_IN = "tv-sign-in"
const val TAG_SIGN_IN_CODE = "tv-sign-in-code"
const val TAG_SIGN_IN_URL = "tv-sign-in-url"
const val TAG_SIGN_IN_QR = "tv-sign-in-qr"
const val TAG_SIGN_IN_STATUS = "tv-sign-in-status"
const val TAG_SIGN_IN_RETRY = "tv-sign-in-retry"
const val TAG_SIGN_IN_CANCEL = "tv-sign-in-cancel"
