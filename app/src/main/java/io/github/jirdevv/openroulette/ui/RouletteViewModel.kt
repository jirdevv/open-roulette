// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.fossroulette.openroulette.data.AppSettings
import org.fossroulette.openroulette.data.DEFAULT_BALANCE
import org.fossroulette.openroulette.data.MAX_HISTORY
import org.fossroulette.openroulette.data.SettingsRepository
import org.fossroulette.openroulette.domain.BetSpot
import org.fossroulette.openroulette.domain.CallBet
import org.fossroulette.openroulette.domain.FrenchRule
import org.fossroulette.openroulette.domain.Payouts
import org.fossroulette.openroulette.domain.PocketRng
import org.fossroulette.openroulette.domain.RoundResult
import org.fossroulette.openroulette.domain.SecureRng
import org.fossroulette.openroulette.domain.TableLayoutFactory
import org.fossroulette.openroulette.domain.TableType

enum class Phase { BETTING, SPINNING, RESULT }
enum class FeedbackEvent { CHIP, SPIN, WIN, LOSE }

data class UiState(
    val loaded: Boolean = false,
    val table: TableType = TableType.EUROPEAN,
    val frenchRule: FrenchRule = FrenchRule.LA_PARTAGE,
    val balance: Long = DEFAULT_BALANCE,
    val phase: Phase = Phase.BETTING,
    val bets: Map<String, Long> = emptyMap(),
    /** Every chip placed this round, newest last (drives Undo and long-press removal). */
    val undoStack: List<Pair<String, Long>> = emptyList(),
    val lastBets: Map<String, Long> = emptyMap(),
    val prison: Map<String, Long> = emptyMap(),
    val selectedChip: Int = 0,
    val spin: SpinRequest? = null,
    val result: RoundResult? = null,
    val history: List<Int> = emptyList(),
    val autoSpin: Boolean = false,
) {
    val totalBet: Long get() = bets.values.sum()
    val prisonTotal: Long get() = prison.values.sum()
}

class RouletteViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val rng: PocketRng = SecureRng()

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    private val _events = MutableSharedFlow<FeedbackEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<FeedbackEvent> = _events.asSharedFlow()

    private val spotCache = HashMap<TableType, Map<String, BetSpot>>()
    private fun spotMap(t: TableType) = spotCache.getOrPut(t) { TableLayoutFactory.build(t).associateBy { it.id } }

    private var spinCounter = 0L
    private var autoJob: Job? = null

    init {
        viewModelScope.launch {
            val s = repo.loadSavedGame()
            _ui.update {
                it.copy(loaded = true, table = s.table, frenchRule = s.frenchRule, balance = s.balance,
                    history = s.history, prison = if (s.table == TableType.FRENCH) s.prison else emptyMap())
            }
        }
    }

    // ---------------------------------------------------------------- betting

    private fun chipValue(): Long {
        val d = settings.value.denominations
        return d[_ui.value.selectedChip.coerceIn(0, d.lastIndex)]
    }

    fun selectChip(index: Int) = _ui.update { it.copy(selectedChip = index) }

    fun placeBet(spotId: String) { addBets(listOf(spotId to chipValue())) }

    fun placeCallBet(call: CallBet) {
        val unit = chipValue()
        addBets(call.parts.map { it.spotId to it.units * unit })
    }

    private fun addBets(items: List<Pair<String, Long>>): Boolean {
        val s = _ui.value
        val total = items.sumOf { it.second }
        if (!s.loaded || s.phase == Phase.SPINNING || total <= 0 || s.balance < total) return false
        val bets = s.bets.toMutableMap()
        items.forEach { (id, a) -> bets[id] = (bets[id] ?: 0L) + a }
        _ui.value = s.copy(
            bets = bets, undoStack = s.undoStack + items, balance = s.balance - total,
            phase = Phase.BETTING, result = null,
        )
        _events.tryEmit(FeedbackEvent.CHIP)
        persist()
        return true
    }

    /** Long-press: take the most recent chip off a spot. */
    fun removeChip(spotId: String) {
        val s = _ui.value
        if (s.phase == Phase.SPINNING) return
        val idx = s.undoStack.indexOfLast { it.first == spotId }
        if (idx < 0) return
        removeAt(idx)
    }

    fun undo() {
        val s = _ui.value
        if (s.phase != Phase.SPINNING && s.undoStack.isNotEmpty()) removeAt(s.undoStack.lastIndex)
    }

    private fun removeAt(idx: Int) {
        val s = _ui.value
        val (id, amount) = s.undoStack[idx]
        val bets = s.bets.toMutableMap()
        val left = (bets[id] ?: 0L) - amount
        if (left <= 0) bets.remove(id) else bets[id] = left
        _ui.value = s.copy(bets = bets, undoStack = s.undoStack.toMutableList().also { it.removeAt(idx) }, balance = s.balance + amount)
        persist()
    }

    fun clearBets() {
        val s = _ui.value
        if (s.phase == Phase.SPINNING) return
        _ui.value = s.copy(bets = emptyMap(), undoStack = emptyList(), balance = s.balance + s.totalBet)
        persist()
    }

    /** Re-places last round's exact bets (only when the table is empty). */
    fun rebet(): Boolean {
        val s = _ui.value
        if (s.phase == Phase.SPINNING || s.bets.isNotEmpty() || s.lastBets.isEmpty()) return false
        val known = spotMap(s.table)
        return addBets(s.lastBets.filterKeys { it in known }.map { it.key to it.value })
    }

    // ------------------------------------------------------------------- spin

    fun spin() {
        val s = _ui.value
        if (!s.loaded || s.phase == Phase.SPINNING || (s.bets.isEmpty() && s.prison.isEmpty())) return
        // The outcome is decided here, by the CSPRNG. The wheel animation only visualises it.
        val index = rng.nextIndex(s.table.pocketCount)
        val pocket = s.table.wheelOrder[index]
        val duration = (BASE_SPIN_MS / settings.value.animationSpeed).toLong()
        val req = SpinRequest(++spinCounter, pocket, duration.toInt())
        _ui.value = s.copy(
            phase = Phase.SPINNING, spin = req, result = null, undoStack = emptyList(),
            lastBets = if (s.bets.isNotEmpty()) s.bets else s.lastBets,
        )
        _events.tryEmit(FeedbackEvent.SPIN)
        viewModelScope.launch {
            delay(duration + SETTLE_MS)
            finishSpin(req.id)
        }
    }

    private fun finishSpin(id: Long) {
        val s = _ui.value
        val req = s.spin ?: return
        if (req.id != id || s.phase != Phase.SPINNING) return
        val result = Payouts.resolve(spotMap(s.table), s.bets, s.prison, req.pocket, s.table, s.frenchRule)
        _ui.value = s.copy(
            phase = Phase.RESULT, spin = null, result = result,
            balance = s.balance + result.totalReturned,
            bets = emptyMap(), undoStack = emptyList(), prison = result.newPrison,
            history = (s.history + req.pocket).takeLast(MAX_HISTORY),
        )
        _events.tryEmit(if (result.net > 0) FeedbackEvent.WIN else FeedbackEvent.LOSE)
        persist()
        if (_ui.value.autoSpin) {
            autoJob?.cancel()
            autoJob = viewModelScope.launch { delay(AUTO_DELAY_MS); autoStep() }
        }
    }

    fun toggleAuto() {
        val on = !_ui.value.autoSpin
        _ui.update { it.copy(autoSpin = on) }
        if (on) { if (_ui.value.phase != Phase.SPINNING) autoStep() } else autoJob?.cancel()
    }

    private fun autoStep() {
        val s = _ui.value
        if (!s.autoSpin || s.phase == Phase.SPINNING) return
        if (s.bets.isEmpty() && !rebet() && _ui.value.prison.isEmpty()) {
            _ui.update { it.copy(autoSpin = false) } // nothing left to bet
            return
        }
        spin()
    }

    // --------------------------------------------------------- table / session

    fun setTable(t: TableType) {
        val s = _ui.value
        if (s.phase == Phase.SPINNING || s.table == t) return
        autoJob?.cancel()
        _ui.value = s.copy(
            table = t, balance = s.balance + s.totalBet + s.prisonTotal, bets = emptyMap(), undoStack = emptyList(),
            lastBets = emptyMap(), prison = emptyMap(), result = null, autoSpin = false, phase = Phase.BETTING,
        )
        persist()
    }

    fun setFrenchRule(r: FrenchRule) {
        val s = _ui.value
        if (s.phase == Phase.SPINNING || s.frenchRule == r) return
        _ui.value = s.copy(frenchRule = r, balance = s.balance + s.prisonTotal, prison = emptyMap())
        persist()
    }

    fun resetBalance() {
        val s = _ui.value
        if (s.phase == Phase.SPINNING) return
        _ui.value = s.copy(balance = DEFAULT_BALANCE, bets = emptyMap(), undoStack = emptyList(), prison = emptyMap(), autoSpin = false)
        persist()
    }

    fun clearHistory() { _ui.update { it.copy(history = emptyList()) }; persist() }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.saveSettings(transform(settings.value)) }
    }

    /** Persists balance INCLUDING chips currently on the table, so a killed process never eats a stake. */
    private fun persist() {
        val s = _ui.value
        if (!s.loaded) return
        viewModelScope.launch { repo.saveGame(s.table, s.frenchRule, s.balance + s.totalBet, s.history, s.prison) }
    }

    companion object {
        const val BASE_SPIN_MS = 6500L
        const val SETTLE_MS = 300L
        const val AUTO_DELAY_MS = 2200L
    }
}
