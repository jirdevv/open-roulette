// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import org.fossroulette.openroulette.data.ChipStyle
import org.fossroulette.openroulette.data.FeltTheme
import org.fossroulette.openroulette.domain.BetSpot
import org.fossroulette.openroulette.domain.BetType
import org.fossroulette.openroulette.domain.PocketColor
import org.fossroulette.openroulette.domain.TableLayoutFactory
import org.fossroulette.openroulette.domain.TableLayoutFactory.GRID_H
import org.fossroulette.openroulette.domain.TableLayoutFactory.GRID_W
import org.fossroulette.openroulette.domain.decomposeChips
import org.fossroulette.openroulette.domain.pocketColor
import org.fossroulette.openroulette.ui.theme.FeltPalette
import org.fossroulette.openroulette.ui.theme.palette

/**
 * The interactive felt. One Canvas draws cells + chips; hit-testing is done in grid units against
 * [BetSpot.rect] (smallest rectangle wins, so corners/splits are reachable without per-spot views).
 *
 *  - tap        : place the selected chip
 *  - long press : remove the most recent chip from that spot
 *  - horizontal scroll on narrow screens (cells never get smaller than ~40dp unless height-limited)
 */
@Composable
fun BettingTable(
    spots: List<BetSpot>,
    bets: Map<String, Long>,
    prison: Map<String, Long>,
    denominations: List<Long>,
    felt: FeltTheme,
    chipStyle: ChipStyle,
    winningPocket: Int?,
    enabled: Boolean,
    onPlace: (String) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = felt.palette()
    val measurer = rememberTextMeasurer()
    val spotById = remember(spots) { spots.associateBy { it.id } }
    val isEnabled by rememberUpdatedState(enabled)
    val place by rememberUpdatedState(onPlace)
    val remove by rememberUpdatedState(onRemove)
    // Straight-up + outside bets are exposed as TalkBack custom actions (Canvas has no per-spot nodes).
    val a11ySpots = remember(spots) { spots.filter { it.isCell } }
    val total = bets.values.sum()

    BoxWithConstraints(modifier) {
        val byWidth = maxWidth / GRID_W
        val cell = if (constraints.hasBoundedHeight) {
            minOf(byWidth, maxHeight / GRID_H).coerceAtLeast(26.dp)
        } else {
            maxOf(byWidth, 40.dp)
        }
        val tableW = cell * GRID_W
        val tableH = cell * GRID_H

        Canvas(
            Modifier
                .align(Alignment.TopCenter)
                .width(minOf(tableW, maxWidth))
                .horizontalScroll(rememberScrollState())
                .size(tableW, tableH)
                .semantics {
                    contentDescription = "Betting table. Total bet $total. Tap a spot to place a chip, long press to remove."
                    customActions = a11ySpots.map { s ->
                        CustomAccessibilityAction("Bet on ${s.describe()}") { place(s.id); true }
                    }
                }
                .pointerInput(spots) {
                    detectTapGestures(
                        onTap = { o ->
                            val cp = size.width / GRID_W
                            if (isEnabled) TableLayoutFactory.hitTest(spots, o.x / cp, o.y / cp)?.let { place(it.id) }
                        },
                        onLongPress = { o ->
                            val cp = size.width / GRID_W
                            if (isEnabled) TableLayoutFactory.hitTest(spots, o.x / cp, o.y / cp)?.let { remove(it.id) }
                        },
                    )
                },
        ) {
            val cp = size.width / GRID_W
            drawRect(palette.felt)
            drawCells(spots, palette, cp, measurer)
            winningPocket?.let { p ->
                spots.firstOrNull { it.type == BetType.STRAIGHT && it.numbers == setOf(p) }?.let { s ->
                    drawRect(palette.highlight, Offset(s.rect.x * cp, s.rect.y * cp), Size(s.rect.w * cp, s.rect.h * cp), style = Stroke(cp * 0.09f))
                }
            }
            val radius = cp * 0.34f
            prison.forEach { (id, amount) ->
                spotById[id]?.let { drawStack(Offset(it.rect.cx * cp, it.rect.cy * cp), radius, amount, denominations, chipStyle, measurer, palette.highlight) }
            }
            bets.forEach { (id, amount) ->
                spotById[id]?.let { drawStack(Offset(it.rect.cx * cp, it.rect.cy * cp), radius, amount, denominations, chipStyle, measurer, null) }
            }
        }
    }
}

private fun DrawScope.drawCells(spots: List<BetSpot>, palette: FeltPalette, cp: Float, measurer: TextMeasurer) {
    val style = TextStyle(color = palette.text, fontSize = (cp * 0.34f).toSp(), fontWeight = FontWeight.Bold)
    for (s in spots) {
        if (!s.isCell) continue
        val tl = Offset(s.rect.x * cp, s.rect.y * cp)
        val sz = Size(s.rect.w * cp, s.rect.h * cp)
        val fill: Color = when {
            s.type == BetType.STRAIGHT -> when (pocketColor(s.numbers.first())) {
                PocketColor.RED -> palette.red
                PocketColor.BLACK -> palette.black
                PocketColor.GREEN -> palette.zero
            }
            s.tint == PocketColor.RED -> palette.red
            s.tint == PocketColor.BLACK -> palette.black
            else -> Color.Transparent
        }
        if (fill != Color.Transparent) drawRect(fill, tl, sz)
        drawRect(palette.line, tl, sz, style = Stroke(cp * 0.03f))
        val layout = measurer.measure(s.label, style)
        drawText(layout, topLeft = Offset(tl.x + (sz.width - layout.size.width) / 2f, tl.y + (sz.height - layout.size.height) / 2f))
    }
}

private fun DrawScope.drawStack(
    center: Offset, radius: Float, amount: Long, denominations: List<Long>,
    style: ChipStyle, measurer: TextMeasurer, prisonRing: Color?,
) {
    val chips = decomposeChips(amount, denominations).take(5) // largest first = bottom of the stack
    var top = center
    chips.forEachIndexed { i, v ->
        top = center + Offset(0f, -i * radius * 0.16f)
        val idx = denominations.indexOf(v).coerceAtLeast(0)
        drawChip(top, radius, chipColor(idx), style, if (i == chips.lastIndex) compact(amount) else null, measurer)
    }
    if (prisonRing != null) drawCircle(prisonRing, radius * 1.1f, top, style = Stroke(radius * 0.14f))
}
