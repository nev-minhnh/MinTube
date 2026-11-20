package com.hhst.youtubelite.channel.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.ChannelAction
import com.hhst.youtubelite.channel.domain.ChannelPage
import com.hhst.youtubelite.channel.domain.ChannelProfile
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType
import com.hhst.youtubelite.ui.design.SubscribeButton
import com.hhst.youtubelite.ui.design.WindowClass

/**
 * Geometry of the channel page. Everything derives from the Mint window classes (compact below
 * 600dp, medium to 839dp, expanded from 840dp) of the width the page really has.
 */
internal object ChannelMetrics {
    fun window(width: Float): WindowClass = WindowClass.of(width.toInt())

    fun inset(width: Float): Dp = MinSpace.pageInset(window(width))

    fun avatar(width: Float): Dp = when (window(width)) {
        WindowClass.Compact -> 72.dp
        WindowClass.Medium -> 96.dp
        WindowClass.Expanded -> 120.dp
    }

    /** Videos, live streams and search results: a list on phones, a card grid from 600dp. */
    fun videoColumns(width: Float): Int = window(width).feedColumns

    fun shortsColumns(width: Float): Int = when (window(width)) {
        WindowClass.Compact -> 2
        WindowClass.Medium -> 3
        WindowClass.Expanded -> 5
    }

    fun playlistColumns(width: Float): Int = window(width).feedColumns + 1

    val GridGap = MinSpace.lg
    val AvatarGap = MinSpace.lg

    /** Width of one grid cell, for tests and placeholders. */
    fun cellWidth(width: Float, columns: Int): Dp = (width.dp - inset(width) * 2 - GridGap * (columns - 1)) / columns

    /** Thumbnail width of a list row; narrow or large-text windows give the text more room. */
    fun rowThumbnail(width: Float, fontScale: Float): Dp {
        val available = (width.dp - inset(width) * 2).coerceAtLeast(1.dp)
        return minOf(160.dp, available * if (fontScale > 1.3f) 0.36f else 0.46f)
    }

    /** Banner width over height: a taller strip on phones, the wide cut on larger windows. */
    fun bannerAspect(width: Float): Float = if (window(width) == WindowClass.Compact) 4f else 6.2f

    /** Lines of the description teaser before "...more". */
    const val DescriptionLines = 2

    /** Longest line of the description and links beside a large avatar. */
    val DetailsMaxWidth = 600.dp
}

/**
 * Banner, avatar, name, handle and counts, the description teaser that opens About, links when the
 * channel has any, and the subscribe button. On phones the details sit under the avatar row; from
 * 600dp they sit beside the avatar.
 */
@Composable
internal fun ChannelHeader(page: ChannelPage, width: Float, onAction: (ChannelAction) -> Unit,
    subscribePending: Boolean = false,
) {
    val profile = page.profile
    val compact = ChannelMetrics.window(width) == WindowClass.Compact
    val inset = ChannelMetrics.inset(width)
    Column(Modifier.fillMaxWidth().testTag("channel-header")) {
        profile.banner?.let { banner ->
            Box(Modifier.padding(start = inset, end = inset, top = MinSpace.sm).fillMaxWidth()
                .aspectRatio(ChannelMetrics.bannerAspect(width)).clip(MinRadius.Card).testTag("channel-banner")) {
                Artwork(banner, Modifier.fillMaxSize())
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = inset, end = inset, top = MinSpace.lg)) {
            Artwork(profile.avatar, Modifier.size(ChannelMetrics.avatar(width)).clip(CircleShape).testTag("channel-avatar"))
            Spacer(Modifier.width(ChannelMetrics.AvatarGap))
            Column(Modifier.weight(1f)) {
                AppText(profile.name, MinType.Display, Modifier.fillMaxWidth().testTag("channel-name").semantics { heading() }, maxLines = 3)
                ChannelIdentity(profile)
                if (!compact) ChannelDetails(profile, onAction, subscribePending)
            }
        }
        if (compact) Column(Modifier.fillMaxWidth().padding(horizontal = inset)) { ChannelDetails(profile, onAction, subscribePending) }
        Spacer(Modifier.height(MinSpace.sm))
    }
}

/** Handle on its own line, then subscribers and video count; either line is skipped when blank. */
@Composable
private fun ChannelIdentity(profile: ChannelProfile) {
    Column(Modifier.fillMaxWidth().padding(top = MinSpace.xs).testTag("channel-identity")) {
        if (profile.handle.isNotBlank()) AppText(profile.handle, MinType.Label, Modifier.testTag("channel-handle"), maxLines = 1)
        val stats = listOf(profile.subscribers, profile.videoCount).filter { it.isNotBlank() }.joinToString(" · ")
        if (stats.isNotBlank()) AppText(stats, MinType.Caption, Modifier.padding(top = MinSpace.xxs), color = DesignTokens.Secondary, maxLines = 2)
    }
}

@Composable
private fun ChannelDetails(profile: ChannelProfile, onAction: (ChannelAction) -> Unit, subscribePending: Boolean) {
    Column(Modifier.widthIn(max = ChannelMetrics.DetailsMaxWidth).fillMaxWidth()) {
        // Always drawn: "...more" is the way into About even when the teaser text is empty.
        ChannelDescription(profile.description, onAction)
        // Links come from the page itself; a channel without them shows no row at all.
        if (profile.links.isNotEmpty()) ChannelLinks(profile, onAction)
        Row(Modifier.padding(top = MinSpace.xs), horizontalArrangement = Arrangement.Start) {
            SubscribeButton(
                subscribed = profile.subscribed == true,
                onClick = { onAction(ChannelAction.Subscribe) },
                pending = subscribePending,
                testTag = "channel-subscribe",
                channelId = profile.channelId,
            )
        }
    }
}

/**
 * Two lines of the description that always end in a visible "...more": the text is cut where the
 * suffix still fits, so the affordance is never ellipsized away. Opens the About panel.
 */
@Composable
private fun ChannelDescription(description: String, onAction: (ChannelAction) -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = MinType.Body.copy(color = DesignTokens.Secondary)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(top = MinSpace.xs).heightIn(min = MinSpace.Touch).testTag("channel-description")
        .clickable(role = Role.Button) { onAction(ChannelAction.OpenFullDescription) }, contentAlignment = Alignment.CenterStart) {
        val limit = ChannelMetrics.DescriptionLines
        val ink = DesignTokens.Ink
        val text = remember(description, constraints.maxWidth, style, density.fontScale, ink) {
            fun summary(end: Int) = buildAnnotatedString {
                append(description.take(end).trimEnd())
                if (end > 0) append(" ")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, color = ink)) { append("...more") }
            }
            var low = 0
            var high = description.length
            while (low < high) {
                val middle = (low + high + 1) / 2
                val result = measurer.measure(summary(middle), style, maxLines = limit,
                    constraints = Constraints(maxWidth = constraints.maxWidth))
                if (result.hasVisualOverflow) high = middle - 1 else low = middle
            }
            summary(low)
        }
        BasicText(text, style = style, maxLines = limit, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun ChannelLinks(profile: ChannelProfile, onAction: (ChannelAction) -> Unit) {
    val first = profile.links.firstOrNull() ?: return
    Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).testTag("channel-links")
        .clickable(role = Role.Button) { onAction(ChannelAction.OpenFullDescription) },
        verticalAlignment = Alignment.CenterVertically) {
        AppText(first, MinType.Body, Modifier.weight(1f, fill = false), color = DesignTokens.Link, maxLines = 1)
        val extra = profile.links.size - 1
        if (extra > 0) {
            Spacer(Modifier.width(MinSpace.xs))
            AppText(pluralStringResource(R.plurals.channel_more_links, extra, extra), MinType.Label, maxLines = 1)
        }
    }
}
