package com.hhst.youtubelite.post.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appDescription
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Square images of a post, one page each. A post with several images shows the edge of the next
 * one and a position counter on the current one. [contentInset] is the page inset of the host.
 */
@Composable
fun PostImageCarousel(
    images: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpen: (Int) -> Unit = {},
    contentInset: Dp = MinSpace.lg,
) {
    if (images.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, images.lastIndex)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val peek = if (images.size > 1) PostMetrics.ImagePeek else 0.dp
        val imageWidth = (maxWidth - contentInset * 2 - peek).coerceAtMost(PostMetrics.ImageMax).coerceAtLeast(1.dp)
        val pagerState = rememberPagerState(initialPage = safeIndex) { images.size }
        LaunchedEffect(pagerState, images.size) {
            snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect(onSelected)
        }
        LaunchedEffect(safeIndex, pagerState) {
            if (pagerState.currentPage != safeIndex) pagerState.scrollToPage(safeIndex)
        }
        Box(Modifier.fillMaxWidth().height(imageWidth)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().testTag("post-images"),
                contentPadding = PaddingValues(horizontal = contentInset),
                pageSpacing = MinSpace.sm,
                pageSize = PageSize.Fixed(imageWidth),
            ) { index ->
                Box(
                    Modifier.size(imageWidth)
                        .clip(MinRadius.Card)
                        .clickable(role = Role.Button) { onOpen(index) }
                        .testTag("post-image-$index")
                        .appDescription(R.string.a11y_post_image_index, index + 1, images.size),
                ) {
                    Artwork(images[index], Modifier.matchParentSize())
                }
            }
            if (images.size > 1) {
                // Sits on artwork, so it keeps the fixed dark badge colors of the card family.
                Box(
                    Modifier.align(Alignment.TopStart)
                        .padding(start = contentInset + MinSpace.sm, top = MinSpace.sm)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .padding(horizontal = MinSpace.sm, vertical = MinSpace.xxs)
                        .testTag("post-image-counter")
                        .appDescription(R.string.a11y_image_index, pagerState.currentPage + 1, images.size),
                    contentAlignment = Alignment.Center,
                ) {
                    AppText("${pagerState.currentPage + 1}/${images.size}", MinType.Micro, color = Color.White, maxLines = 1)
                }
            }
        }
    }
}
