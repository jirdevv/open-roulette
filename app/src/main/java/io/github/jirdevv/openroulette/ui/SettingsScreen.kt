// SPDX-License-Identifier: GPL-3.0-or-later
@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package org.fossroulette.openroulette.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.fossroulette.openroulette.data.AppSettings
import org.fossroulette.openroulette.data.ChipStyle
import org.fossroulette.openroulette.data.FeltTheme
import org.fossroulette.openroulette.data.ThemeMode
import org.fossroulette.openroulette.data.parseDenominations
import org.fossroulette.openroulette.domain.FrenchRule
import org.fossroulette.openroulette.domain.TableType

@Composable
fun SettingsScreen(vm: RouletteViewModel, settings: AppSettings, ui: UiState, onBack: () -> Unit) {
    val idle = ui.phase != Phase.SPINNING
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Header("Table")
            Choice("Roulette type", TableType.entries, ui.table, { it.displayName }, enabled = idle) { vm.setTable(it) }
            if (ui.table == TableType.FRENCH) {
                Choice("Zero rule for even-money bets", FrenchRule.entries, ui.frenchRule, { it.displayName }, enabled = idle) { vm.setFrenchRule(it) }
                Toggle("Show call bets (Voisins, Tiers, Orphelins, Jeu 0)", settings.showCallBets) { v -> vm.updateSettings { it.copy(showCallBets = v) } }
            }
            Text("Changing table or rule refunds any chips on the table.", style = MaterialTheme.typography.bodySmall)

            HorizontalDivider()
            Header("Appearance")
            Choice("Theme", ThemeMode.entries, settings.themeMode, { it.displayName }) { v -> vm.updateSettings { it.copy(themeMode = v) } }
            Choice("Table felt", FeltTheme.entries, settings.felt, { it.displayName }) { v -> vm.updateSettings { it.copy(felt = v) } }
            Choice("Chip design", ChipStyle.entries, settings.chipStyle, { it.displayName }) { v -> vm.updateSettings { it.copy(chipStyle = v) } }

            HorizontalDivider()
            Header("Gameplay")
            var speed by remember(settings.animationSpeed) { mutableFloatStateOf(settings.animationSpeed) }
            Column {
                Text("Animation speed: ${String.format(Locale.getDefault(), "%.1f", speed)}×")
                Slider(
                    value = speed, onValueChange = { speed = it },
                    onValueChangeFinished = { vm.updateSettings { it.copy(animationSpeed = speed) } },
                    valueRange = 0.5f..3f,
                )
            }
            Toggle("Sound effects", settings.sound) { v -> vm.updateSettings { it.copy(sound = v) } }
            Toggle("Haptic feedback", settings.haptics) { v -> vm.updateSettings { it.copy(haptics = v) } }

            HorizontalDivider()
            Header("Chips")
            var text by remember(settings.denominations) { mutableStateOf(settings.denominations.joinToString(", ")) }
            val parsed = parseDenominations(text)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text("Denominations") },
                supportingText = { Text("Comma-separated, up to 8 values") }, isError = parsed == null,
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { parsed?.let { p -> vm.selectChip(0); vm.updateSettings { it.copy(denominations = p) } } }, enabled = parsed != null) { Text("Apply") }
                OutlinedButton(onClick = { text = "1, 5, 25, 100, 500, 1000" }) { Text("Defaults") }
            }

            HorizontalDivider()
            Header("Play money")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::resetBalance, enabled = idle) { Text("Reset balance") }
                OutlinedButton(onClick = vm::clearHistory) { Text("Clear statistics") }
            }

            HorizontalDivider()
            Text(
                "Open Roulette is free software (GPL-3.0-or-later). It is a simulation with play money only; " +
                    "no real-money gambling, no network access, no tracking.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun <T> Choice(title: String, options: List<T>, selected: T, label: (T) -> String, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o -> FilterChip(selected = o == selected, enabled = enabled, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
        }
    }
}

@Composable
private fun Toggle(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
