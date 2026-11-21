package com.hhst.youtubelite.tv

import androidx.activity.ComponentActivity
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.rules.ActivityScenarioRule

typealias TvComposeRule = AndroidComposeTestRule<ActivityScenarioRule<ComponentActivity>, ComponentActivity>

/**
 * Presses [key] on whatever holds focus, like a remote does. A panel shown in its own window (a
 * dialog) has its own focused node while the screen below keeps one; the remote's keys go to the
 * top window, which is registered last.
 */
@OptIn(ExperimentalTestApi::class)
fun TvComposeRule.press(key: Key) {
    waitForIdle()
    onAllNodes(isFocused()).onLast().performKeyInput { pressKey(key) }
    waitForIdle()
}

/** Back arrives through the dispatcher (predictive back), not as a key event. */
fun TvComposeRule.back() {
    runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
    waitForIdle()
}

fun TvComposeRule.isFocused(tag: String): Boolean =
    onAllNodes(hasTestTag(tag) and isFocused()).fetchSemanticsNodes().isNotEmpty()

/** Tags of the focused nodes, for failure messages. */
fun TvComposeRule.focusedTags(): List<String?> =
    onAllNodes(isFocused()).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.TestTag) }

fun TvComposeRule.awaitFocus(tag: String, timeoutMs: Long = 5_000) {
    try {
        waitUntil(timeoutMs) { isFocused(tag) }
    } catch (timeout: ComposeTimeoutException) {
        throw AssertionError("Focus never reached '$tag'; focused: ${focusedTags()}", timeout)
    }
}

/** Waits until one of [tags] holds focus. */
fun TvComposeRule.awaitAnyFocus(vararg tags: String, timeoutMs: Long = 5_000) {
    try {
        waitUntil(timeoutMs) { tags.any { isFocused(it) } }
    } catch (timeout: ComposeTimeoutException) {
        throw AssertionError("Focus never reached any of ${tags.toList()}; focused: ${focusedTags()}", timeout)
    }
}
