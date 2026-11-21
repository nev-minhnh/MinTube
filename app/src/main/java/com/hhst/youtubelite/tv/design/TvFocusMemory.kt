package com.hhst.youtubelite.tv.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester

/** A row as focus restoration sees it: a stable key and its item keys in display order. */
data class TvFocusRow(val key: String, val itemKeys: List<String>)

/** Where focus should land: indexes into the rows and items passed to [TvFocusMemory.restoreTarget]. */
data class TvFocusTarget(val rowIndex: Int, val itemIndex: Int)

/**
 * Remembers the last focused item of every row and the last focused row, so focus comes back
 * to the same card after Back from Watch, a refresh that replaced the rows, or re-entering a row.
 *
 * Plain Kotlin (unit-tested): an item that left the list falls back to its old index, clamped
 * to the new size; a row that left falls back to the row now at the old row index.
 */
class TvFocusMemory internal constructor(
    private val items: MutableMap<String, Position>,
    private var lastRow: String?,
    private var lastRowIndex: Int,
) {
    constructor() : this(mutableMapOf(), null, 0)

    data class Position(val itemKey: String, val index: Int)

    val lastRowKey: String? get() = lastRow

    fun record(rowKey: String, rowIndex: Int, itemKey: String, itemIndex: Int) {
        items[rowKey] = Position(itemKey, itemIndex)
        lastRow = rowKey
        lastRowIndex = rowIndex
    }

    /** The index focus should enter [itemKeys] at: the remembered item, else its old slot, else 0. */
    fun indexIn(rowKey: String, itemKeys: List<String>): Int {
        if (itemKeys.isEmpty()) return 0
        val position = items[rowKey] ?: return 0
        val current = itemKeys.indexOf(position.itemKey)
        return if (current >= 0) current else position.index.coerceIn(0, itemKeys.lastIndex)
    }

    /** Null when nothing is focusable. */
    fun restoreTarget(rows: List<TvFocusRow>): TvFocusTarget? {
        val focusable = rows.withIndex().filter { it.value.itemKeys.isNotEmpty() }
        if (focusable.isEmpty()) return null
        val row = focusable.firstOrNull { it.value.key == lastRow }
            ?: focusable.minByOrNull { kotlin.math.abs(it.index - lastRowIndex) }!!
        return TvFocusTarget(row.index, indexIn(row.value.key, row.value.itemKeys))
    }

    fun forget(rowKey: String) {
        items.remove(rowKey)
    }

    fun clear() {
        items.clear()
        lastRow = null
        lastRowIndex = 0
    }

    internal fun save(): List<String> = buildList {
        add(lastRow.orEmpty())
        add(lastRowIndex.toString())
        items.forEach { (row, position) ->
            add(row)
            add(position.itemKey)
            add(position.index.toString())
        }
    }

    companion object {
        internal fun restore(saved: List<String>): TvFocusMemory {
            val items = mutableMapOf<String, Position>()
            saved.drop(2).chunked(3).filter { it.size == 3 }.forEach { (row, item, index) ->
                items[row] = Position(item, index.toIntOrNull() ?: 0)
            }
            return TvFocusMemory(items, saved.getOrNull(0)?.takeIf(String::isNotEmpty), saved.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        val Saver: Saver<TvFocusMemory, List<String>> = Saver(save = { it.save() }, restore = { restore(it) })
    }
}

@Composable
fun rememberTvFocusMemory(): TvFocusMemory = rememberSaveable(saver = TvFocusMemory.Saver) { TvFocusMemory() }

/**
 * One [FocusRequester] per row key. A row attaches its requester to the card at
 * [TvFocusMemory.indexIn]; the screen requests it to restore focus.
 */
class TvFocusRequesters {
    private val requesters = mutableMapOf<String, FocusRequester>()
    fun of(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** False when the target is not composed yet (scrolled away or still loading). */
    fun request(key: String): Boolean =
        runCatching { requesters[key]?.requestFocus(FocusDirection.Enter) ?: false }.getOrDefault(false)
}
