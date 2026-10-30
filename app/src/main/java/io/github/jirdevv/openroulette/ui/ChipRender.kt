// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import org.fossroulette.openroulette.data.ChipStyle

private val ChipPalette = listOf(
    Color(0xFFEEEEEE), Color(0xFFD32F2F), Color(0xFF2E7D32), Color(0xFF212121),
    Color(0xFF7B1FA2), Color(0xFFF9A825), Color(0xFF1565C0), Color(0xFF00838F),
)

fun chipColor(index: Int): Color = ChipPalette[index.mod(ChipPalette.size)]

/** 1500 -> "1.5k", 2_000_000 -> "2M". */
fun compact(v: Long): String = when {
    v >= 1_000_000 -> if (v % 1_000_000 == 0L) "${v / 1_000_000}M" else String.format(Locale.ROOT, "%.1fM", v / 1e6)
    v >= 1_000 -> if (v % 1_000 == 0L) "${v / 1_000}k" else String.format(Locale.ROOT, "%.1fk", v / 1e3)
    else -> v.toString()
}

/** Draws one chip. Pure vector drawing: no bitmap assets needed. */
fun DrawScope.drawChip(
    center: Offset, radius: Float, color: Color, style: ChipStyle, label: String?, measurer: TextMeasurer,
) {
    val light = color.luminance() > 0.5f
    val edge = if (light) Color(0xFF333333) else Color.White
    drawCircle(Color.Black.copy(alpha = 0.35f), radius, center + Offset(radius * 0.06f, radius * 0.1f))
    when (style) {
        ChipStyle.CLASSIC -> {
            drawCircle(color, radius, center)
            val r = radius * 0.86f
            for (k in 0 until 8) {
                drawArc(edge, k * 45f + 6f, 24f, false, center - Offset(r, r), Size(r * 2, r * 2), style = Stroke(radius * 0.2f))
            }
            drawCircle(edge, radius * 0.62f, center, style = Stroke(radius * 0.05f))
        }
        ChipStyle.FLAT -> {
            drawCircle(color, radius, center)
            drawCircle(edge, radius * 0.86f, center, style = Stroke(radius * 0.06f))
        }
        ChipStyle.OUTLINE -> {
            drawCircle(Color(0xFF121212), radius, center)
            drawCircle(color, radius * 0.88f, center, style = Stroke(radius * 0.2f))
        }
    }
    if (label != null) {
        val textColor = if (style == ChipStyle.OUTLINE) Color.White else if (light) Color.Black else Color.White
        val layout = measurer.measure(label, TextStyle(color = textColor, fontSize = (radius * 0.8f).toSp(), fontWeight = FontWeight.Bold))
        drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
    }
}
