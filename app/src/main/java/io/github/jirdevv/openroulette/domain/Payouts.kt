// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

enum class Outcome { WIN, LOSE, HALF_BACK, IMPRISONED, RELEASED }

data class BetOutcome(val spotId: String, val stake: Long, val outcome: Outcome, val returned: Long)

data class RoundResult(
    val pocket: Int,
    val outcomes: List<BetOutcome>,
    /** Chips staked this round (excluding stakes already imprisoned from earlier rounds). */
    val totalStake: Long,
    /** Chips handed back this round, INCLUDING the original stake of winning bets. */
    val totalReturned: Long,
    /** Even-money stakes now "in prison" (En Prison rule). */
    val newPrison: Map<String, Long>,
) {
    val net: Long get() = totalReturned - totalStake
}

object Payouts {
    /**
     * Resolves one spin. Stakes were already deducted from the balance when placed, so the caller
     * simply adds [RoundResult.totalReturned] to the balance.
     */
    fun resolve(
        spots: Map<String, BetSpot>,
        bets: Map<String, Long>,
        prison: Map<String, Long>,
        pocket: Int,
        table: TableType,
        rule: FrenchRule,
    ): RoundResult {
        val outcomes = mutableListOf<BetOutcome>()
        val newPrison = mutableMapOf<String, Long>()
        var returned = 0L

        // 1. Previously imprisoned even-money bets (En Prison).
        for ((id, stake) in prison) {
            val spot = spots[id] ?: continue
            when {
                pocket in spot.numbers -> { returned += stake; outcomes += BetOutcome(id, stake, Outcome.RELEASED, stake) }
                pocket == 0 -> { newPrison[id] = (newPrison[id] ?: 0L) + stake; outcomes += BetOutcome(id, stake, Outcome.IMPRISONED, 0) }
                else -> outcomes += BetOutcome(id, stake, Outcome.LOSE, 0)
            }
        }

        // 2. Bets placed this round.
        var staked = 0L
        for ((id, stake) in bets) {
            val spot = spots[id] ?: continue
            staked += stake
            when {
                pocket in spot.numbers -> {
                    val ret = stake * (spot.type.payout + 1)
                    returned += ret
                    outcomes += BetOutcome(id, stake, Outcome.WIN, ret)
                }
                pocket == 0 && table == TableType.FRENCH && spot.type == BetType.EVEN_MONEY -> when (rule) {
                    FrenchRule.LA_PARTAGE -> {
                        val half = stake / 2 // odd stakes round down in the casino's favour
                        returned += half
                        outcomes += BetOutcome(id, stake, Outcome.HALF_BACK, half)
                    }
                    FrenchRule.EN_PRISON -> {
                        newPrison[id] = (newPrison[id] ?: 0L) + stake
                        outcomes += BetOutcome(id, stake, Outcome.IMPRISONED, 0)
                    }
                }
                else -> outcomes += BetOutcome(id, stake, Outcome.LOSE, 0)
            }
        }
        return RoundResult(pocket, outcomes, staked, returned, newPrison)
    }

    /** Highest total return over all possible pockets (the "potential payout" shown to the player). */
    fun bestCaseReturn(spots: Map<String, BetSpot>, bets: Map<String, Long>, table: TableType): Long {
        if (bets.isEmpty()) return 0L
        return table.wheelOrder.maxOf { p ->
            bets.entries.sumOf { (id, stake) ->
                val s = spots[id]
                if (s != null && p in s.numbers) stake * (s.type.payout + 1) else 0L
            }
        }
    }
}
