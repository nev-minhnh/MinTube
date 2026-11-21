package com.hhst.youtubelite.tv

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.requestFocus
import androidx.test.platform.app.InstrumentationRegistry
import com.hhst.youtubelite.account.domain.SignInUiState
import com.hhst.youtubelite.extension.PreferenceKeys
import com.hhst.youtubelite.tv.account.TAG_SIGN_IN_CODE
import com.hhst.youtubelite.tv.account.TAG_SIGN_IN_QR
import com.hhst.youtubelite.tv.account.TAG_SIGN_IN_RETRY
import com.hhst.youtubelite.tv.account.TAG_SIGN_IN_URL
import com.hhst.youtubelite.tv.account.TvSignInScreen
import com.hhst.youtubelite.tv.design.TvCanvas
import com.hhst.youtubelite.tv.design.TvPanelOption
import com.hhst.youtubelite.tv.settings.TAG_PANEL_LANGUAGE
import com.hhst.youtubelite.tv.settings.TAG_PANEL_SIGN_OUT
import com.hhst.youtubelite.tv.settings.TAG_ROW
import com.hhst.youtubelite.tv.settings.TAG_SETTINGS_LIST
import com.hhst.youtubelite.tv.settings.TvAccountChoice
import com.hhst.youtubelite.tv.settings.TvAccountState
import com.hhst.youtubelite.tv.settings.TvSettingsCallbacks
import com.hhst.youtubelite.tv.settings.TvSettingsCatalog
import com.hhst.youtubelite.tv.settings.TvSettingsChoices
import com.hhst.youtubelite.tv.settings.TvSettingsScreen
import com.hhst.youtubelite.tv.verify.TAG_VERIFICATION_CANCEL
import com.hhst.youtubelite.tv.verify.TAG_VERIFICATION_OPEN
import com.hhst.youtubelite.tv.verify.TvVerificationScreen
import com.hhst.youtubelite.tv.verify.TvVerificationStep
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** D-pad on TV Settings, the device-code sign-in page and the traffic-check page (fakes, no network). */
@OptIn(ExperimentalTestApi::class)
class TvSettingsAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val calls = mutableListOf<String>()
    private val prefs = mutableStateOf(mapOf<String, Boolean>())
    private val account = mutableStateOf(TvAccountState())

    private val callbacks = object : TvSettingsCallbacks {
        override fun toggle(prefKey: String, enabled: Boolean) {
            calls += "toggle:$prefKey:$enabled"
            prefs.value = prefs.value + (prefKey to enabled)
        }
        override fun signIn() { calls += "signIn" }
        override fun selectAccount(id: String) { calls += "select:$id" }
        override fun useDevice() { calls += "device" }
        override fun signOut() { calls += "signOut" }
        override fun language(index: Int) { calls += "language:$index" }
        override fun captionSize(index: Int) { calls += "captionSize:$index" }
        override fun captionBackground(index: Int) { calls += "captionBackground:$index" }
    }

    private fun hostSettings() {
        compose.setContent {
            TvCanvas {
                val rows = TvSettingsCatalog.rows(prefs.value, account.value, "English", "Version 1.0.0")
                TvSettingsScreen(rows, account.value, TvSettingsChoices(
                    languages = listOf(TvPanelOption("System default", false), TvPanelOption("English", true),
                        TvPanelOption("Tiếng Việt", false)),
                ), callbacks)
            }
        }
    }

    private fun row(key: String) = "$TAG_ROW-$key"

    private fun focusRow(key: String) {
        compose.onNodeWithTag(TAG_SETTINGS_LIST).performScrollToKey(key)
        compose.onNodeWithTag(row(key)).requestFocus()
        compose.awaitFocus(row(key))
    }

    private fun systemBack() {
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
    }

    @Test fun signedOutDownReachesSignInAndOkStartsIt() {
        hostSettings()
        focusRow("account:identity")
        compose.press(Key.DirectionDown)
        compose.awaitFocus(row("action:SignIn"))
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("signIn"), calls)
    }

    @Test fun okFlipsAToggleThroughTheCallback() {
        hostSettings()
        val key = "pref:${PreferenceKeys.CONTENT_LANGUAGE_ENGLISH}"
        focusRow(key)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("toggle:${PreferenceKeys.CONTENT_LANGUAGE_ENGLISH}:false"), calls)
        compose.awaitFocus(row(key))
        compose.press(Key.DirectionCenter)
        assertEquals("toggle:${PreferenceKeys.CONTENT_LANGUAGE_ENGLISH}:true", calls.last())
    }

    @Test fun languagePanelFocusesTheCurrentChoiceAndReturnsFocusAfterAPick() {
        hostSettings()
        focusRow("action:Language")
        compose.press(Key.DirectionCenter)
        compose.onNodeWithTag(TAG_PANEL_LANGUAGE).assertExists()
        compose.awaitFocus("$TAG_PANEL_LANGUAGE-1")
        compose.press(Key.DirectionDown)
        compose.awaitFocus("$TAG_PANEL_LANGUAGE-2")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("language:2"), calls)
        compose.onNodeWithTag(TAG_PANEL_LANGUAGE).assertDoesNotExist()
        compose.awaitFocus(row("action:Language"))
    }

    @Test fun backClosesAPanelWithoutPicking() {
        hostSettings()
        focusRow("action:Language")
        compose.press(Key.DirectionCenter)
        compose.awaitFocus("$TAG_PANEL_LANGUAGE-1")
        systemBack()
        compose.onNodeWithTag(TAG_PANEL_LANGUAGE).assertDoesNotExist()
        assertEquals(emptyList<String>(), calls)
        compose.awaitFocus(row("action:Language"))
    }

    @Test fun signOutAsksFirstWithCancelFocused() {
        account.value = TvAccountState(hasAccount = true, signedIn = true, name = "Minh", email = "m@example.com",
            accounts = listOf(TvAccountChoice("a", "Minh", "m@example.com", true)))
        hostSettings()
        focusRow("action:SignOut")
        compose.press(Key.DirectionCenter)
        compose.awaitFocus("$TAG_PANEL_SIGN_OUT-cancel")
        compose.press(Key.DirectionRight)
        compose.awaitFocus("$TAG_PANEL_SIGN_OUT-confirm")
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("signOut"), calls)
    }

    @Test fun signInShowsLargeCodeUrlAndQrWithFocusedRetry() {
        compose.setContent {
            TvCanvas {
                TvSignInScreen(SignInUiState.Ready("ABCD-EFGH", "https://www.youtube.com/activate"),
                    onRetry = { calls += "retry" }, onCancel = { calls += "cancel" })
            }
        }
        compose.onNodeWithTag(TAG_SIGN_IN_CODE).assertExists()
        compose.onNodeWithTag(TAG_SIGN_IN_URL).assertExists()
        compose.onNodeWithTag(TAG_SIGN_IN_QR).assertExists()
        compose.awaitFocus(TAG_SIGN_IN_RETRY)
        compose.press(Key.DirectionCenter)
        compose.press(Key.DirectionRight)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("retry", "cancel"), calls)
    }

    @Test fun expiredCodeOffersAFocusedTryAgain() {
        compose.setContent {
            TvCanvas {
                TvSignInScreen(SignInUiState.Error("The sign-in code expired. Get a new one."),
                    onRetry = { calls += "retry" }, onCancel = { calls += "cancel" })
            }
        }
        compose.awaitFocus(TAG_SIGN_IN_RETRY)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("retry"), calls)
    }

    @Test fun verificationPageOpensTheCheckOrLeaves() {
        val step = mutableStateOf(TvVerificationStep.Intro)
        compose.setContent {
            TvCanvas { TvVerificationScreen(step.value, onOpen = { calls += "open" }, onCancel = { calls += "cancel" }) }
        }
        compose.awaitFocus(TAG_VERIFICATION_OPEN)
        compose.press(Key.DirectionCenter)
        compose.press(Key.DirectionDown)
        compose.awaitFocus(TAG_VERIFICATION_CANCEL)
        compose.press(Key.DirectionCenter)
        assertEquals(listOf("open", "cancel"), calls)
        step.value = TvVerificationStep.Unavailable
        compose.awaitFocus(TAG_VERIFICATION_CANCEL)
    }
}
