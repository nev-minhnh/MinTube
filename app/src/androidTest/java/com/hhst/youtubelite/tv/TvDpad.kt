package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.rules.ActivityScenarioRule

typealias TvComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/** Presses [key] on whatever holds focus, like a remote does. */
@OptIn(ExperimentalTestApi::class)
fun TvComposeRule.press(key: Key) {
    waitForIdle()
    onNode(isFocused()).performKeyInput { pressKey(key) }
    waitForIdle()
}

/** Back arrives through the dispatcher (predictive back), not as a key event. */
fun TvComposeRule.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

fun TvComposeRule.isFocused(tag: String): Boolean =
    onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

fun TvComposeRule.awaitFocus(tag: String, timeoutMs: Long = 5_000) {
    waitUntil(timeoutMs) { isFocused(tag) }
}
