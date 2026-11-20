package com.hhst.youtubelite.post.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.ui.design.AppIcon
import com.hhst.youtubelite.ui.design.AppIconButton
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.WindowClass
import com.hhst.youtubelite.watch.domain.VideoRating

/**
 * Geometry shared by the post page and the post cards of a channel, so a post looks the same in
 * both places. Posts are text first: on wide windows they keep a reading width and sit centered.
 */
internal object PostMetrics {
    /** Reading width of a post. */
    val MaxWidth = 720.dp
    val Avatar = 40.dp
    /** The avatar sits at the start of a 48dp touch slot. */
    val AvatarSlot = MinSpace.Touch
    /** Distance from the content edge to the author name. */
    val NameStart = AvatarSlot + MinSpace.xs
    val HeaderTop = MinSpace.md
    /** Largest side of a post image. */
    val ImageMax = 480.dp
    /** How much of the next image shows when a post has several. */
    val ImagePeek = MinSpace.xxxl

    fun inset(width: Dp): Dp = MinSpace.pageInset(WindowClass.of(width.value.toInt()))

    /** Left edge of the centered post column inside a window of [width]. */
    fun columnStart(width: Dp): Dp = if (width > MaxWidth) (width - MaxWidth) / 2 else 0.dp
}

/** Centers [content] at the post reading width. */
@Composable
internal fun PostColumn(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = PostMetrics.MaxWidth).fillMaxWidth(), content = content)
    }
}

/**
 * Author line of a post: avatar, name, age and an optional trailing control. The avatar is its own
 * 48dp button when [onAvatarClick] is given; [onTextClick] makes the name block a button.
 */
@Composable
internal fun PostAuthorRow(
    name: String,
    age: String,
    avatar: String,
    inset: Dp,
    modifier: Modifier = Modifier,
    nameTag: String? = null,
    avatarTag: String? = null,
    avatarDescription: String? = null,
    onAvatarClick: (() -> Unit)? = null,
    onTextClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = inset, top = PostMetrics.HeaderTop), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(PostMetrics.AvatarSlot)
                .then(if (onAvatarClick != null) Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onAvatarClick) else Modifier)
                .then(if (avatarDescription != null) Modifier.semantics { contentDescription = avatarDescription } else Modifier),
            contentAlignment = Alignment.TopStart,
        ) {
            com.hhst.youtubelite.ui.components.MinAvatar(avatar, name,
                if (avatarTag != null) Modifier.testTag(avatarTag) else Modifier, size = PostMetrics.Avatar)
        }
        Spacer(Modifier.width(PostMetrics.NameStart - PostMetrics.AvatarSlot))
        Column(
            Modifier.weight(1f).heightIn(min = PostMetrics.Avatar)
                .then(if (onTextClick != null) Modifier.clickable(role = Role.Button, onClick = onTextClick) else Modifier),
        ) {
            AppText(name, MinType.Label, if (nameTag != null) Modifier.testTag(nameTag) else Modifier, maxLines = 2)
            if (age.isNotBlank()) AppText(age, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = DesignTokens.Secondary, maxLines = 1)
        }
        if (trailing != null) trailing() else Spacer(Modifier.width(inset))
    }
}

/**
 * Like, dislike and comments of a post. The rating buttons report `selected`, show progress for
 * the one write in flight and ignore taps while any write is pending; the write itself is the
 * caller's.
 */
@Composable
internal fun PostRatingRow(
    likes: String,
    comments: String,
    rating: VideoRating,
    enabled: Boolean,
    likePending: Boolean,
    dislikePending: Boolean,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onComments: () -> Unit,
    likeTag: String,
    dislikeTag: String,
    commentsTag: String,
    modifier: Modifier = Modifier,
) {
    val liked = rating == VideoRating.Like
    val disliked = rating == VideoRating.Dislike
    Row(modifier.fillMaxWidth().heightIn(min = MinSpace.Touch), verticalAlignment = Alignment.CenterVertically) {
        AppIconButton(
            if (liked) AppIcon.LikeFilled else AppIcon.Like,
            stringResource(if (liked) R.string.ui_unlike_post else R.string.a11y_like_post),
            modifier = Modifier.testTag(likeTag).semantics { selected = liked },
            enabled = enabled, pending = likePending,
            background = if (liked) DesignTokens.AccentContainer else Color.Transparent,
            color = if (liked) DesignTokens.AccentText else DesignTokens.Ink,
            onClick = onLike,
        )
        AppText(likes, MinType.Label, Modifier.widthIn(min = MinSpace.xxxl).padding(horizontal = MinSpace.xs), maxLines = 1)
        AppIconButton(
            if (disliked) AppIcon.DislikeFilled else AppIcon.Dislike,
            stringResource(if (disliked) R.string.ui_remove_dislike else R.string.a11y_dislike_post),
            modifier = Modifier.testTag(dislikeTag).semantics { selected = disliked },
            enabled = enabled, pending = dislikePending,
            background = if (disliked) DesignTokens.Surface else Color.Transparent,
            onClick = onDislike,
        )
        Spacer(Modifier.weight(1f))
        val commentsLabel = stringResource(R.string.comments_title)
        Row(
            Modifier.clip(CircleShape).clickable(role = Role.Button, onClick = onComments)
                .heightIn(min = MinSpace.Touch).widthIn(min = MinSpace.Touch).padding(horizontal = MinSpace.md)
                .testTag(commentsTag).semantics { contentDescription = commentsLabel },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(AppIcon.Comment, Modifier.size(20.dp))
            if (comments.isNotBlank()) AppText(comments, MinType.Label, Modifier.padding(start = MinSpace.sm), maxLines = 1)
        }
    }
}
