// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.fossroulette.openroulette.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.fossroulette.openroulette.domain.PocketColor
import org.fossroulette.openroulette.domain.TableType
import org.fossroulette.openroulette.domain.computeStats
import org.fossroulette.openroulette.domain.pocketColor
import org.fossroulette.openroulette.domain.pocketLabel

@Composable
fun StatsScreen(history: List<Int>, table: TableType, onBack: () -> Unit) {
    val stats = remember(history, table) { computeStats(history, table) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Statistics") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${stats.total} spins on the ${table.displayName} wheel (up to the last 500)", style = MaterialTheme.typography.bodyMedium)
            if (stats.total == 0) return@Column
            Bar("Red", stats.red, stats.total, Color(0xFFC62828))
            Bar("Black", stats.black, stats.total, Color(0xFF424242))
            Bar("Green (zero)", stats.green, stats.total, Color(0xFF2E7D32))
            val real = stats.total - stats.green
            if (real > 0) {
                Bar("Odd", stats.odd, real, MaterialTheme.colorScheme.primary)
                Bar("Even", stats.even, real, MaterialTheme.colorScheme.primary)
                Bar("Low 1-18", stats.low, real, MaterialTheme.colorScheme.secondary)
                Bar("High 19-36", stats.high, real, MaterialTheme.colorScheme.secondary)
            }
            Section("Hot numbers", stats.hot)
            Section("Cold numbers", stats.cold)
            Text(
                "Every spin is independent. \"Hot\" and \"cold\" describe the past only; they do not predict anything.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Bar(label: String, value: Int, total: Int, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f))
            Text("$value (${if (total == 0) 0 else value * 100 / total}%)")
        }
        LinearProgressIndicator(progress = { if (total == 0) 0f else value / total.toFloat() }, Modifier.fillMaxWidth(), color = color)
    }
}

@Composable
private fun Section(title: String, items: List<Pair<Int, Int>>) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (p, n) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(30.dp).background(
                        when (pocketColor(p)) { PocketColor.RED -> Color(0xFFC62828); PocketColor.BLACK -> Color(0xFF1B1B1B); PocketColor.GREEN -> Color(0xFF2E7D32) },
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) { Text(pocketLabel(p), color = Color.White, style = MaterialTheme.typography.labelMedium) }
                Text("×$n")
            }
        }
    }
}
