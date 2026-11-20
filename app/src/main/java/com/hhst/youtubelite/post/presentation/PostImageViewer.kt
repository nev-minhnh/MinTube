package com.hhst.youtubelite.post.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.appPaneTitle
import kotlinx.coroutines.flow.distinctUntilChanged

/** Full-window image viewer. Like the player it stays on black in both themes. */
@Composable
fun PostImageViewer(
    images: List<String>,
    initialIndex: Int,
    onClose: () -> Unit,
    onPageSelected: (Int) -> Unit,
) {
    if (images.isEmpty()) return
    val safeIndex = initialIndex.coerceIn(0, images.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeIndex) { images.size }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect(onPageSelected)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .testTag("post-image-viewer").appPaneTitle(R.string.ui_post_image),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { index ->
            Box(Modifier.fillMaxSize().padding(horizontal = MinSpace.lg, vertical = 56.dp),
                contentAlignment = Alignment.Center) {
                Artwork(images[index], Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
        AppText(
            "${pagerState.currentPage + 1}/${images.size}", MinType.Label,
            Modifier.align(Alignment.TopCenter).padding(top = MinSpace.lg)
                .background(Color.White.copy(alpha = 0.16f), CircleShape)
                .padding(horizontal = MinSpace.md, vertical = MinSpace.xs)
                .testTag("post-image-viewer-counter"),
            color = Color.White, maxLines = 1,
        )
        AppIconButton(
            AppIcon.Close, stringResource(R.string.ui_close_image_viewer), color = Color.White,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = MinSpace.xs, top = MinSpace.xs)
                .testTag("post-image-viewer-close"),
            onClick = onClose,
        )
    }
}
