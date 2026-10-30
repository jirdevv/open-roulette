// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

/** French "announced" bets: a fixed set of chips placed on the layout in one tap. */
data class CallBetPart(val type: BetType, val numbers: Set<Int>, val units: Int = 1) {
    val spotId: String get() = spotId(type, numbers)
}

data class CallBet(val name: String, val shortName: String, val parts: List<CallBetPart>) {
    val totalUnits: Int get() = parts.sumOf { it.units }
}

object FrenchCallBets {
    /** Voisins du Zéro: 17 pockets around zero, 9 chips. */
    val VOISINS = CallBet("Voisins du Zéro", "Voisins", listOf(
        CallBetPart(BetType.TRIO, setOf(0, 2, 3), 2),
        CallBetPart(BetType.SPLIT, setOf(4, 7)),
        CallBetPart(BetType.SPLIT, setOf(12, 15)),
        CallBetPart(BetType.SPLIT, setOf(18, 21)),
        CallBetPart(BetType.SPLIT, setOf(19, 22)),
        CallBetPart(BetType.CORNER, setOf(25, 26, 28, 29), 2),
        CallBetPart(BetType.SPLIT, setOf(32, 35)),
    ))

    /** Tiers du Cylindre: 12 pockets opposite zero, 6 split chips. */
    val TIERS = CallBet("Tiers du Cylindre", "Tiers", listOf(
        CallBetPart(BetType.SPLIT, setOf(5, 8)),
        CallBetPart(BetType.SPLIT, setOf(10, 11)),
        CallBetPart(BetType.SPLIT, setOf(13, 16)),
        CallBetPart(BetType.SPLIT, setOf(23, 24)),
        CallBetPart(BetType.SPLIT, setOf(27, 30)),
        CallBetPart(BetType.SPLIT, setOf(33, 36)),
    ))

    /** Orphelins: the 8 pockets left over, 5 chips. */
    val ORPHELINS = CallBet("Orphelins", "Orphelins", listOf(
        CallBetPart(BetType.STRAIGHT, setOf(1)),
        CallBetPart(BetType.SPLIT, setOf(6, 9)),
        CallBetPart(BetType.SPLIT, setOf(14, 17)),
        CallBetPart(BetType.SPLIT, setOf(17, 20)),
        CallBetPart(BetType.SPLIT, setOf(31, 34)),
    ))

    /** Jeu Zéro (Zero game): 7 pockets, 4 chips. */
    val JEU_ZERO = CallBet("Jeu Zéro", "Jeu 0", listOf(
        CallBetPart(BetType.SPLIT, setOf(0, 3)),
        CallBetPart(BetType.SPLIT, setOf(12, 15)),
        CallBetPart(BetType.STRAIGHT, setOf(26)),
        CallBetPart(BetType.SPLIT, setOf(32, 35)),
    ))

    val ALL = listOf(VOISINS, TIERS, ORPHELINS, JEU_ZERO)
}
