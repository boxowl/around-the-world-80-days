package com.boxowl.aroundtheworld.expedition.path

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for PathRouteV1 (V2.3a): the 28 visual "Path" segments, their
 * binding classes, thresholds derived from RouteDatasetV1, the hero schedule
 * and the no-reward rule. See docs/design/v23a-anchor-binding.md.
 */
class PathRouteV1Test {

    @Test
    fun `exactly 28 rows, each classified per the binding matrix`() {
        assertEquals(28, PathRouteV1.rows.size)
        assertEquals((1..28).toList(), PathRouteV1.rows.map { it.index })

        val expectedClasses: Map<Int, PathBindingClass> = buildMap {
            // Класс A: якорь-конец — узел набора на границе плеча.
            for (i in listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17, 18, 22, 25, 26, 27, 28)) {
                put(i, PathBindingClass.A)
            }
            // Класс B: интерполяция на плече — Бенарес (12) и Чикаго (23, waypoint
            // внутри leg-20-omaha-new-york, см. RouteDatasetV1).
            for (i in listOf(12, 23)) put(i, PathBindingClass.B)
            // Класс C: явные пробелы без порога.
            for (i in listOf(1, 19, 20, 21, 24)) put(i, PathBindingClass.C)
        }
        for (row in PathRouteV1.rows) {
            assertEquals(
                "row ${row.index} binding class",
                expectedClasses.getValue(row.index), row.bindingClass,
            )
        }
        // Класс D: спорные силуэты — океанские пакетботы и Tankadere.
        val disputed = PathRouteV1.rows.filter { it.silhouetteDisputed }.map { it.index }
        assertEquals(listOf(7, 8, 9, 16, 17, 18), disputed)
    }

    @Test
    fun `every class A and B reference exists in RouteDatasetV1`() {
        val nodeIds = RouteDatasetV1.nodes.map { it.id }.toSet()
        for (anchor in PathRouteV1.anchors) {
            when (val binding = anchor.binding) {
                is AnchorBinding.RouteStart -> assertTrue(anchor.id, binding.nodeId in nodeIds)
                is AnchorBinding.LegEnd -> {
                    assertTrue(anchor.id, binding.nodeId in nodeIds)
                    val leg = RouteDatasetV1.legs.single { it.id == binding.throughLegId }
                    assertEquals(anchor.id, binding.nodeId, leg.toId)
                }
                is AnchorBinding.OnLegWaypoint -> {
                    val leg = RouteDatasetV1.legs.single { it.id == binding.legId }
                    assertTrue(anchor.id, binding.waypointId in leg.waypointIds)
                }
                is AnchorBinding.Unbound -> Unit
            }
        }
        // Бенарес — первый waypoint плеча leg-11-allahabad-calcutta.
        val leg11 = RouteDatasetV1.legs.single { it.id == "leg-11-allahabad-calcutta" }
        assertEquals("benares", leg11.waypointIds.first())
    }

    @Test
    fun `thresholds of class A and B rows are monotonically non-decreasing`() {
        var previous = 0.0
        for (row in PathRouteV1.rows) {
            val threshold = PathRouteV1.endThresholdKm(row.index) ?: continue
            assertTrue(
                "row ${row.index}: threshold $threshold < previous $previous",
                threshold >= previous,
            )
            previous = threshold
        }
    }

    @Test
    fun `finish threshold of row 28 equals the measured total`() {
        assertEquals(RouteMeasure.totalKm(), PathRouteV1.endThresholdKm(28)!!, 1e-6)
    }

    @Test
    fun `class C rows have no threshold and carry a documented gap`() {
        for (row in PathRouteV1.rows.filter { it.bindingClass == PathBindingClass.C }) {
            assertNull("row ${row.index} must not have an end threshold", PathRouteV1.endThresholdKm(row.index))
            val binding = PathRouteV1.anchor(row.endAnchorId).binding
            assertTrue(binding is AnchorBinding.Unbound)
            val gap = (binding as AnchorBinding.Unbound).gap
            assertTrue("row ${row.index} gap question", gap.questionRu.isNotBlank())
            assertTrue("row ${row.index} gap variants", gap.variantsRu.isNotEmpty())
            assertTrue("row ${row.index} gap sources", gap.sourcesRu.isNotBlank())
        }
    }

    @Test
    fun `benares threshold lies strictly inside leg-11`() {
        val cumulative = RouteMeasure.cumulativeKm()
        val legs = RouteDatasetV1.legs
        val legIndex = legs.indexOfFirst { it.id == "leg-11-allahabad-calcutta" }
        val legStart = cumulative[legIndex - 1]
        val legEnd = cumulative[legIndex]
        val benares = PathRouteV1.anchorThresholdKm("benares")!!
        assertTrue("benares $benares must exceed leg start $legStart", benares > legStart)
        assertTrue("benares $benares must precede leg end $legEnd", benares < legEnd)
        // Строка 13 стартует из того же якоря: пороги совпадают.
        assertEquals(benares, PathRouteV1.endThresholdKm(12)!!, 1e-9)
        assertEquals(benares, PathRouteV1.startThresholdKm(13)!!, 1e-9)
    }

    @Test
    fun `hero schedule matches the owner contract`() {
        for (row in PathRouteV1.rows) {
            assertTrue("row ${row.index}: Fogg is always present", PathHero.FOGG in row.heroes)
            assertEquals(
                "row ${row.index}: Passepartout from row 2",
                row.index >= 2, PathHero.PASSEPARTOUT in row.heroes,
            )
            assertEquals(
                "row ${row.index}: Aouda from row 11",
                row.index >= 11, PathHero.AOUDA in row.heroes,
            )
        }
    }

    @Test
    fun `visual anchors grant no reward`() {
        for (row in PathRouteV1.rows) {
            assertEquals("row ${row.index}", PathReward.NONE, row.reward)
        }
    }

    @Test
    fun `row 2 starts at the London station with threshold zero`() {
        assertEquals(0.0, PathRouteV1.startThresholdKm(2)!!, 1e-12)
        assertEquals("london-station", PathRouteV1.row(2).startAnchorId)
        assertEquals("london", (PathRouteV1.anchor("london-station").binding as AnchorBinding.RouteStart).nodeId)
    }

    @Test
    fun `every historical transport of the contract is used`() {
        val used = PathRouteV1.rows.map { it.historicalTransport }.toSet()
        assertEquals(HistoricalTransport.entries.toSet(), used)
    }

    @Test
    fun `end threshold of a bound row equals the next row start threshold`() {
        for (row in PathRouteV1.rows) {
            val next = PathRouteV1.rows.getOrNull(row.index) ?: break
            val end = PathRouteV1.endThresholdKm(row.index)
            val nextStart = PathRouteV1.startThresholdKm(next.index)
            if (end != null && nextStart != null) {
                assertEquals("rows ${row.index}/${next.index}", end, nextStart, 1e-9)
            }
        }
        // Контроль: не все пары пустые.
        assertNotNull(PathRouteV1.endThresholdKm(2))
    }
}
