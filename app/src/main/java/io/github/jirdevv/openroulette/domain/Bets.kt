// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

/** Payout is "to one": a winning 10-chip straight bet returns 10 * (35 + 1) = 360. */
enum class BetType(val payout: Int, val displayName: String) {
    STRAIGHT(35, "Straight up"),
    SPLIT(17, "Split"),
    STREET(11, "Street"),
    TRIO(11, "Trio"),
    CORNER(8, "Corner"),
    BASKET(6, "Basket"),      // American 0-00-1-2-3 (6:1)
    SIX_LINE(5, "Six line"),
    COLUMN(2, "Column"),
    DOZEN(2, "Dozen"),
    EVEN_MONEY(1, "Even money"),
}

/** Rectangle in table-grid units (1.0 == one number cell). Keeps the domain free of Android types. */
data class CellRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx: Float get() = x + w / 2f
    val cy: Float get() = y + h / 2f
    val area: Float get() = w * h
    fun contains(px: Float, py: Float) = px >= x && px < x + w && py >= y && py < y + h
}

fun spotId(type: BetType, numbers: Set<Int>): String = "${type.name}:${numbers.sorted().joinToString("-")}"

/** One place on the table where chips can be put. */
data class BetSpot(
    val type: BetType,
    val numbers: Set<Int>,
    val label: String,
    val rect: CellRect,
    val tint: PocketColor? = null,
) {
    val id: String = spotId(type, numbers)

    /** Spots that are drawn as visible cells (vs. invisible hit areas on lines/corners). */
    val isCell: Boolean get() = type == BetType.STRAIGHT || type == BetType.COLUMN ||
        type == BetType.DOZEN || type == BetType.EVEN_MONEY

    fun describe(): String = when (type) {
        BetType.STRAIGHT -> "Straight up ${label}"
        BetType.COLUMN, BetType.DOZEN, BetType.EVEN_MONEY -> "${type.displayName} ${label}"
        else -> "${type.displayName} ${numbers.sorted().joinToString(", ") { pocketLabel(it) }}"
    }
}
