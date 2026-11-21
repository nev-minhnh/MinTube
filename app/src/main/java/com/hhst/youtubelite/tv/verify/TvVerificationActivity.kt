package com.hhst.youtubelite.tv.verify

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.hhst.youtubelite.R
import com.hhst.youtubelite.core.TrafficVerificationActivity
import com.hhst.youtubelite.core.UrlPolicy
import com.hhst.youtubelite.tv.TvActivity
import com.hhst.youtubelite.tv.design.TvButton
import com.hhst.youtubelite.tv.design.TvSpacing
import com.hhst.youtubelite.tv.design.TvStateMessage
import com.hhst.youtubelite.ui.design.AppIcon

/**
 * TV entry to the traffic check ("are you a robot", YouTube's /sorry page). Same contract as the
 * phone's [TrafficVerificationActivity]: input is the challenge URL, RESULT_OK carries it back
 * and grants the caller's view model one retry (`verificationCompleted(challenge)`).
 */
object TvVerification {
    const val EXTRA_CHALLENGE = TrafficVerificationActivity.EXTRA_CHALLENGE

    fun intent(context: Context, challengeUrl: String): Intent =
        Intent(context, TvVerificationActivity::class.java).putExtra(EXTRA_CHALLENGE, challengeUrl)

    /** Result: the solved challenge URL, or null when the user backed out. */
    object Contract : ActivityResultContract<String, String?>() {
        override fun createIntent(context: Context, input: String): Intent = intent(context, input)
        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            if (resultCode == Activity.RESULT_OK) intent?.getStringExtra(EXTRA_CHALLENGE) else null
    }
}

/** What the TV verification page shows. */
enum class TvVerificationStep {
    /** Explains the check and offers to open it. */
    Intro,
    /** The check was closed without passing: try again or leave. */
    NotCompleted,
    /** No WebView on this TV: the check cannot be shown at all. */
    Unavailable,
}

/**
 * Explains the check in remote terms, then opens the unchanged WebView page
 * ([TrafficVerificationActivity]), where the D-pad moves between the page's controls and OK
 * presses them. Its result is passed straight back to the caller.
 */
class TvVerificationActivity : TvActivity() {
    private var step by mutableStateOf(TvVerificationStep.Intro)
    private lateinit var challenge: String
    private val check: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            setResult(RESULT_OK, Intent().putExtra(TvVerification.EXTRA_CHALLENGE, challenge))
            finish()
        } else {
            step = TvVerificationStep.NotCompleted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        challenge = intent.getStringExtra(TvVerification.EXTRA_CHALLENGE)?.takeIf(UrlPolicy::isTrafficChallenge)
            ?: return finish()
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) step = TvVerificationStep.Unavailable
        savedInstanceState?.getString(STATE_STEP)?.let { saved ->
            TvVerificationStep.entries.firstOrNull { it.name == saved }?.let { step = it }
        }
        setTvContent {
            BackHandler { finish() }
            TvVerificationScreen(step, onOpen = ::openCheck, onCancel = ::finish)
        }
    }

    private fun openCheck() {
        runCatching {
            check.launch(Intent(this, TrafficVerificationActivity::class.java)
                .putExtra(TrafficVerificationActivity.EXTRA_CHALLENGE, challenge))
        }.onFailure { step = TvVerificationStep.Unavailable }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_STEP, step.name)
    }

    private companion object {
        const val STATE_STEP = "tv_verification_step"
    }
}

/** State-hoisted page: a [TvStateMessage] with a focused primary action, and "Not now" below it. */
@Composable
fun TvVerificationScreen(step: TvVerificationStep, onOpen: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical)
        .testTag(TAG_VERIFICATION), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(TvSpacing.md)) {
            when (step) {
                TvVerificationStep.Intro, TvVerificationStep.NotCompleted -> {
                    TvStateMessage(
                        title = stringResource(if (step == TvVerificationStep.Intro) R.string.tv_verify_title
                        else R.string.tv_verify_not_completed_title),
                        message = stringResource(R.string.tv_verify_message),
                        icon = AppIcon.Error,
                        actionLabel = stringResource(if (step == TvVerificationStep.Intro) R.string.tv_verify_open
                        else R.string.ui_try_again),
                        onAction = onOpen,
                        actionModifier = Modifier.testTag(TAG_VERIFICATION_OPEN),
                        requestFocus = true,
                    )
                    TvButton(stringResource(R.string.tv_verify_not_now), onCancel, Modifier.testTag(TAG_VERIFICATION_CANCEL))
                }
                TvVerificationStep.Unavailable -> TvStateMessage(
                    title = stringResource(R.string.tv_verify_title),
                    message = stringResource(R.string.tv_verify_unavailable),
                    icon = AppIcon.Error,
                    actionLabel = stringResource(R.string.verification_close),
                    onAction = onCancel,
                    actionModifier = Modifier.testTag(TAG_VERIFICATION_CANCEL),
                    requestFocus = true,
                )
            }
        }
    }
}

const val TAG_VERIFICATION = "tv-verification"
const val TAG_VERIFICATION_OPEN = "tv-verification-open"
const val TAG_VERIFICATION_CANCEL = "tv-verification-cancel"

/**
 * A screen-level notice for a pending traffic check. [blocking]: nothing loaded, so the notice
 * replaces the content and takes focus; otherwise it is a slim banner above working content.
 */
@Composable
fun TvVerificationNotice(onVerify: () -> Unit, blocking: Boolean, modifier: Modifier = Modifier) {
    if (blocking) {
        Box(modifier.fillMaxSize().padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.OverscanVertical),
            contentAlignment = Alignment.Center) {
            TvStateMessage(
                title = stringResource(R.string.tv_verify_title),
                message = stringResource(R.string.tv_verify_blocked_message),
                icon = AppIcon.Error,
                actionLabel = stringResource(R.string.tv_verify_open),
                onAction = onVerify,
                actionModifier = Modifier.testTag(TAG_VERIFICATION_NOTICE),
                requestFocus = true,
            )
        }
    } else {
        androidx.compose.foundation.layout.Row(
            modifier.padding(horizontal = TvSpacing.OverscanHorizontal, vertical = TvSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(TvSpacing.lg),
        ) {
            androidx.tv.material3.Text(stringResource(R.string.tv_verify_banner),
                style = com.hhst.youtubelite.tv.design.TvType.Body, color = com.hhst.youtubelite.tv.design.TvColors.Secondary,
                modifier = Modifier.weight(1f, fill = false))
            TvButton(stringResource(R.string.tv_verify_open), onVerify, Modifier.testTag(TAG_VERIFICATION_NOTICE),
                icon = AppIcon.Error)
        }
    }
}

const val TAG_VERIFICATION_NOTICE = "tv-verification-notice"
