// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.fossroulette.openroulette.domain.PocketColor
import org.fossroulette.openroulette.domain.TableType
import org.fossroulette.openroulette.domain.pocketColor
import org.fossroulette.openroulette.domain.pocketLabel

/** Request to visualise a spin whose outcome ([pocket]) was already drawn by the CSPRNG. */
data class SpinRequest(val id: Long, val pocket: Int, val durationMs: Int)

/**
 * Animation plan. Angles are degrees, clockwise from 12 o'clock.
 *
 * Wheel turns clockwise with quadratic ease-out; ball runs counter-clockwise with a steeper cubic
 * ease-out (it loses speed faster, like the real thing). The ball's total travel is solved so that at
 * t = 1 it sits exactly on the winning pocket in the wheel's rotating frame:
 *     ballStart - ballTotal  ==  wheelEnd + pocketAngle   (mod 360)
 * Randomness here is cosmetic only; it can never change which pocket wins.
 */
private class SpinPlan(
    val wheelStart: Float, val wheelTotal: Float,
    val ballStart: Float, val ballTotal: Float,
    val active: Boolean,
) {
    val wheelEnd: Float get() = wheelStart + wheelTotal
    val ballEnd: Float get() = ballStart - ballTotal

    fun wheelAt(t: Float): Float { val k = 1f - t; return wheelStart + wheelTotal * (1f - k * k) }
    fun ballAt(t: Float): Float { val k = 1f - t; return ballStart - ballTotal * (1f - k * k * k) }

    companion object {
        val REST = SpinPlan(0f, 0f, 0f, 0f, active = false)

        fun create(previous: SpinPlan, pocketAngle: Float): SpinPlan {
            val wheelStart = previous.wheelEnd % 360f
            val wheelTotal = 360f * (3f + Random.nextFloat() * 1.5f)
            val ballStart = previous.ballEnd % 360f
            val target = wheelStart + wheelTotal + pocketAngle
            val delta = (((ballStart - target) % 360f) + 360f) % 360f
            val ballTotal = delta + 360f * (6 + Random.nextInt(3))
            return SpinPlan(wheelStart, wheelTotal, ballStart, ballTotal, active = true)
        }
    }
}

/** Ball distance from centre as a fraction of the wheel radius: orbit, drop into the pockets, small bounces. */
private fun ballRadius(t: Float): Float {
    val outer = 0.89f
    val inner = 0.70f
    val drop = ((t - 0.55f) / 0.35f).coerceIn(0f, 1f)
    val base = outer + (inner - outer) * drop * drop
    val bounce = if (t > 0.88f) sin((t - 0.88f) * 70f) * (1f - t) * 0.2f else 0f
    return base + bounce
}

@Composable
fun RouletteWheel(table: TableType, spin: SpinRequest?, modifier: Modifier = Modifier) {
    val order = table.wheelOrder
    val progress = remember { Animatable(1f) }
    var plan by remember(table) { mutableStateOf(SpinPlan.REST) }
    val measurer = rememberTextMeasurer()

    LaunchedEffect(spin?.id) {
        val req = spin ?: return@LaunchedEffect
        val index = order.indexOf(req.pocket)
        plan = SpinPlan.create(plan, index * 360f / order.size)
        progress.snapTo(0f)
        progress.animateTo(1f, tween(req.durationMs, easing = LinearEasing))
    }

    Box(modifier.aspectRatio(1f).semantics { contentDescription = "${table.displayName} roulette wheel" }) {
        // Layer 1: static rim and ball track.
        Canvas(Modifier.fillMaxSize()) { drawRim() }
        // Layer 2: pockets + hub. Rotated by the GPU (graphicsLayer) so it is NOT redrawn per frame.
        Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = plan.wheelAt(progress.value) }) {
            drawPockets(order, measurer)
        }
        // Layer 3: ball.
        Canvas(Modifier.fillMaxSize()) {
            val p = plan
            if (!p.active) return@Canvas
            val t = progress.value
            val r = size.minDimension / 2f
            val a = Math.toRadians(p.ballAt(t).toDouble())
            val dist = r * ballRadius(t)
            val c = Offset(size.width / 2f + (dist * sin(a)).toFloat(), size.height / 2f - (dist * cos(a)).toFloat())
            drawCircle(Color(0x66000000), r * 0.04f, c + Offset(r * 0.015f, r * 0.02f))
            drawCircle(Color(0xFFF5F5F5), r * 0.035f, c)
        }
    }
}

private fun DrawScope.drawRim() {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    drawCircle(Color(0xFF4E342E), r, c)
    drawCircle(Color(0xFF8D6E63), r * 0.975f, c, style = Stroke(r * 0.02f))
    drawCircle(Color(0xFF1B1410), r * 0.93f, c) // ball track
}

private fun DrawScope.drawPockets(order: List<Int>, measurer: TextMeasurer) {
    val r = size.minDimension / 2f
    val c = Offset(size.width / 2f, size.height / 2f)
    val step = 360f / order.size
    val ringMid = r * 0.705f
    val ringWidth = r * 0.25f
    val textStyle = TextStyle(color = Color.White, fontSize = (r * 0.085f).toSp(), fontWeight = FontWeight.Bold)

    order.forEachIndexed { i, pocket ->
        val color = when (pocketColor(pocket)) {
            PocketColor.RED -> Color(0xFFC62828)
            PocketColor.BLACK -> Color(0xFF1B1B1B)
            PocketColor.GREEN -> Color(0xFF2E7D32)
        }
        drawArc(
            color, startAngle = -90f + i * step - step / 2f, sweepAngle = step + 0.4f, useCenter = false,
            topLeft = c - Offset(ringMid, ringMid), size = Size(ringMid * 2, ringMid * 2), style = Stroke(ringWidth),
        )
        // Divider ("fret") on the pocket boundary.
        rotate(i * step + step / 2f, c) {
            drawLine(Color(0xFFD4AF37), Offset(c.x, c.y - r * 0.58f), Offset(c.x, c.y - r * 0.83f), strokeWidth = r * 0.008f)
        }
        // Number, upright along the radius.
        rotate(i * step, c) {
            val layout = measurer.measure(pocketLabel(pocket), textStyle)
            drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - r * 0.775f - layout.size.height / 2f))
        }
    }
    // Inner cone and hub.
    drawCircle(Color(0xFF2B2B2B), r * 0.58f, c)
    drawCircle(Color(0xFF3A3A3A), r * 0.5f, c, style = Stroke(r * 0.02f))
    for (k in 0 until 4) {
        rotate(k * 90f + 45f, c) { drawLine(Color(0xFFD4AF37), c, Offset(c.x, c.y - r * 0.45f), strokeWidth = r * 0.03f) }
    }
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE082), Color(0xFFB8860B)), c, r * 0.18f), r * 0.18f, c)
}
