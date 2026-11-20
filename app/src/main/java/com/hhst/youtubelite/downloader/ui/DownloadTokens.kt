package com.hhst.youtubelite.downloader.ui

import androidx.compose.ui.unit.dp
import com.hhst.youtubelite.ui.design.MinSpace

/** Downloader layout constants on the Mint scale (spacing, 48dp targets, 24dp sheet tops). */
object DownloadTokens {
    const val ROW_ACTION_WIDTH_DP = 48
    const val SHEET_CORNER_DP = 24
    const val TITLE_MAX_LINES = 2
    const val THUMB_ASPECT_W = 16
    const val THUMB_ASPECT_H = 9
    const val CAPSULE_DP = 20
    const val DIVIDER_ALPHA = 1f

    val PageInset = MinSpace.lg
    val Icon = 24.dp
    val MinTouch = MinSpace.Touch
    val RowActionWidth = ROW_ACTION_WIDTH_DP.dp
    val SheetCorner = SHEET_CORNER_DP.dp
    val Capsule = CAPSULE_DP.dp
    val ThumbAspect = THUMB_ASPECT_W.toFloat() / THUMB_ASPECT_H.toFloat()

    /** Sheets stop growing at this width and center on wide windows. */
    val SheetMaxWidth = 480.dp
}
