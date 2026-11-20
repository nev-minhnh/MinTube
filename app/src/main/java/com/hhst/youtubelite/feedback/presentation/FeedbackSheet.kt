package com.hhst.youtubelite.feedback.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.design.AppText
import com.hhst.youtubelite.ui.design.MenuBottomSheet
import com.hhst.youtubelite.ui.design.MenuSheetItem
import com.hhst.youtubelite.ui.design.MenuSheetLayout
import com.hhst.youtubelite.ui.design.MinSpace
import com.hhst.youtubelite.ui.design.MinType

data class FeedbackReason(val label: String, val tag: String, @get:androidx.annotation.StringRes val labelResource: Int? = null)

/** "Tell us why" reasons for a dismissed recommendation, in the shared Mint menu sheet. */
@Composable
fun FeedbackSheet(
    title: String,
    reasons: List<FeedbackReason>,
    onDismiss: () -> Unit,
    onSelect: (FeedbackReason) -> Unit,
) {
    MenuBottomSheet(
        items = reasons.map {
            MenuSheetItem(null, it.labelResource?.let { id -> stringResource(id) } ?: it.label, it.tag)
        },
        onDismiss = onDismiss,
        onSelect = { index -> if (index in reasons.indices) onSelect(reasons[index]) },
        tag = "feedback-sheet",
        paneTitle = title,
        header = {
            AppText(title, MinType.Title,
                Modifier.fillMaxWidth().testTag("feedback-title").semantics { heading() }
                    .padding(start = MenuSheetLayout.RowStart.dp, end = MenuSheetLayout.RowStart.dp,
                        top = MinSpace.xs, bottom = MinSpace.sm))
        },
    )
}
