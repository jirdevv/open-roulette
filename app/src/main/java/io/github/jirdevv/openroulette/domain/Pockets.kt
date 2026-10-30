// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

/** Internal encoding of the American "00" pocket. Real numbers are 0..36. */
const val DOUBLE_ZERO = 37

enum class PocketColor { RED, BLACK, GREEN }

private val RED_NUMBERS = setOf(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36)

fun pocketColor(pocket: Int): PocketColor = when {
    pocket == 0 || pocket == DOUBLE_ZERO -> PocketColor.GREEN
    pocket in RED_NUMBERS -> PocketColor.RED
    else -> PocketColor.BLACK
}

fun pocketLabel(pocket: Int): String = if (pocket == DOUBLE_ZERO) "00" else pocket.toString()

/** Physical pocket order, clockwise, starting at zero. */
object Wheels {
    val EUROPEAN = listOf(
        0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24,
        16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26,
    )
    val AMERICAN = listOf(
        0, 28, 9, 26, 30, 11, 7, 20, 32, 17, 5, 22, 34, 15, 3, 24, 36, 13, 1, DOUBLE_ZERO,
        27, 10, 25, 29, 12, 8, 19, 31, 18, 6, 21, 33, 16, 4, 23, 35, 14, 2,
    )
}

enum class TableType(val displayName: String, val wheelOrder: List<Int>) {
    EUROPEAN("European", Wheels.EUROPEAN),
    AMERICAN("American", Wheels.AMERICAN),
    FRENCH("French", Wheels.EUROPEAN);

    val hasDoubleZero: Boolean get() = this == AMERICAN
    val pocketCount: Int get() = wheelOrder.size
}

/** What happens to even-money bets when the ball lands on the single zero (French table only). */
enum class FrenchRule(val displayName: String) {
    /** Half of the even-money stake is returned. */
    LA_PARTAGE("La Partage"),
    /** Stake is "imprisoned" and decides the next spin: win = stake returned, loss = lost, zero = still imprisoned. */
    EN_PRISON("En Prison"),
}
