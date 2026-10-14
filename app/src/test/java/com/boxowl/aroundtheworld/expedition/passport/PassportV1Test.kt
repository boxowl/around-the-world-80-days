package com.boxowl.aroundtheworld.expedition.passport

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import java.time.Instant
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Контрактные тесты чистой доменной модели паспорта v2
 * (docs/design/v24a-passport.md). Хранение, восстановление, backfill дат и
 * источник календаря — V2.4b, здесь не проверяются.
 */
class PassportV1Test {

    private val t1: Instant = Instant.ofEpochSecond(1_760_000_000L)
    private val t2: Instant = Instant.ofEpochSecond(1_760_100_000L)

    private fun legIndex(legId: String): Int {
        val index = RouteDatasetV1.legs.indexOfFirst { it.id == legId }
        assertTrue("leg not found in dataset: $legId", index >= 0)
        return index
    }

    private fun ceilMeters(thresholdBookMeters: Double): Long = ceil(thresholdBookMeters).toLong()

    @Test
    fun `exactly nine stamps with stable unique string ids`() {
        assertEquals(9, StampId.entries.size)
        assertEquals(9, StampId.entries.map { it.id }.distinct().size)
        val expected = mapOf(
            StampId.LONDON_START to "stamp-london-start",
            StampId.SUEZ_FINISH to "stamp-suez-finish",
            StampId.BOMBAY_FINISH to "stamp-bombay-finish",
            StampId.CALCUTTA_FINISH to "stamp-calcutta-finish",
            StampId.HONG_KONG_FINISH to "stamp-hong-kong-finish",
            StampId.YOKOHAMA_FINISH to "stamp-yokohama-finish",
            StampId.SAN_FRANCISCO_FINISH to "stamp-san-francisco-finish",
            StampId.NEW_YORK_FINISH to "stamp-new-york-finish",
            StampId.LONDON_FINISH to "stamp-london-finish",
        )
        for ((stampId, id) in expected) {
            assertEquals(id, stampId.id)
        }
    }

    @Test
    fun `london start and finish are distinct stamps of the same node`() {
        assertNotEquals(StampId.LONDON_START, StampId.LONDON_FINISH)
        assertNotEquals(StampId.LONDON_START.id, StampId.LONDON_FINISH.id)
        assertEquals("london", StampId.LONDON_START.nodeId)
        assertEquals("london", StampId.LONDON_FINISH.nodeId)
        assertEquals(0.0, PassportV1.thresholdBookMeters(StampId.LONDON_START), 0.0)
        assertEquals(
            RouteMeasure.totalKm() * 1000.0,
            PassportV1.thresholdBookMeters(StampId.LONDON_FINISH),
            0.0,
        )
    }

    @Test
    fun `chapter finish thresholds come from route geometry not literals`() {
        val expectedLegs = listOf(
            StampId.SUEZ_FINISH to "leg-06b-port-said-suez",
            StampId.BOMBAY_FINISH to "leg-08-aden-bombay",
            StampId.CALCUTTA_FINISH to "leg-11-allahabad-calcutta",
            StampId.HONG_KONG_FINISH to "leg-13-singapore-hong-kong",
            StampId.YOKOHAMA_FINISH to "leg-15-shanghai-mouth-yokohama",
            StampId.SAN_FRANCISCO_FINISH to "leg-16-yokohama-san-francisco",
            StampId.NEW_YORK_FINISH to "leg-20-omaha-new-york",
        )
        val cumulative = RouteMeasure.cumulativeKm()
        for ((stampId, legId) in expectedLegs) {
            assertEquals(
                cumulative[legIndex(legId)] * 1000.0,
                PassportV1.thresholdBookMeters(stampId),
                0.0,
            )
        }
    }

    @Test
    fun `thresholds strictly increase along the route`() {
        val values = StampId.entries.map(PassportV1::thresholdBookMeters)
        assertEquals(StampId.entries.size, values.size)
        for (i in 1 until values.size) {
            assertTrue("${values[i - 1]} < ${values[i]}", values[i - 1] < values[i])
        }
    }

    @Test
    fun `empty passport at zero progress grants only london start`() {
        val snapshot = PassportV1.reconcile(PassportSnapshot.of(), 0L, t1)
        assertEquals(mapOf(StampId.LONDON_START to t1), snapshot.granted)
    }

    @Test
    fun `empty passport at full route length grants all nine stamps`() {
        val full = ceilMeters(PassportV1.thresholdBookMeters(StampId.LONDON_FINISH))
        val snapshot = PassportV1.reconcile(PassportSnapshot.of(), full, t1)
        assertEquals(StampId.entries.toSet(), snapshot.granted.keys)
        assertTrue(snapshot.granted.values.all { it == t1 })
    }

    @Test
    fun `partially granted passport keeps old dates and only adds new stamps`() {
        val partial = PassportSnapshot.of(
            mapOf(StampId.LONDON_START to t1, StampId.SUEZ_FINISH to t2),
        )
        val bombayMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.BOMBAY_FINISH))
        val snapshot = PassportV1.reconcile(partial, bombayMeters, t1)
        assertEquals(t1, snapshot.granted[StampId.LONDON_START])
        assertEquals(t2, snapshot.granted[StampId.SUEZ_FINISH])
        assertEquals(t1, snapshot.granted[StampId.BOMBAY_FINISH])
        assertEquals(3, snapshot.granted.size)
    }

    @Test
    fun `jump across several chapters grants all intermediate stamps`() {
        val hongKongMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.HONG_KONG_FINISH))
        val snapshot = PassportV1.reconcile(PassportSnapshot.of(), hongKongMeters, t1)
        assertEquals(
            setOf(
                StampId.LONDON_START,
                StampId.SUEZ_FINISH,
                StampId.BOMBAY_FINISH,
                StampId.CALCUTTA_FINISH,
                StampId.HONG_KONG_FINISH,
            ),
            snapshot.granted.keys,
        )
    }

    @Test
    fun `progress rollback does not revoke granted stamps`() {
        val hongKongMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.HONG_KONG_FINISH))
        val granted = PassportV1.reconcile(PassportSnapshot.of(), hongKongMeters, t1)
        assertEquals(5, granted.granted.size)
        val afterRollback = PassportV1.reconcile(granted, 0L, t2)
        assertEquals(granted, afterRollback)
        assertEquals(5, afterRollback.granted.size)
    }

    @Test
    fun `reconcile is idempotent for the same arguments`() {
        val bombayMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.BOMBAY_FINISH))
        val once = PassportV1.reconcile(PassportSnapshot.of(), bombayMeters, t1)
        val twice = PassportV1.reconcile(once, bombayMeters, t1)
        assertEquals(once, twice)
        assertEquals(once.hashCode(), twice.hashCode())
    }

    @Test
    fun `new crossing gets observedAt and null observedAt stays null`() {
        val suezMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.SUEZ_FINISH))
        val dated = PassportV1.reconcile(PassportSnapshot.of(), suezMeters, t1)
        assertEquals(t1, dated.granted[StampId.SUEZ_FINISH])
        val undated = PassportV1.reconcile(PassportSnapshot.of(), suezMeters)
        assertTrue(undated.granted.containsKey(StampId.SUEZ_FINISH))
        assertNull(undated.granted[StampId.SUEZ_FINISH])
    }

    @Test
    fun `existing dates are not overwritten by a new observedAt`() {
        val partial = PassportSnapshot.of(
            mapOf(StampId.LONDON_START to t1, StampId.SUEZ_FINISH to null),
        )
        val bombayMeters = ceilMeters(PassportV1.thresholdBookMeters(StampId.BOMBAY_FINISH))
        val snapshot = PassportV1.reconcile(partial, bombayMeters, t2)
        assertEquals(t1, snapshot.granted[StampId.LONDON_START])
        assertTrue(snapshot.granted.containsKey(StampId.SUEZ_FINISH))
        assertNull(snapshot.granted[StampId.SUEZ_FINISH])
        assertEquals(t2, snapshot.granted[StampId.BOMBAY_FINISH])
    }

    @Test
    fun `suez threshold boundary is exact without hidden tolerance`() {
        // Порог Суэца дробный (4 339 152.5 книжных метров): floor не хватает,
        // ceil достаточно. Округлять порог до сравнения нельзя.
        val threshold = PassportV1.thresholdBookMeters(StampId.SUEZ_FINISH)
        assertTrue("suez threshold is fractional", threshold != Math.floor(threshold))
        val floor = threshold.toLong()
        val below = PassportV1.reconcile(PassportSnapshot.of(), floor, t1)
        assertFalse(below.granted.containsKey(StampId.SUEZ_FINISH))
        val at = PassportV1.reconcile(PassportSnapshot.of(), floor + 1, t1)
        assertTrue(at.granted.containsKey(StampId.SUEZ_FINISH))
    }

    @Test
    fun `london finish threshold boundary is exact without hidden tolerance`() {
        // Финальный порог дробный (40 501 820.8 книжных метров): floor не
        // хватает только финишному Лондону, остальные 8 уже выданы.
        val threshold = PassportV1.thresholdBookMeters(StampId.LONDON_FINISH)
        assertTrue("finish threshold is fractional", threshold != Math.floor(threshold))
        val floor = threshold.toLong()
        val below = PassportV1.reconcile(PassportSnapshot.of(), floor, t1)
        assertEquals(8, below.granted.size)
        assertFalse(below.granted.containsKey(StampId.LONDON_FINISH))
        val at = PassportV1.reconcile(PassportSnapshot.of(), floor + 1, t1)
        assertEquals(9, at.granted.size)
        assertTrue(at.granted.containsKey(StampId.LONDON_FINISH))
    }

    @Test
    fun `mutating the source map after creation does not change the snapshot`() {
        val source = mutableMapOf<StampId, Instant?>(StampId.LONDON_START to t1)
        val snapshot = PassportSnapshot.of(source)
        source[StampId.LONDON_START] = t2
        source[StampId.SUEZ_FINISH] = t2
        assertEquals(mapOf(StampId.LONDON_START to t1), snapshot.granted)
        assertEquals(PassportSnapshot.of(mapOf(StampId.LONDON_START to t1)), snapshot)
    }

    @Test
    fun `exposed granted map rejects mutation even through a mutable cast`() {
        val snapshot = PassportSnapshot.of(mapOf(StampId.LONDON_START to t1))
        @Suppress("UNCHECKED_CAST")
        val casted = snapshot.granted as MutableMap<StampId, Instant?>
        assertThrows(UnsupportedOperationException::class.java) {
            casted[StampId.SUEZ_FINISH] = t1
        }
        assertEquals(mapOf(StampId.LONDON_START to t1), snapshot.granted)
    }

    @Test
    fun `reconcile does not mutate the input snapshot`() {
        val input = PassportSnapshot.of(mapOf(StampId.LONDON_START to t1))
        val before = input.granted
        val full = ceilMeters(PassportV1.thresholdBookMeters(StampId.LONDON_FINISH))
        PassportV1.reconcile(input, full, t2)
        assertEquals(mapOf(StampId.LONDON_START to t1), input.granted)
        assertEquals(mapOf(StampId.LONDON_START to t1), before)
    }

    @Test
    fun `negative progress is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PassportV1.reconcile(PassportSnapshot.of(), -1L, t1)
        }
    }
}
