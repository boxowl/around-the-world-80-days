package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V2CurrentTransportTest {
    @Test fun firstBoundaryUsesArrivingLegThenNextLeg() {
        val firstEnd = RouteMeasure.cumulativeKm().first()
        val first = V2CurrentTransport.at(0.0)
        assertEquals(RouteDatasetV1.node(RouteDatasetV1.legs.first().fromId).nameRu, first.from)
        assertEquals("Поезд", first.transport)
        assertEquals(first, V2CurrentTransport.at(firstEnd))
        val next = V2CurrentTransport.at(Math.nextUp(firstEnd))
        assertEquals("Паром", next.transport)
        assertEquals(first.to, next.from)
    }

    @Test fun suezBoundaryAndRollbackFollowMeasuredGeometry() {
        val index = RouteDatasetV1.legs.indexOfFirst { it.toId == "suez" }
        val suezEnd = RouteMeasure.cumulativeKm()[index]
        val arrived = V2CurrentTransport.at(suezEnd)
        assertEquals("Суэц", arrived.to)
        assertEquals("Водный путь", arrived.transport)
        assertEquals(8, arrived.chapter)
        val after = V2CurrentTransport.at(Math.nextUp(suezEnd))
        assertEquals("Суэц", after.from)
        assertEquals("Судно", after.transport)
        assertEquals(arrived, V2CurrentTransport.at(suezEnd))
    }

    @Test fun finalLegAndCompletionAreStableAfterRouteEnd() {
        val end = RouteMeasure.totalKm()
        val final = V2CurrentTransport.at(end)
        assertEquals("Лондон", final.to)
        assertTrue(final.completed)
        assertEquals(final, V2CurrentTransport.at(end + 1000.0))
        assertFalse(V2CurrentTransport.at(Math.nextDown(end)).completed)
    }
}
