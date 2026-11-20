package com.hhst.youtubelite.post.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.presentation.SectionState
import com.hhst.youtubelite.channel.presentation.StatePage
import com.hhst.youtubelite.comments.domain.CommentAction
import com.hhst.youtubelite.comments.domain.CommentsUiState
import com.hhst.youtubelite.comments.presentation.CommentsPanel
import com.hhst.youtubelite.post.domain.PostDestination
import com.hhst.youtubelite.post.domain.PostDetailAction
import com.hhst.youtubelite.post.domain.PostDetailContent
import com.hhst.youtubelite.post.domain.PostDetailOverlay
import com.hhst.youtubelite.post.domain.PostDetailUiState
import com.hhst.youtubelite.post.domain.PostMenuAction
import com.hhst.youtubelite.ui.components.SkeletonBox
import com.hhst.youtubelite.ui.components.SkeletonLine
import com.hhst.youtubelite.ui.components.StateKind
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.AppTheme
import com.hhst.youtubelite.ui.design.BrowseNavigationBar
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.labelResource

/** Height of the post top bar at the default font scale; the comments sheet opens below it. */
private const val PostTopBarHeight = 56f

/**
 * A community post: author, text, images, poll and the like / dislike / comments row, at a
 * reading width that is centered on wide windows. Comments open in the shared comments panel.
 */
@Composable
fun PostDetailScreen(
    state: PostDetailUiState,
    onAction: (PostDetailAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    commentsState: CommentsUiState = CommentsUiState(),
    onCommentsAction: (CommentAction) -> Unit = {},
) {
    val commentsListState = rememberLazyListState()
    AppTheme {
        BoxWithConstraints(modifier.fillMaxSize().background(DesignTokens.Background).testTag("post-detail-page")) {
            val pageWidth = maxWidth
            Column(Modifier.fillMaxSize().then(
                if (state.overlay != PostDetailOverlay.None || state.commentsOpen) Modifier.clearAndSetSemantics {} else Modifier,
            )) {
                PostDetailTopBar(state, onAction, onBack)
                when (state.content) {
                    PostDetailContent.Loading -> PostDetailSkeleton(pageWidth, Modifier.weight(1f))
                    PostDetailContent.Failed -> Box(Modifier.weight(1f)) { PostDetailFailed(onAction) }
                    is PostDetailContent.Ready -> Box(Modifier.weight(1f)) {
                        PostDetailContentColumn(state, pageWidth, onAction)
                    }
                }
                BrowseNavigationBar("post-detail", state.signedIn) { onAction(PostDetailAction.SelectDestination(PostDestination.valueOf(it.name))) }
            }
            when (val overlay = state.overlay) {
                PostDetailOverlay.None -> Unit
                PostDetailOverlay.Menu -> MenuBottomSheet(
                    items = PostMenuAction.entries.map { action ->
                        MenuSheetItem(
                            icon = AppIcon.Report,
                            label = stringResource(action.labelResource),
                            tag = "post-detail-menu-${action.name.lowercase()}",
                        )
                    },
                    onDismiss = { onAction(PostDetailAction.CloseOverlay) },
                    onSelect = { index -> onAction(PostDetailAction.MenuSelected(PostMenuAction.entries[index])) },
                    tag = "post-detail-menu",
                    paneTitle = stringResource(R.string.a11y_post_actions),
                )
                is PostDetailOverlay.Image -> PostImageViewer(
                    images = state.post?.images.orEmpty(),
                    initialIndex = overlay.index,
                    onClose = { onAction(PostDetailAction.CloseOverlay) },
                    onPageSelected = { onAction(PostDetailAction.SelectImage(it)) },
                )
            }
            if (state.commentsOpen) {
                CommentsPanel(
                    state = commentsState,
                    commentCount = commentsState.total,
                    onAction = onCommentsAction,
                    onClose = { onAction(PostDetailAction.CloseComments) },
                    modifier = Modifier.fillMaxSize(),
                    listState = commentsListState,
                    sheetTopOverride = PostTopBarHeight,
                )
            }
        }
    }
}

/** Same geometry as `MinTopBar`; written out because the title, progress and menu carry this page's tags. */
@Composable
private fun PostDetailTopBar(
    state: PostDetailUiState,
    onAction: (PostDetailAction) -> Unit,
    onBack: () -> Unit,
) {
    val title = state.post?.text?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: stringResource(R.string.ui_post)
    Row(
        Modifier.fillMaxWidth().heightIn(min = PostTopBarHeight.dp).background(DesignTokens.Background)
            .testTag("post-detail-topbar").padding(horizontal = MinSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconButton(AppIcon.Back, stringResource(R.string.a11y_close_post), onClick = onBack)
        AppText(
            title, MinType.Title,
            Modifier.weight(1f).padding(end = MinSpace.xs).testTag("post-detail-title").semantics { heading() },
            maxLines = 1,
        )
        if (state.refreshing) Box(Modifier.size(MinSpace.Touch).testTag("post-detail-refreshing"), contentAlignment = Alignment.Center) {
            ActionProgress()
        }
        if (!state.live) AppIconButton(
            AppIcon.More, stringResource(R.string.a11y_post_actions),
            modifier = Modifier.testTag("post-detail-more"),
        ) { onAction(PostDetailAction.OpenMenu) }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
}

@Composable
private fun PostDetailContentColumn(
    state: PostDetailUiState,
    pageWidth: Dp,
    onAction: (PostDetailAction) -> Unit,
) {
    val post = state.post ?: return
    val inset = PostMetrics.inset(pageWidth)
    LazyColumn(
        Modifier.fillMaxSize().testTag("post-detail-list"),
        contentPadding = PaddingValues(bottom = MinSpace.xxl),
    ) {
        item("author") {
            PostColumn {
                PostAuthorRow(
                    post.author.name, post.age, post.author.avatar, inset,
                    Modifier.testTag("post-detail-author"),
                    nameTag = "post-detail-author-name",
                    avatarDescription = stringResource(R.string.a11y_go_to_channel, post.author.name),
                    onAvatarClick = { onAction(PostDetailAction.OpenChannel) },
                )
            }
        }
        item("body") {
            PostColumn {
                AppText(
                    post.text, MinType.Body,
                    Modifier.fillMaxWidth().padding(horizontal = inset).padding(top = MinSpace.md).testTag("post-detail-body"),
                )
            }
        }
        if (post.images.isNotEmpty()) {
            item("images") {
                PostColumn(Modifier.padding(top = MinSpace.md)) {
                    PostImageCarousel(
                        images = post.images,
                        selectedIndex = state.selectedImage,
                        onSelected = { onAction(PostDetailAction.SelectImage(it)) },
                        onOpen = { onAction(PostDetailAction.OpenImage(it)) },
                        modifier = Modifier.testTag("post-detail-images"),
                        contentInset = inset,
                    )
                }
            }
        }
        post.poll?.let { poll ->
            item("poll") {
                PostColumn {
                    PostPollChoices(poll, state.actionPending, Modifier.padding(horizontal = inset).padding(top = MinSpace.md),
                        pendingOption = (state.pendingAction as? PostDetailAction.Vote)?.option) {
                        onAction(PostDetailAction.Vote(it, !poll.options[it].selected))
                    }
                }
            }
        }
        item("feedback") {
            PostColumn {
                PostRatingRow(
                    likes = post.likes,
                    comments = post.comments,
                    rating = post.rating,
                    enabled = !state.actionPending,
                    likePending = state.pendingAction == PostDetailAction.Like,
                    dislikePending = state.pendingAction == PostDetailAction.Dislike,
                    onLike = { onAction(PostDetailAction.Like) },
                    onDislike = { onAction(PostDetailAction.Dislike) },
                    onComments = { onAction(PostDetailAction.Comments) },
                    likeTag = "post-detail-like",
                    dislikeTag = "post-detail-dislike",
                    commentsTag = "post-detail-comments",
                    modifier = Modifier.padding(horizontal = inset - MinSpace.md).padding(top = MinSpace.sm).testTag("post-detail-feedback"),
                )
            }
        }
    }
}

/** First load: the post in outline. Never scrollable and silent to TalkBack. */
@Composable
private fun PostDetailSkeleton(pageWidth: Dp, modifier: Modifier = Modifier) {
    val inset = PostMetrics.inset(pageWidth)
    Box(modifier.fillMaxWidth().testTag("post-detail-loading").clearAndSetSemantics {}, contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = PostMetrics.MaxWidth).fillMaxWidth()
                .verticalScroll(rememberScrollState(), enabled = false).padding(horizontal = inset),
        ) {
            Row(Modifier.padding(top = PostMetrics.HeaderTop), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(Modifier.size(PostMetrics.Avatar), CircleShape)
                Spacer(Modifier.width(PostMetrics.NameStart - PostMetrics.Avatar))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
                    SkeletonLine(fraction = 0.4f)
                    SkeletonLine(fraction = 0.25f, height = 12.dp)
                }
            }
            Spacer(Modifier.height(MinSpace.lg))
            SkeletonLine(fraction = 0.95f)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.85f)
            Spacer(Modifier.height(MinSpace.sm))
            SkeletonLine(fraction = 0.6f)
            Spacer(Modifier.height(MinSpace.lg))
            SkeletonBox(Modifier.widthIn(max = PostMetrics.ImageMax).fillMaxWidth().aspectRatio(1f), MinRadius.Card)
        }
    }
}

@Composable
private fun PostDetailFailed(onAction: (PostDetailAction) -> Unit) {
    StatePage {
        SectionState(
            stringResource(R.string.ui_this_post_could_not_be_loaded), tag = "post-detail-failed",
            message = stringResource(R.string.ui_please_try_again), kind = StateKind.Error,
            actionLabel = stringResource(R.string.retry), actionTag = "post-detail-retry",
            onAction = { onAction(PostDetailAction.Retry) },
        )
    }
}
