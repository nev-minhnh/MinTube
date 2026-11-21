package com.hhst.youtubelite.tv.qr

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [text] as a QR code: black modules on white with the standard 4-module quiet zone, so phone
 * cameras read it from a dark TV screen. Not focusable. Draws nothing if the text is too long.
 */
@Composable
fun TvQrImage(text: String, contentDescription: String, modifier: Modifier = Modifier, size: Dp = 200.dp) {
    val matrix = remember(text) { TvQrCode.encode(text) }
    Canvas(modifier.size(size).semantics { this.contentDescription = contentDescription }) {
        drawRect(Color.White)
        val qr = matrix ?: return@Canvas
        val cells = qr.size + QUIET_ZONE * 2
        val cell = this.size.minDimension / cells
        for (y in 0 until qr.size) for (x in 0 until qr.size) {
            if (qr.isDark(x, y)) {
                // A hair of overlap avoids anti-aliased seams between neighbouring modules.
                drawRect(Color.Black, Offset((x + QUIET_ZONE) * cell, (y + QUIET_ZONE) * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private const val QUIET_ZONE = 4
