package com.hhst.youtubelite.tv.design

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusEnterExitScope
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusPropertiesModifierNode
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.restoreFocusedChild
import androidx.compose.ui.focus.saveFocusedChild
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.node.ModifierNodeElement

/*
 * Compose's focusRestorer(fallback) calls fallback.requestFocus() when focus enters a group with no
 * saved child, and that throws "FocusRequester is not initialized" when the fallback item is not
 * composed: in a lazy list the anchor card or row may have scrolled away (or not be laid out yet).
 * This happens in the same frame the anchor is removed (a history entry deleted under focus), so the
 * check must run when focus enters, not at composition. Mark the anchor with [tvAnchor] and use
 * [tvFocusRestorer] on the group: like focusRestorer it saves the focused child on exit and restores
 * it on enter, and it falls back to the anchor only while the anchor is attached; otherwise focus
 * enters the group as requested. (It does not pin the saved child: pinning is internal to
 * Compose. A saved child that scrolled out of composition is not restored; the anchor is used.)
 */

/** Attachment counts of the requesters marked with [tvAnchor] (main thread only). */
object TvFocusAnchors {
    private val attached = HashMap<FocusRequester, Int>()

    fun isAttached(requester: FocusRequester): Boolean = (attached[requester] ?: 0) > 0

    internal fun add(requester: FocusRequester, delta: Int) {
        val count = (attached[requester] ?: 0) + delta
        if (count <= 0) attached.remove(requester) else attached[requester] = count
    }
}

/** [focusRequester] for the item a [tvFocusRestorer] group falls back to. */
fun Modifier.tvAnchor(requester: FocusRequester): Modifier = focusRequester(requester) then TvAnchorElement(requester)

/** focusRestorer whose [fallback] is used only while its [tvAnchor] item is attached. */
fun Modifier.tvFocusRestorer(fallback: FocusRequester): Modifier = this then TvRestorerElement(fallback)

private data class TvRestorerElement(val fallback: FocusRequester) : ModifierNodeElement<TvRestorerNode>() {
    override fun create() = TvRestorerNode(fallback)
    override fun update(node: TvRestorerNode) {
        node.fallback = fallback
    }
}

@OptIn(ExperimentalComposeUiApi::class)
private class TvRestorerNode(var fallback: FocusRequester) : Modifier.Node(), FocusPropertiesModifierNode,
    FocusRequesterModifierNode, FocusEventModifierNode {
    private val onExit: FocusEnterExitScope.() -> Unit = { saveFocusedChild() }

    private var groupHasFocus = false

    private val onEnter: FocusEnterExitScope.() -> Unit = {
        when {
            restoreFocusedChild() -> Unit
            TvFocusAnchors.isAttached(fallback) -> fallback.requestFocus()
            // Not composed: let focus enter as requested. When the anchor attaches by the next frame
            // (the card that slid into a removed card's slot) and focus is still here, move to it.
            else -> coroutineScope.launch {
                withFrameNanos { }
                if (groupHasFocus && TvFocusAnchors.isAttached(fallback)) fallback.requestFocus()
            }
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        groupHasFocus = focusState.hasFocus
    }

    override fun applyFocusProperties(focusProperties: FocusProperties) {
        focusProperties.onEnter = onEnter
        focusProperties.onExit = onExit
    }
}

private data class TvAnchorElement(val requester: FocusRequester) : ModifierNodeElement<TvAnchorNode>() {
    override fun create() = TvAnchorNode(requester)
    override fun update(node: TvAnchorNode) = node.retarget(requester)
}

private class TvAnchorNode(private var requester: FocusRequester) : Modifier.Node() {
    override fun onAttach() = TvFocusAnchors.add(requester, 1)
    override fun onDetach() = TvFocusAnchors.add(requester, -1)

    fun retarget(next: FocusRequester) {
        if (next === requester) return
        if (isAttached) {
            TvFocusAnchors.add(requester, -1)
            TvFocusAnchors.add(next, 1)
        }
        requester = next
    }
}
