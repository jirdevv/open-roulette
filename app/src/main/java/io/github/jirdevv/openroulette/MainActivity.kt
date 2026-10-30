// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.jirdevv.openroulette

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.fossroulette.openroulette.ui.GameScreen
import org.fossroulette.openroulette.ui.RouletteViewModel
import org.fossroulette.openroulette.ui.SettingsScreen
import org.fossroulette.openroulette.ui.StatsScreen
import org.fossroulette.openroulette.ui.theme.RouletteTheme

private enum class Screen { GAME, SETTINGS, STATS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: RouletteViewModel = viewModel()
            val settings by vm.settings.collectAsStateWithLifecycle()
            val ui by vm.ui.collectAsStateWithLifecycle()
            var screen by rememberSaveable { mutableStateOf(Screen.GAME) }

            RouletteTheme(settings.themeMode) {
                BackHandler(enabled = screen != Screen.GAME) { screen = Screen.GAME }
                when (screen) {
                    Screen.GAME -> GameScreen(vm, settings, { screen = Screen.SETTINGS }, { screen = Screen.STATS })
                    Screen.SETTINGS -> SettingsScreen(vm, settings, ui) { screen = Screen.GAME }
                    Screen.STATS -> StatsScreen(ui.history, ui.table) { screen = Screen.GAME }
                }
            }
        }
    }
}
