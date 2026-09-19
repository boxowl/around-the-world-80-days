package com.boxowl.aroundtheworld.expedition

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyScenesTest {
    @Test fun dayPhaseFollowsFixedBoundaries() {
        assertEquals(DayPhase.NIGHT, dayPhaseAt(LocalTime.of(0, 0)))
        assertEquals(DayPhase.NIGHT, dayPhaseAt(LocalTime.of(4, 59)))
        assertEquals(DayPhase.DAWN, dayPhaseAt(LocalTime.of(5, 0)))
        assertEquals(DayPhase.DAWN, dayPhaseAt(LocalTime.of(7, 59)))
        assertEquals(DayPhase.DAY, dayPhaseAt(LocalTime.of(8, 0)))
        assertEquals(DayPhase.DAY, dayPhaseAt(LocalTime.of(16, 59)))
        assertEquals(DayPhase.SUNSET, dayPhaseAt(LocalTime.of(17, 0)))
        assertEquals(DayPhase.SUNSET, dayPhaseAt(LocalTime.of(21, 59)))
        assertEquals(DayPhase.NIGHT, dayPhaseAt(LocalTime.of(22, 0)))
        assertEquals(DayPhase.NIGHT, dayPhaseAt(LocalTime.of(23, 59)))
    }

    @Test fun paletteMatchesPhaseAwayFromBoundaries() {
        assertEquals(1f, paletteFor(LocalTime.of(2, 0)).starAlpha)
        assertEquals(0f, paletteFor(LocalTime.of(12, 0)).starAlpha)
        assertEquals(0f, paletteFor(LocalTime.of(12, 0)).lightAlpha)
        assertEquals(1f, paletteFor(LocalTime.of(2, 0)).lightAlpha)
        assertEquals(phasePalette(DayPhase.DAWN), paletteFor(LocalTime.of(6, 30)))
    }

    @Test fun paletteBlendsWithinBoundaryHour() {
        val blended = paletteFor(LocalTime.of(5, 0))
        val expected = lerp(phasePalette(DayPhase.NIGHT), phasePalette(DayPhase.DAWN), 0.5f)
        assertEquals(expected, blended)
        val edge = paletteFor(LocalTime.of(4, 30))
        assertEquals(phasePalette(DayPhase.NIGHT), edge)
    }

    @Test fun sceneListAlignsWithRouteStops() {
        assertEquals(FIRST_LEG.size, JOURNEY_SCENES.size)
        FIRST_LEG.zip(JOURNEY_SCENES).forEach { (stop, scene) ->
            assertEquals(stop.id, scene.stopId)
            assertEquals(stop.name, scene.label)
        }
    }

    @Test fun sceneBlendDecomposesPosition() {
        val start = sceneBlendAt(0f, JOURNEY_SCENES.size)
        assertEquals(0, start.fromIndex)
        assertNull(start.toIndex)
        assertEquals(0f, start.fraction)

        val mid = sceneBlendAt(2.5f, JOURNEY_SCENES.size)
        assertEquals(2, mid.fromIndex)
        assertEquals(3, mid.toIndex)
        assertEquals(0.5f, mid.fraction, 1e-6f)

        val end = sceneBlendAt(9f, JOURNEY_SCENES.size)
        assertEquals(9, end.fromIndex)
        assertNull(end.toIndex)
        assertEquals(0f, end.fraction)

        val beyond = sceneBlendAt(15f, JOURNEY_SCENES.size)
        assertEquals(9, beyond.fromIndex)
        assertNull(beyond.toIndex)

        val negative = sceneBlendAt(-2f, JOURNEY_SCENES.size)
        assertEquals(0, negative.fromIndex)
        assertNull(negative.toIndex)
    }

    @Test fun celestialPositionStaysInsideCanvas() {
        for (minutes in 0 until 24 * 60 step 37) {
            val time = LocalTime.of(minutes / 60, minutes % 60)
            val pos = celestialPosition(time, dayPhaseAt(time))
            assertTrue("x in range at $time", pos.x in 0f..1f)
            assertTrue("y in range at $time", pos.y in 0f..1f)
        }
    }
}
