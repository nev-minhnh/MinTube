package com.hhst.youtubelite.search.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.dearrow.rememberDeArrowTitle
import com.hhst.youtubelite.search.domain.SearchAction
import com.hhst.youtubelite.search.domain.SearchResult
import com.hhst.youtubelite.search.domain.SearchResultsLayout
import com.hhst.youtubelite.ui.components.ShortCard
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.CenteredListGutter
import com.hhst.youtubelite.ui.design.miniDockListPadding

/** Shorts filter results on the shared 9:16 card; views and length sit under the title, on the artwork scrim. */
@Composable
fun ShortsResultsList(shorts: List<SearchResult>, state: LazyGridState, onAction: (SearchAction) -> Unit, footer: (@Composable () -> Unit)? = null,
    columns: Int = 2, inset: Dp = MinSpace.lg) {
    CenteredListGutter(SearchResultsLayout.ColumnMaxWidth.dp) { gutter ->
        val dock = miniDockListPadding(MinSpace.xxl)
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = state,
            modifier = Modifier.fillMaxSize().then(dock.modifier).testTag("search-shorts-list"),
            contentPadding = PaddingValues(top = MinSpace.sm, start = inset + gutter, end = inset + gutter, bottom = dock.bottom),
            horizontalArrangement = Arrangement.spacedBy(MinSpace.md),
            verticalArrangement = Arrangement.spacedBy(MinSpace.md),
        ) {
            items(shorts, key = { it.id }) { short ->
                ShortCard(
                    title = rememberDeArrowTitle(short.id, short.title),
                    artwork = short.artwork,
                    onClick = { onAction(SearchAction.OpenResult(short.id)) },
                    modifier = Modifier.testTag("search-short-result-${short.id}"),
                    // Only non-empty fields join: no orphan separators on missing data.
                    metadata = listOf(short.views, short.duration).filter(String::isNotBlank).joinToString(" · ").takeIf(String::isNotBlank),
                    videoId = short.id,
                )
            }
            if (footer != null) item(key = "pagination", span = { GridItemSpan(maxLineSpan) }) { footer() }
        }
    }
}
