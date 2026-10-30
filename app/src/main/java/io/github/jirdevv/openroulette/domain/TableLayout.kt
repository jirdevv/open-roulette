// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

/**
 * Builds every bet spot of a table with its hit rectangle.
 *
 * Grid (landscape, units = 1 cell):
 *   x: [0,1) zero column | [1,13) twelve number columns | [13,14) column bets
 *   y: [0,3) three number rows (top row = 3,6,9..) | [3,4) dozens | [4,5) even-money bets
 *
 * Hit testing picks the SMALLEST rectangle under the finger, so corners beat splits beat straights.
 */
object TableLayoutFactory {
    const val GRID_W = 14f
    const val GRID_H = 5f
    private const val E = 0.17f // half-size of line/corner hit areas

    /** Number at number-column [col] (0..11) and row [row] (0 = top .. 2 = bottom). */
    fun numberAt(col: Int, row: Int): Int = 3 * col + (3 - row)

    fun hitTest(spots: List<BetSpot>, gx: Float, gy: Float): BetSpot? =
        spots.filter { it.rect.contains(gx, gy) }.minByOrNull { it.rect.area }

    private fun point(x: Float, y: Float) = CellRect(x - E, y - E, 2 * E, 2 * E)

    fun build(table: TableType): List<BetSpot> = buildList {
        // American: 00 sits on top (next to 3/2), 0 below (next to 2/1). European: one tall zero.
        val zeros: List<Pair<Int, CellRect>> =
            if (table.hasDoubleZero) listOf(
                DOUBLE_ZERO to CellRect(0f, 0f, 1f, 1.5f),
                0 to CellRect(0f, 1.5f, 1f, 1.5f),
            ) else listOf(0 to CellRect(0f, 0f, 1f, 3f))

        // --- Straight up ---
        for ((z, r) in zeros) add(BetSpot(BetType.STRAIGHT, setOf(z), pocketLabel(z), r))
        for (c in 0 until 12) for (r in 0 until 3) {
            val n = numberAt(c, r)
            add(BetSpot(BetType.STRAIGHT, setOf(n), n.toString(), CellRect(1f + c, r.toFloat(), 1f, 1f)))
        }

        // --- Splits touching the zero area (generated from geometric overlap) ---
        for ((z, zr) in zeros) for (r in 0 until 3) {
            val lo = maxOf(zr.y, r.toFloat())
            val hi = minOf(zr.y + zr.h, r + 1f)
            if (hi - lo > 0.01f) {
                add(BetSpot(BetType.SPLIT, setOf(z, numberAt(0, r)), "", CellRect(1f - E, lo, 2 * E, hi - lo)))
            }
        }
        if (table.hasDoubleZero) {
            add(BetSpot(BetType.SPLIT, setOf(0, DOUBLE_ZERO), "", CellRect(0.2f, 1.5f - E, 0.6f, 2 * E)))
        }

        // --- Trios and basket ---
        if (table.hasDoubleZero) {
            add(BetSpot(BetType.TRIO, setOf(0, 1, 2), "", point(1f, 2f)))
            add(BetSpot(BetType.TRIO, setOf(DOUBLE_ZERO, 2, 3), "", point(1f, 1f)))
            add(BetSpot(BetType.TRIO, setOf(0, DOUBLE_ZERO, 2), "", point(1f, 1.5f)))
            add(BetSpot(BetType.BASKET, setOf(0, DOUBLE_ZERO, 1, 2, 3), "", point(1f, 3f)))
        } else {
            add(BetSpot(BetType.TRIO, setOf(0, 1, 2), "", point(1f, 2f)))
            add(BetSpot(BetType.TRIO, setOf(0, 2, 3), "", point(1f, 1f)))
        }

        // --- Inner splits (vertical neighbours, on the horizontal lines) ---
        for (c in 0 until 12) for (r in 0 until 2) {
            add(BetSpot(BetType.SPLIT, setOf(numberAt(c, r), numberAt(c, r + 1)), "",
                CellRect(1f + c + E, r + 1f - E, 1f - 2 * E, 2 * E)))
        }
        // --- Inner splits (horizontal neighbours, on the vertical lines) ---
        for (c in 0 until 11) for (r in 0 until 3) {
            add(BetSpot(BetType.SPLIT, setOf(numberAt(c, r), numberAt(c + 1, r)), "",
                CellRect(2f + c - E, r + E, 2 * E, 1f - 2 * E)))
        }
        // --- Corners ---
        for (c in 0 until 11) for (r in 0 until 2) {
            val nums = setOf(numberAt(c, r), numberAt(c, r + 1), numberAt(c + 1, r), numberAt(c + 1, r + 1))
            add(BetSpot(BetType.CORNER, nums, "", point(2f + c, r + 1f)))
        }
        // --- Streets (bottom edge of each number column) ---
        for (c in 0 until 12) {
            add(BetSpot(BetType.STREET, setOf(numberAt(c, 0), numberAt(c, 1), numberAt(c, 2)), "",
                CellRect(1f + c + E, 3f - E, 1f - 2 * E, 2 * E)))
        }
        // --- Six lines ---
        for (c in 0 until 11) {
            val nums = (0 until 3).flatMap { r -> listOf(numberAt(c, r), numberAt(c + 1, r)) }.toSet()
            add(BetSpot(BetType.SIX_LINE, nums, "", point(2f + c, 3f)))
        }

        // --- Columns (2:1). Top row holds 3,6,9.. (n % 3 == 0), middle 2,5,8.. (== 2), bottom 1,4,7.. (== 1) ---
        val colRemainder = listOf(0, 2, 1)
        for (r in 0 until 3) {
            add(BetSpot(BetType.COLUMN, (1..36).filter { it % 3 == colRemainder[r] }.toSet(), "2:1",
                CellRect(13f, r.toFloat(), 1f, 1f)))
        }
        // --- Dozens ---
        listOf("1st 12", "2nd 12", "3rd 12").forEachIndexed { k, label ->
            add(BetSpot(BetType.DOZEN, (12 * k + 1..12 * k + 12).toSet(), label, CellRect(1f + 4 * k, 3f, 4f, 1f)))
        }
        // --- Even-money bets ---
        val evenSpecs = listOf<Triple<String, Set<Int>, PocketColor?>>(
            Triple("1-18", (1..18).toSet(), null),
            Triple("EVEN", (1..36).filter { it % 2 == 0 }.toSet(), null),
            Triple("RED", (1..36).filter { pocketColor(it) == PocketColor.RED }.toSet(), PocketColor.RED),
            Triple("BLACK", (1..36).filter { pocketColor(it) == PocketColor.BLACK }.toSet(), PocketColor.BLACK),
            Triple("ODD", (1..36).filter { it % 2 == 1 }.toSet(), null),
            Triple("19-36", (19..36).toSet(), null),
        )
        evenSpecs.forEachIndexed { i, (label, nums, tint) ->
            add(BetSpot(BetType.EVEN_MONEY, nums, label, CellRect(1f + 2 * i, 4f, 2f, 1f), tint))
        }
    }
}
