package com.hhst.youtubelite.watch.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.components.FilterChips
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.watch.domain.WatchPage
import com.hhst.youtubelite.watch.domain.WatchRelated

/*
 * The chip row under the player. The watch page data has no server-side chip/filter continuations
 * (no "Related" / "For you" / topic chips), so the row only filters the related items already
 * loaded: `All` (paging continues) and `From <channel>` (shown once at least one loaded item comes
 * from the current video's channel; paging pauses and a one-line hint explains the shorter list).
 */

/** Client-side filter of the loaded related list; pure, so the rules are unit-tested. */
@Immutable
internal data class RelatedFilter(
    val channel: String,
    /** True when `From <channel>` is offered (at least one loaded item matches). */
    val canFilter: Boolean,
    /** True when `From <channel>` is the active chip. */
    val fromChannel: Boolean,
    val visible: List<WatchRelated>,
) {
    companion object {
        fun of(page: WatchPage, fromChannelSelected: Boolean): RelatedFilter {
            val channelId = page.video.channelId
            val matches = if (channelId.isBlank()) emptyList() else page.related.filter { it.channelId == channelId }
            val canFilter = matches.isNotEmpty()
            val active = fromChannelSelected && canFilter
            return RelatedFilter(page.video.channel, canFilter, active, if (active) matches else page.related)
        }
    }
}

/** `All` and, when offered, `From <channel>`; chip tags `watch-related-chips-<index>`. */
@Composable
internal fun RelatedChipRow(filter: RelatedFilter, inset: Dp, onSelect: (fromChannel: Boolean) -> Unit, modifier: Modifier = Modifier) {
    val labels = buildList {
        add(stringResource(R.string.responsive_chip_all))
        if (filter.canFilter) add(stringResource(R.string.responsive_chip_from_channel, filter.channel))
    }
    // Opaque: in the single-column page the row sticks under the player while the feed scrolls beneath it.
    Box(modifier.fillMaxWidth().background(DesignTokens.Background).testTag("watch-related-chip-row")) {
        FilterChips(
            labels = labels,
            selectedIndex = if (filter.fromChannel) 1 else 0,
            onSelect = { onSelect(it == 1) },
            contentPadding = PaddingValues(horizontal = inset),
            tag = "watch-related-chips",
        )
    }
}

@Composable
internal fun RelatedFilterHint(inset: Dp) {
    AppText(
        stringResource(R.string.responsive_chip_filter_hint), MinType.Caption,
        Modifier.fillMaxWidth().padding(horizontal = inset).padding(bottom = MinSpace.sm).testTag("watch-related-filter-hint"),
        color = DesignTokens.Secondary, maxLines = 2,
    )
}
