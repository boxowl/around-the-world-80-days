package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.NodeRole.PORT
import com.boxowl.aroundtheworld.expedition.route.NodeRole.STOP
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.expedition.route.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the V2.4c endpoint-only stop cards: exact count,
 * selection rule (STOP/PORT leg endpoints only), london's two distinct
 * occurrences, thresholds pinned to [RouteMeasure] (never literals), and the
 * pure `reached` semantics.
 */
class GlobeStopCardsV1Test {

    private val cards: List<GlobeStopCard> = GlobeStopCardsV1.cards
    private val byOccurrence: Map<String, GlobeStopCard> = cards.associateBy { it.occurrenceId }

    @Test
    fun `exactly 25 cards with unique occurrence ids`() {
        assertEquals(25, cards.size)
        assertEquals(25, byOccurrence.size)
    }

    @Test
    fun `every card is a STOP or PORT endpoint of at least one leg`() {
        for (card in cards) {
            val node = RouteDatasetV1.node(card.nodeId)
            assertTrue("$card: role", node.role == STOP || node.role == PORT)
            assertTrue(
                "$card: not a leg endpoint",
                RouteDatasetV1.legs.any { it.fromId == card.nodeId || it.toId == card.nodeId },
            )
            assertEquals(card.nameRu, node.nameRu)
        }
    }

    @Test
    fun `london has two distinct occurrences for start and finish`() {
        val start = byOccurrence.getValue("london#1")
        val finish = byOccurrence.getValue("london#2")

        assertNotEquals(start.occurrenceId, finish.occurrenceId)
        assertNotEquals(start.thresholdBookKm, finish.thresholdBookKm)
        assertNotEquals(start.arrivalTransport, finish.arrivalTransport)
        assertNotEquals(start.arrivalChapter, finish.arrivalChapter)

        // Start: no arriving leg — transport is not invented; chapter of the first outgoing leg.
        assertEquals(0.0, start.thresholdBookKm, 0.0)
        assertNull(start.arrivalTransport)
        assertEquals(3, start.arrivalChapter)
        assertEquals(RouteDatasetV1.legs.first().chapter, start.arrivalChapter)

        // Finish: the arriving leg is leg-24 (TRAIN, chapter 34), threshold is the full route.
        assertEquals(RouteMeasure.totalKm(), finish.thresholdBookKm, 0.0)
        assertEquals(Transport.TRAIN, finish.arrivalTransport)
        assertEquals(34, finish.arrivalChapter)
        assertEquals(RouteDatasetV1.legs.last().chapter, finish.arrivalChapter)

        assertEquals(listOf("london#1", "london#2"), cards.filter { it.nodeId == "london" }.map { it.occurrenceId })
    }

    @Test
    fun `mid-leg STOP or CALL waypoints have no card`() {
        // chicago is STOP but only a waypoint of leg-20 — being in waypointIds is not being an endpoint.
        val chicago = RouteDatasetV1.node("chicago")
        assertEquals(STOP, chicago.role)
        assertTrue(RouteDatasetV1.legs.any { "chicago" in it.waypointIds })
        assertFalse(RouteDatasetV1.legs.any { it.fromId == "chicago" || it.toId == "chicago" })
        assertFalse(cards.any { it.nodeId == "chicago" })

        // nagasaki is CALL and a mid-leg waypoint of leg-15.
        assertTrue(RouteDatasetV1.legs.any { "nagasaki" in it.waypointIds })
        assertFalse(cards.any { it.nodeId == "nagasaki" })

        // WAYPOINT-role leg endpoints have no card in this slice.
        assertFalse(cards.any { it.nodeId == "hooghly-mouth" })
        assertFalse(cards.any { it.nodeId == "shanghai-mouth" })
    }

    @Test
    fun `thresholds come from RouteMeasure cumulative and are strictly increasing`() {
        val legs = RouteDatasetV1.legs
        val cumulativeKm = RouteMeasure.cumulativeKm()

        for (card in cards) {
            if (card.occurrenceId == "london#1") continue
            val legIndex = legs.indexOfFirst { it.toId == card.nodeId }
            assertTrue("$card: no arriving leg", legIndex >= 0)
            assertEquals("$card: threshold", cumulativeKm[legIndex], card.thresholdBookKm, 0.0)
            assertEquals("$card: chapter", legs[legIndex].chapter, card.arrivalChapter)
            assertEquals("$card: transport", legs[legIndex].transport, card.arrivalTransport)
        }

        val thresholds = cards.map { it.thresholdBookKm }
        assertTrue(
            "thresholds must be strictly increasing in occurrence order",
            thresholds.zipWithNext().all { (a, b) -> a < b },
        )
        assertEquals(RouteMeasure.totalKm(), thresholds.last(), 0.0)
    }

    @Test
    fun `reached at zero is true only for london start`() {
        val reachedAtZero = cards.filter { GlobeStopCardsV1.reached(it, 0.0) }
        assertEquals(listOf("london#1"), reachedAtZero.map { it.occurrenceId })
    }

    @Test
    fun `reached is inclusive at the exact threshold and false just below`() {
        for (card in cards) {
            val thresholdMeters = card.thresholdBookKm * 1000.0
            assertTrue("$card at threshold", GlobeStopCardsV1.reached(card, thresholdMeters))
            if (card.thresholdBookKm > 0.0) {
                assertFalse("$card below threshold", GlobeStopCardsV1.reached(card, thresholdMeters - 1.0))
            }
        }
    }

    @Test
    fun `reached at total distance covers all 25 cards`() {
        val totalMeters = RouteMeasure.totalKm() * 1000.0
        assertEquals(25, cards.count { GlobeStopCardsV1.reached(it, totalMeters) })
    }

    @Test
    fun `reached is a pure function without memory of previous progress`() {
        val finish = byOccurrence.getValue("london#2")
        val halfwayMeters = RouteMeasure.totalKm() * 1000.0 / 2.0
        assertFalse(GlobeStopCardsV1.reached(finish, halfwayMeters))
        assertTrue(GlobeStopCardsV1.reached(finish, RouteMeasure.totalKm() * 1000.0))
        // Rollback of totalBookMeters flips the derived state back — no latch, no memory.
        assertFalse(GlobeStopCardsV1.reached(finish, halfwayMeters))
    }
}
