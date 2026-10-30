// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import org.fossroulette.openroulette.data.AppSettings
import org.fossroulette.openroulette.data.ChipStyle
import org.fossroulette.openroulette.domain.FrenchCallBets
import org.fossroulette.openroulette.domain.Outcome
import org.fossroulette.openroulette.domain.Payouts
import org.fossroulette.openroulette.domain.PocketColor
import org.fossroulette.openroulette.domain.RoundResult
import org.fossroulette.openroulette.domain.TableLayoutFactory
import org.fossroulette.openroulette.domain.TableType
import org.fossroulette.openroulette.domain.pocketColor
import org.fossroulette.openroulette.domain.pocketLabel

private fun fmt(v: Long) = String.format(Locale.getDefault(), "%,d", v)

private fun pocketBg(p: Int) = when (pocketColor(p)) {
    PocketColor.RED -> Color(0xFFC62828)
    PocketColor.BLACK -> Color(0xFF1B1B1B)
    PocketColor.GREEN -> Color(0xFF2E7D32)
}

@Composable
fun GameScreen(vm: RouletteViewModel, settings: AppSettings, onOpenSettings: () -> Unit, onOpenStats: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    // Haptics + sound reacting to ViewModel events.
    val haptic = LocalHapticFeedback.current
    val sounds = remember { SoundEffects() }
    DisposableEffect(Unit) { onDispose { sounds.release() } }
    val latest by rememberUpdatedState(settings)
    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            if (latest.haptics) haptic.performHapticFeedback(
                if (e == FeedbackEvent.CHIP) HapticFeedbackType.TextHandleMove else HapticFeedbackType.LongPress
            )
            if (latest.sound) sounds.play(e)
        }
    }

    val spots = remember(ui.table) { TableLayoutFactory.build(ui.table) }
    val spotById = remember(spots) { spots.associateBy { it.id } }
    val bestCase = remember(ui.bets, spotById, ui.table) { Payouts.bestCaseReturn(spotById, ui.bets, ui.table) }
    val denoms = settings.denominations
    val canBet = ui.phase != Phase.SPINNING

    val wheel: @Composable (Modifier) -> Unit = { m -> RouletteWheel(ui.table, ui.spin, m) }
    val table: @Composable (Modifier) -> Unit = { m ->
        BettingTable(
            spots = spots, bets = ui.bets, prison = ui.prison, denominations = denoms,
            felt = settings.felt, chipStyle = settings.chipStyle, winningPocket = ui.result?.pocket,
            enabled = canBet, onPlace = vm::placeBet, onRemove = vm::removeChip, modifier = m,
        )
    }
    val summary: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Bet ${fmt(ui.totalBet)}  ·  Max return ${fmt(bestCase)}" +
                    if (ui.prison.isNotEmpty()) "  ·  In prison ${fmt(ui.prisonTotal)}" else "",
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f),
            )
            if (ui.balance == 0L && ui.bets.isEmpty() && ui.prison.isEmpty()) {
                TextButton(onClick = vm::resetBalance) { Text("Out of chips – reset") }
            }
        }
    }
    val callBets: @Composable () -> Unit = {
        if (ui.table == TableType.FRENCH && settings.showCallBets) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FrenchCallBets.ALL.forEach { cb ->
                    AssistChip(onClick = { vm.placeCallBet(cb) }, enabled = canBet, label = { Text("${cb.shortName} (${cb.totalUnits})") })
                }
            }
        }
    }
    val chips: @Composable () -> Unit = { ChipTray(denoms, ui.selectedChip, settings.chipStyle, vm::selectChip) }
    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val pad = PaddingValues(horizontal = 6.dp)
            OutlinedButton(vm::undo, Modifier.weight(1f), enabled = canBet && ui.undoStack.isNotEmpty(), contentPadding = pad) { Text("Undo", maxLines = 1) }
            OutlinedButton(vm::clearBets, Modifier.weight(1f), enabled = canBet && ui.bets.isNotEmpty(), contentPadding = pad) { Text("Clear", maxLines = 1) }
            OutlinedButton(
                { vm.rebet() }, Modifier.weight(1f),
                enabled = canBet && ui.bets.isEmpty() && ui.lastBets.isNotEmpty(), contentPadding = pad,
            ) { Text("Rebet", maxLines = 1) }
            FilterChip(selected = ui.autoSpin, onClick = vm::toggleAuto, label = { Text("Auto") })
            Button(
                vm::spin, Modifier.weight(1.4f),
                enabled = canBet && (ui.bets.isNotEmpty() || ui.prison.isNotEmpty()), contentPadding = pad,
            ) { Text(if (ui.phase == Phase.SPINNING) "Spinning…" else "SPIN", maxLines = 1, fontWeight = FontWeight.Bold) }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            HeaderBar(ui, canBet, vm::setTable, onOpenStats, onOpenSettings)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                if (maxWidth > maxHeight) {
                    // Landscape: wheel + history on the left, table + controls on the right.
                    Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(0.33f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                                wheel(Modifier.size(minOf(maxWidth, maxHeight)).align(Alignment.Center))
                            }
                            HistoryStrip(ui.history)
                            ResultBanner(ui.result)
                        }
                        Column(Modifier.weight(0.67f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            table(Modifier.weight(1f).fillMaxWidth())
                            summary(); callBets(); chips(); actions()
                        }
                    }
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        wheel(Modifier.fillMaxWidth(0.72f).align(Alignment.CenterHorizontally))
                        HistoryStrip(ui.history)
                        ResultBanner(ui.result)
                        table(Modifier.fillMaxWidth())
                        summary(); callBets(); chips(); actions()
                        Spacer(Modifier.size(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderBar(ui: UiState, canChange: Boolean, onTable: (TableType) -> Unit, onStats: () -> Unit, onSettings: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { menu = true }, enabled = canChange) {
                Text(ui.table.displayName + if (ui.table == TableType.FRENCH) " · ${ui.frenchRule.displayName}" else "")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                TableType.entries.forEach { t ->
                    DropdownMenuItem(text = { Text(t.displayName) }, onClick = { menu = false; onTable(t) })
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(
                fmt(ui.balance), Modifier.padding(horizontal = 14.dp, vertical = 6.dp).semantics { contentDescription = "Balance ${ui.balance}" },
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        TextButton(onClick = onStats) { Text("Stats") }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, contentDescription = "Settings") }
    }
}

@Composable
private fun HistoryStrip(history: List<Int>) {
    val last = remember(history) { history.takeLast(20).reversed() }
    Box(Modifier.fillMaxWidth().heightIn(min = 34.dp), contentAlignment = Alignment.CenterStart) {
        if (last.isEmpty()) {
            Text("No spins yet", style = MaterialTheme.typography.labelMedium)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(last) { p ->
                    Box(
                        Modifier.size(28.dp).background(pocketBg(p), CircleShape)
                            .semantics { contentDescription = "Previous result ${pocketLabel(p)}" },
                        contentAlignment = Alignment.Center,
                    ) { Text(pocketLabel(p), color = White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun ResultBanner(result: RoundResult?) {
    Box(Modifier.fillMaxWidth().heightIn(min = 36.dp), contentAlignment = Alignment.CenterStart) {
        if (result == null) return@Box
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).background(pocketBg(result.pocket), CircleShape), contentAlignment = Alignment.Center) {
                Text(pocketLabel(result.pocket), color = White, fontWeight = FontWeight.Bold)
            }
            val prisoned = result.newPrison.values.sum()
            val text = when {
                result.outcomes.isEmpty() -> "No bets"
                result.net > 0 -> "Won +${fmt(result.net)}"
                result.net < 0 && result.outcomes.any { it.outcome == Outcome.HALF_BACK } -> "La Partage: lost ${fmt(-result.net)}"
                result.net < 0 -> "Lost ${fmt(-result.net)}"
                else -> "Break-even"
            } + if (prisoned > 0) " · ${fmt(prisoned)} imprisoned" else ""
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ChipTray(denoms: List<Long>, selected: Int, style: ChipStyle, onSelect: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        denoms.forEachIndexed { i, d ->
            Canvas(
                Modifier.size(46.dp)
                    .clickable(role = Role.RadioButton) { onSelect(i) }
                    .semantics { contentDescription = "Chip $d"; this.selected = i == selected },
            ) {
                val half = size.minDimension / 2f
                if (i == selected) drawCircle(Color(0xFFFFD54F), half, center, style = Stroke(3.dp.toPx()))
                drawChip(center, half * 0.82f, chipColor(i), style, compact(d), measurer)
            }
        }
    }
}
