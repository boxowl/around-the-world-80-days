package com.boxowl.aroundtheworld.expedition.globe

import androidx.compose.ui.geometry.Offset
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2ChapterMarkerHitTestTest {
    @Test fun centeredSuezIsTappableWithinRadiusButNotBeyond() {
        val suez = RouteDatasetV1.node("suez")
        val targets = V2ChapterMarkerHitTest.targets(Offset(100f, 100f), 80f, suez.lat, suez.lon)
        val mark = targets.single { it.occurrenceId == "suez#1" }
        assertEquals(100f, mark.center.x, 0.01f)
        assertEquals(100f, mark.center.y, 0.01f)
        assertEquals("suez#1", V2ChapterMarkerHitTest.hit(targets, mark.center, 24f))
        assertEquals("suez#1", V2ChapterMarkerHitTest.hit(listOf(mark), Offset(124f, 100f), 24f))
        assertNull(V2ChapterMarkerHitTest.hit(listOf(mark), Offset(125f, 100f), 24f))
    }

    @Test fun backHemisphereMarkersNeverBecomeTargets() {
        val suez = RouteDatasetV1.node("suez")
        val targets = V2ChapterMarkerHitTest.targets(Offset.Zero, 80f, suez.lat, suez.lon + 180.0)
        assertFalse(targets.any { it.occurrenceId == "suez#1" })
    }

    @Test fun londonMarkSelectsFinishOccurrence() {
        val london = RouteDatasetV1.node("london")
        val targets = V2ChapterMarkerHitTest.targets(Offset(100f, 100f), 80f, london.lat, london.lon)
        assertTrue(targets.any { it.occurrenceId == "london#2" })
        assertFalse(targets.any { it.occurrenceId == "london#1" })
        assertEquals("london#2", V2ChapterMarkerHitTest.hit(targets, Offset(100f, 100f), 24f))
    }
}
