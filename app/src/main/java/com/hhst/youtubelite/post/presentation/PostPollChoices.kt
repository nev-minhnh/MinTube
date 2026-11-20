package com.hhst.youtubelite.post.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.post.domain.PostPollData
import com.hhst.youtubelite.ui.design.ActionProgress
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.Artwork
import com.hhst.youtubelite.ui.design.DesignTokens
import com.hhst.youtubelite.ui.design.MinRadius
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

/**
 * Poll options of a post. The chosen option is tinted; percentages appear once the viewer has
 * voted. Options ignore taps while a vote is in flight, and the one being sent shows progress.
 */
@Composable
fun PostPollChoices(poll: PostPollData, pending: Boolean, modifier: Modifier = Modifier,
    pendingOption: Int? = null, onVote: (Int) -> Unit) {
    Column(modifier.fillMaxWidth().testTag("post-poll"), verticalArrangement = Arrangement.spacedBy(MinSpace.sm)) {
        poll.options.forEachIndexed { index, option ->
            Row(Modifier.fillMaxWidth().heightIn(min = MinSpace.Touch).clip(MinRadius.Field)
                .background(if (option.selected) DesignTokens.AccentContainer else DesignTokens.Surface)
                .then(if (option.selected) Modifier.border(1.5.dp, DesignTokens.Accent, MinRadius.Field) else Modifier)
                .clickable(enabled = !pending, role = Role.RadioButton) { onVote(index) }
                .semantics { selected = option.selected }.testTag("post-poll-option-$index")
                .padding(horizontal = MinSpace.md, vertical = MinSpace.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MinSpace.md)) {
                option.image?.let { Artwork(it, Modifier.size(64.dp).clip(MinRadius.Badge)) }
                AppText(option.text, MinType.Body, Modifier.weight(1f))
                // Keep the same trailing slot before, during and after the vote.
                Box(Modifier.defaultMinSize(minWidth = MinSpace.huge, minHeight = MinSpace.xxl), contentAlignment = Alignment.CenterEnd) {
                    if (pendingOption == index) ActionProgress()
                    else if (poll.voted) option.percentage?.let { AppText(it, MinType.Label, maxLines = 1) }
                }
            }
        }
        if (poll.total.isNotBlank()) AppText(poll.total, MinType.Caption, color = DesignTokens.Secondary)
    }
}
