// SPDX-License-Identifier: GPL-3.0-or-later
package org.fossroulette.openroulette.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouletteRulesTest {
    private fun spots(t: TableType) = TableLayoutFactory.build(t).associateBy { it.id }
    private fun id(type: BetType, vararg n: Int) = spotId(type, n.toSet())
    private fun redId(t: TableType) = spots(t).values.first { it.type == BetType.EVEN_MONEY && it.label == "RED" }.id

    @Test fun wheelsContainEveryPocketExactlyOnce() {
        assertEquals((0..36).toSet(), Wheels.EUROPEAN.toSet()); assertEquals(37, Wheels.EUROPEAN.size)
        assertEquals((0..36).toSet() + DOUBLE_ZERO, Wheels.AMERICAN.toSet()); assertEquals(38, Wheels.AMERICAN.size)
    }

    /** Every bet except the American basket must pay exactly (payout+1) * count == 36, i.e. the same house edge. */
    @Test fun payoutsAreConsistentWithCoverage() {
        for (t in TableType.entries) TableLayoutFactory.build(t).filter { it.type != BetType.BASKET }.forEach {
            assertEquals("${t} ${it.id}", 36, (it.type.payout + 1) * it.numbers.size)
        }
    }

    @Test fun layoutCounts() {
        fun count(t: TableType, b: BetType) = TableLayoutFactory.build(t).count { it.type == b }
        assertEquals(37, count(TableType.EUROPEAN, BetType.STRAIGHT))
        assertEquals(60, count(TableType.EUROPEAN, BetType.SPLIT))
        assertEquals(22, count(TableType.EUROPEAN, BetType.CORNER))
        assertEquals(12, count(TableType.EUROPEAN, BetType.STREET))
        assertEquals(11, count(TableType.EUROPEAN, BetType.SIX_LINE))
        assertEquals(2, count(TableType.EUROPEAN, BetType.TRIO))
        assertEquals(38, count(TableType.AMERICAN, BetType.STRAIGHT))
        assertEquals(3, count(TableType.AMERICAN, BetType.TRIO))
        assertEquals(1, count(TableType.AMERICAN, BetType.BASKET))
    }

    @Test fun spotIdsAreUnique() {
        for (t in TableType.entries) { val l = TableLayoutFactory.build(t); assertEquals(l.size, l.map { it.id }.toSet().size) }
    }

    @Test fun hitTestPrefersSmallestSpot() {
        val l = TableLayoutFactory.build(TableType.EUROPEAN)
        // Centre of number 1's cell -> straight up 1.
        assertEquals(setOf(1), TableLayoutFactory.hitTest(l, 1.5f, 2.5f)!!.numbers)
        // Intersection of 1,2,4,5 -> corner.
        assertEquals(BetType.CORNER, TableLayoutFactory.hitTest(l, 2f, 2f)!!.type)
        // Boundary between 1 and 2 -> split.
        assertEquals(setOf(1, 2), TableLayoutFactory.hitTest(l, 1.5f, 2f)!!.numbers)
    }

    @Test fun straightPays35to1() {
        val r = Payouts.resolve(spots(TableType.EUROPEAN), mapOf(id(BetType.STRAIGHT, 17) to 10L), emptyMap(), 17, TableType.EUROPEAN, FrenchRule.LA_PARTAGE)
        assertEquals(360L, r.totalReturned)
        assertEquals(350L, r.net)
    }

    @Test fun dozenAndColumnPay2to1() {
        val s = spots(TableType.EUROPEAN)
        val dozen = s.values.first { it.type == BetType.DOZEN && 1 in it.numbers }.id
        val column = s.values.first { it.type == BetType.COLUMN && 1 in it.numbers }.id
        val r = Payouts.resolve(s, mapOf(dozen to 10L, column to 10L), emptyMap(), 1, TableType.EUROPEAN, FrenchRule.LA_PARTAGE)
        assertEquals(60L, r.totalReturned)
    }

    @Test fun americanDoubleZeroLosesAllOutsideBets() {
        val s = spots(TableType.AMERICAN)
        val r = Payouts.resolve(s, mapOf(redId(TableType.AMERICAN) to 100L), emptyMap(), DOUBLE_ZERO, TableType.AMERICAN, FrenchRule.LA_PARTAGE)
        assertEquals(0L, r.totalReturned)
    }

    @Test fun americanBasketPays6to1() {
        val s = spots(TableType.AMERICAN)
        val basket = s.values.first { it.type == BetType.BASKET }.id
        val r = Payouts.resolve(s, mapOf(basket to 10L), emptyMap(), DOUBLE_ZERO, TableType.AMERICAN, FrenchRule.LA_PARTAGE)
        assertEquals(70L, r.totalReturned)
    }

    @Test fun laPartageReturnsHalfOnZero() {
        val s = spots(TableType.FRENCH)
        val r = Payouts.resolve(s, mapOf(redId(TableType.FRENCH) to 100L), emptyMap(), 0, TableType.FRENCH, FrenchRule.LA_PARTAGE)
        assertEquals(50L, r.totalReturned)
    }

    @Test fun laPartageDoesNotApplyToEuropeanTable() {
        val s = spots(TableType.EUROPEAN)
        val r = Payouts.resolve(s, mapOf(redId(TableType.EUROPEAN) to 100L), emptyMap(), 0, TableType.EUROPEAN, FrenchRule.LA_PARTAGE)
        assertEquals(0L, r.totalReturned)
    }

    @Test fun enPrisonLifecycle() {
        val s = spots(TableType.FRENCH)
        val red = redId(TableType.FRENCH)
        val first = Payouts.resolve(s, mapOf(red to 100L), emptyMap(), 0, TableType.FRENCH, FrenchRule.EN_PRISON)
        assertEquals(0L, first.totalReturned)
        assertEquals(mapOf(red to 100L), first.newPrison)
        // Zero again: stays imprisoned.
        val again = Payouts.resolve(s, emptyMap(), first.newPrison, 0, TableType.FRENCH, FrenchRule.EN_PRISON)
        assertEquals(mapOf(red to 100L), again.newPrison)
        // Red hits: stake released, no winnings.
        val won = Payouts.resolve(s, emptyMap(), first.newPrison, 1, TableType.FRENCH, FrenchRule.EN_PRISON)
        assertEquals(100L, won.totalReturned); assertTrue(won.newPrison.isEmpty())
        // Black hits: lost.
        val lost = Payouts.resolve(s, emptyMap(), first.newPrison, 2, TableType.FRENCH, FrenchRule.EN_PRISON)
        assertEquals(0L, lost.totalReturned); assertTrue(lost.newPrison.isEmpty())
    }

    @Test fun callBetsMapOntoRealSpotsAndHaveOfficialChipCounts() {
        val ids = spots(TableType.FRENCH).keys
        FrenchCallBets.ALL.forEach { cb -> cb.parts.forEach { assertTrue("${cb.name} ${it.spotId}", it.spotId in ids) } }
        assertEquals(9, FrenchCallBets.VOISINS.totalUnits)
        assertEquals(6, FrenchCallBets.TIERS.totalUnits)
        assertEquals(5, FrenchCallBets.ORPHELINS.totalUnits)
        assertEquals(4, FrenchCallBets.JEU_ZERO.totalUnits)
    }

    @Test fun bestCaseReturnPicksMostProfitablePocket() {
        val s = spots(TableType.EUROPEAN)
        val bets = mapOf(id(BetType.STRAIGHT, 7) to 10L, redId(TableType.EUROPEAN) to 10L)
        assertEquals(10L * 36 + 10L * 2, Payouts.bestCaseReturn(s, bets, TableType.EUROPEAN)) // 7 is red
    }
}
