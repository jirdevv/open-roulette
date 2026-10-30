// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.fossroulette.openroulette.domain.FrenchRule
import org.fossroulette.openroulette.domain.TableType

enum class ThemeMode(val displayName: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED") }
enum class FeltTheme(val displayName: String) {
    CLASSIC_GREEN("Classic green"), MODERN_DARK("Modern dark"), LUXURY_GOLD("Luxury gold"), ROYAL_BLUE("Royal blue")
}
enum class ChipStyle(val displayName: String) { CLASSIC("Classic"), FLAT("Flat"), OUTLINE("Outline") }

val DEFAULT_DENOMINATIONS = listOf(1L, 5L, 25L, 100L, 500L, 1000L)
const val DEFAULT_BALANCE = 10_000L
const val MAX_HISTORY = 500

/** Pure preferences (appearance / gameplay feel). */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val felt: FeltTheme = FeltTheme.CLASSIC_GREEN,
    val chipStyle: ChipStyle = ChipStyle.CLASSIC,
    val animationSpeed: Float = 1f,
    val sound: Boolean = false,
    val haptics: Boolean = true,
    val denominations: List<Long> = DEFAULT_DENOMINATIONS,
    val showCallBets: Boolean = true,
)

/** Game state that survives process death. Stakes on the table are refunded on restore (see ViewModel.persist). */
data class SavedGame(
    val table: TableType,
    val frenchRule: FrenchRule,
    val balance: Long,
    val history: List<Int>,
    val prison: Map<String, Long>,
)

/** "1, 5, 25" -> [1, 5, 25]; null when invalid. Sorted ascending, max 8 distinct positive values. */
fun parseDenominations(text: String): List<Long>? {
    val values = text.split(',', ';', ' ').map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLongOrNull() ?: return null }
    if (values.isEmpty() || values.any { it <= 0 || it > 1_000_000 }) return null
    val distinct = values.distinct().sorted()
    return if (distinct.size > 8) null else distinct
}

private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "open_roulette")

private object K {
    val THEME = stringPreferencesKey("theme")
    val FELT = stringPreferencesKey("felt")
    val CHIP_STYLE = stringPreferencesKey("chip_style")
    val SPEED = floatPreferencesKey("anim_speed")
    val SOUND = booleanPreferencesKey("sound")
    val HAPTICS = booleanPreferencesKey("haptics")
    val DENOMS = stringPreferencesKey("denominations")
    val CALLS = booleanPreferencesKey("call_bets")
    val TABLE = stringPreferencesKey("table")
    val RULE = stringPreferencesKey("french_rule")
    val BALANCE = longPreferencesKey("balance")
    val HISTORY = stringPreferencesKey("history")
    val PRISON = stringPreferencesKey("prison")
}

private inline fun <reified E : Enum<E>> Preferences.enumOf(key: Preferences.Key<String>, default: E): E =
    this[key]?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

class SettingsRepository(private val context: Context) {
    private val prefs: Flow<Preferences> =
        context.store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }

    val settings: Flow<AppSettings> = prefs.map { p ->
        AppSettings(
            themeMode = p.enumOf(K.THEME, ThemeMode.SYSTEM),
            felt = p.enumOf(K.FELT, FeltTheme.CLASSIC_GREEN),
            chipStyle = p.enumOf(K.CHIP_STYLE, ChipStyle.CLASSIC),
            animationSpeed = (p[K.SPEED] ?: 1f).coerceIn(0.5f, 3f),
            sound = p[K.SOUND] ?: false,
            haptics = p[K.HAPTICS] ?: true,
            denominations = p[K.DENOMS]?.let(::parseDenominations) ?: DEFAULT_DENOMINATIONS,
            showCallBets = p[K.CALLS] ?: true,
        )
    }.distinctUntilChanged()

    suspend fun loadSavedGame(): SavedGame {
        val p = prefs.first()
        return SavedGame(
            table = p.enumOf(K.TABLE, TableType.EUROPEAN),
            frenchRule = p.enumOf(K.RULE, FrenchRule.LA_PARTAGE),
            balance = p[K.BALANCE] ?: DEFAULT_BALANCE,
            history = p[K.HISTORY]?.split(',')?.mapNotNull { it.toIntOrNull() } ?: emptyList(),
            prison = decodePrison(p[K.PRISON]),
        )
    }

    suspend fun saveSettings(s: AppSettings) {
        context.store.edit {
            it[K.THEME] = s.themeMode.name
            it[K.FELT] = s.felt.name
            it[K.CHIP_STYLE] = s.chipStyle.name
            it[K.SPEED] = s.animationSpeed
            it[K.SOUND] = s.sound
            it[K.HAPTICS] = s.haptics
            it[K.DENOMS] = s.denominations.joinToString(",")
            it[K.CALLS] = s.showCallBets
        }
    }

    suspend fun saveGame(table: TableType, rule: FrenchRule, balance: Long, history: List<Int>, prison: Map<String, Long>) {
        context.store.edit {
            it[K.TABLE] = table.name
            it[K.RULE] = rule.name
            it[K.BALANCE] = balance
            it[K.HISTORY] = history.takeLast(MAX_HISTORY).joinToString(",")
            it[K.PRISON] = prison.entries.joinToString("|") { e -> "${e.key}=${e.value}" }
        }
    }

    private fun decodePrison(raw: String?): Map<String, Long> =
        raw.orEmpty().split('|').mapNotNull { part ->
            val i = part.lastIndexOf('=')
            if (i <= 0) null else part.substring(i + 1).toLongOrNull()?.let { part.substring(0, i) to it }
        }.toMap()
}
