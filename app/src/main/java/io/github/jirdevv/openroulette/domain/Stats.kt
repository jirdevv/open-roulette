// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

data class SpinStats(
    val total: Int,
    val counts: Map<Int, Int>,
    val red: Int, val black: Int, val green: Int,
    val odd: Int, val even: Int, val low: Int, val high: Int,
    val hot: List<Pair<Int, Int>>,
    val cold: List<Pair<Int, Int>>,
)

fun computeStats(history: List<Int>, table: TableType): SpinStats {
    val valid = table.wheelOrder.toSet()
    val spins = history.filter { it in valid }
    val counts = table.wheelOrder.associateWith { 0 }.toMutableMap()
    spins.forEach { counts[it] = counts.getValue(it) + 1 }
    val real = spins.filter { it in 1..36 }
    return SpinStats(
        total = spins.size,
        counts = counts,
        red = spins.count { pocketColor(it) == PocketColor.RED },
        black = spins.count { pocketColor(it) == PocketColor.BLACK },
        green = spins.count { pocketColor(it) == PocketColor.GREEN },
        odd = real.count { it % 2 == 1 }, even = real.count { it % 2 == 0 },
        low = real.count { it <= 18 }, high = real.count { it >= 19 },
        hot = counts.entries.filter { it.value > 0 }.sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key to it.value },
        cold = counts.entries.sortedWith(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .take(5).map { it.key to it.value },
    )
}

/** Greedy split of [amount] into chips (largest first) for drawing stacks. */
fun decomposeChips(amount: Long, denominations: List<Long>): List<Long> {
    var rem = amount
    val out = mutableListOf<Long>()
    for (d in denominations.sortedDescending()) {
        if (d <= 0) continue
        val n = rem / d
        repeat(minOf(n, 6L).toInt()) { out += d }
        rem -= n * d
    }
    if (rem > 0) out += rem
    return out
}
