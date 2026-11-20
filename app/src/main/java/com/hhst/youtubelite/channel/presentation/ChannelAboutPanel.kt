package com.hhst.youtubelite.channel.presentation

import com.hhst.youtubelite.ui.design.appPaneTitle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.R
import com.hhst.youtubelite.channel.domain.*
import com.hhst.youtubelite.ui.design.*
import androidx.compose.ui.res.stringResource

object ChannelAboutLayout {
    fun docked(width: Float, height: Float) = width >= 931f && width > height
    fun panelWidth(width: Float, height: Float) = if (!docked(width, height)) width else if (width >= 1280f) 500f else 256f
    fun top(width: Float, height: Float): Float {
        val sampled = if (docked(width, height)) 48f else if (width > height && width > 320f) width * .5625f - 48f else 48f + width * .5625f
        return sampled.coerceIn(0f, (height - 200f).coerceAtLeast(0f))
    }
}

/**
 * The About sheet: description, links when the channel has any, and the details the live mapping
 * fills (only non-blank rows are drawn, so nothing is a placeholder). A bottom sheet on phones and
 * portrait tablets, a panel docked at the end edge on wide landscape windows.
 */
@Composable
fun ChannelAboutPanel(profile: ChannelProfile, width: Float, height: Float, onAction: (ChannelAction) -> Unit,
    scroll: ScrollState = rememberScrollState()) {
    val docked = ChannelAboutLayout.docked(width, height)
    val top = ChannelAboutLayout.top(width, height)
    val square = width > height && (width >= 931 || width * 9 >= height * 13)
    val inset = MinSpace.pageInset(WindowClass.of(ChannelAboutLayout.panelWidth(width, height).toInt()))
    PanelTransition(fromBottom = !docked, onDismiss = { onAction(ChannelAction.CloseOverlay) },
        scrim = { scrimMotion, close ->
            Box(Modifier.fillMaxSize().then(scrimMotion).background(DesignTokens.Scrim)
                .clickable(role = Role.Button) { close() }.testTag("channel-about-scrim"))
            com.hhst.youtubelite.ui.components.CoverNavigationRail(DesignTokens.Scrim, onDismiss = close)
        }) { panelMotion, close ->
    val drag = rememberBottomSheetDrag(enabled = !docked, onDismiss = close)
    val corner = if (square) 0.dp else MinRadius.lg
    Column(Modifier.offset(x = if (docked) (width - ChannelAboutLayout.panelWidth(width, height)).dp else 0.dp, y = top.dp)
        .height((height - top).dp).width(ChannelAboutLayout.panelWidth(width, height).dp)
        .then(panelMotion)
        .then(drag.panelModifier)
        .clip(RoundedCornerShape(topStart = corner, topEnd = corner))
        .background(DesignTokens.Panel).pointerInput(Unit) { detectTapGestures {} }
        .appPaneTitle(R.string.download_size_estimate, profile.name).testTag("channel-about-panel")) {
        Column(Modifier.fillMaxWidth().then(drag.handleModifier).testTag("channel-about-drag-header")) {
            Box(Modifier.fillMaxWidth().padding(top = MinSpace.sm), contentAlignment = Alignment.TopCenter) {
                if (!square) Box(Modifier.size(32.dp, 4.dp).background(DesignTokens.Ghost, CircleShape))
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = inset, end = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                AppText(profile.name, MinType.Title, Modifier.weight(1f).semantics { heading() }, maxLines = 2)
                AppIconButton(AppIcon.Close, stringResource(R.string.a11y_close_channel_details)) { close() }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(DesignTokens.Border))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = inset).testTag("channel-about-content")) {
            if (profile.fullDescription.isNotBlank()) {
                AboutHeading(stringResource(R.string.video_description))
                AppText(profile.fullDescription, MinType.Body)
            }
            if (profile.links.isNotEmpty()) {
                AboutHeading(stringResource(R.string.ui_links))
                profile.links.forEach { link ->
                    Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).clip(MinRadius.Field)
                        .clickable(role = Role.Button) { onAction(ChannelAction.OpenLink("https://$link")) }
                        .padding(vertical = MinSpace.xs), verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(AppIcon.Link, Modifier.size(20.dp), color = DesignTokens.Secondary)
                        Spacer(Modifier.width(MinSpace.lg))
                        AppText(link, MinType.Body, Modifier.weight(1f), color = DesignTokens.Link, maxLines = 2)
                    }
                }
            }
            AboutHeading(stringResource(R.string.ui_more_info))
            val details = listOf(AppIcon.Globe to profile.canonicalUrl.removePrefix("https://"), AppIcon.Globe to profile.country,
                AppIcon.Info to profile.joined, AppIcon.Audience to profile.subscribers, AppIcon.VideoOutline to profile.videoCount, AppIcon.Trending to profile.views)
            details.filter { it.second.isNotEmpty() }.forEachIndexed { index, (icon, value) ->
                Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).testTag("channel-about-detail-$index"), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(icon, modifier = Modifier.size(20.dp).testTag("channel-about-detail-icon-$index"), color = DesignTokens.Secondary)
                    Spacer(Modifier.width(MinSpace.lg))
                    AppText(value, MinType.Body, Modifier.weight(1f).testTag("channel-about-detail-value-$index"),
                        maxLines = if (LocalDensity.current.fontScale > 1.3f) 3 else 2)
                }
            }
            Spacer(Modifier.height(MinSpace.xxxl))
        }
    }
    }
}

@Composable
private fun AboutHeading(text: String) {
    AppText(text, MinType.Subtitle, Modifier.fillMaxWidth().padding(top = MinSpace.xl, bottom = MinSpace.sm).semantics { heading() }, maxLines = 2)
}
