package com.boxowl.aroundtheworld.debug.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM tests of the V2.0d synthetic scenarios: extras -> CreditLedger snapshots.
 * Book km always derive from CreditLedger.totalBookMeters (k = 20 applied once,
 * inside CreditLedger).
 */
class SliceScenarioTest {

    @Test
    fun `free input passes both daily aggregates verbatim`() {
        val r = SliceScenario.resolve(null, metersPrev = 10_000L, metersToday = 5_000L)
        assertEquals(15_000L, r.ledger.totalRealMeters)
        assertEquals(10_000L, r.ledger.creditForDay(SliceScenario.PREV_DAY)?.realMeters)
        assertEquals(5_000L, r.ledger.creditForDay(SliceScenario.TODAY)?.realMeters)
        assertEquals(300.0, SliceScenario.bookKmOf(r.ledger), 1e-9)
    }

    @Test
    fun `start has no confirmed days`() {
        val r = SliceScenario.resolve("start", 0L, 0L)
        assertEquals(0L, r.ledger.totalRealMeters)
        assertNull(r.ledger.creditForDay(SliceScenario.TODAY))
        assertEquals(0.0, SliceScenario.bookKmOf(r.ledger), 1e-12)
    }

    @Test
    fun `suez before is below and suez after is above the suez mark`() {
        val suezKm = RoutePosition.endKmOfNode("suez")
        val before = SliceScenario.bookKmOf(SliceScenario.resolve("suez_before", 0L, 0L).ledger)
        val after = SliceScenario.bookKmOf(SliceScenario.resolve("suez_after", 0L, 0L).ledger)
        assertTrue(before < suezKm)
        assertTrue(after > suezKm)
        assertEquals(suezKm * 0.9, before, 0.05)
    }

    @Test
    fun `jump two chapters totals at hong kong`() {
        val r = SliceScenario.resolve("jump_two_chapters", 0L, 0L)
        val hkKm = RoutePosition.endKmOfNode("hong-kong")
        assertEquals(hkKm, SliceScenario.bookKmOf(r.ledger), 0.1)
        assertTrue(r.ledger.creditForDay(SliceScenario.PREV_DAY)!!.realMeters > 0L)
        assertTrue(r.ledger.creditForDay(SliceScenario.TODAY)!!.realMeters > 0L)
    }

    @Test
    fun `rollback replaces the aggregate downward via reconcile`() {
        val r = SliceScenario.resolve("rollback", 0L, 0L)
        val suezKm = RoutePosition.endKmOfNode("suez")
        val bookKm = SliceScenario.bookKmOf(r.ledger)
        // Final state after the replacing reconcile: 90% of the way to Suez.
        assertEquals(suezKm * 0.9, bookKm, 0.05)
        assertEquals(suezKm * 0.9 * 1000.0 / 20.0, r.ledger.totalRealMeters.toDouble(), 1.0)
    }

    @Test
    fun `finish reaches at least the full book length`() {
        val r = SliceScenario.resolve("finish", 0L, 0L)
        assertTrue(SliceScenario.bookKmOf(r.ledger) >= RoutePosition.totalKm)
    }

    @Test
    fun `unknown scenario falls back to empty start`() {
        val r = SliceScenario.resolve("nope", 0L, 0L)
        assertEquals(0L, r.ledger.totalRealMeters)
        assertTrue(r.description.contains("неизвестный сценарий"))
    }
}
