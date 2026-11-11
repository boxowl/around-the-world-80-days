package com.boxowl.aroundtheworld.expedition.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DistanceLightingTest {
    @Test fun lightAndDarkPlateausAndBoundaryMidpoints() {
        assertEquals(0.5, DistanceLighting.darknessAtRealKm(0.0), 1e-12)
        assertEquals(0.0, DistanceLighting.darknessAtRealKm(0.5), 1e-12)
        assertEquals(0.0, DistanceLighting.darknessAtRealKm(19.5), 1e-12)
        assertEquals(0.5, DistanceLighting.darknessAtRealKm(20.0), 1e-12)
        assertEquals(1.0, DistanceLighting.darknessAtRealKm(20.5), 1e-12)
        assertEquals(1.0, DistanceLighting.darknessAtRealKm(29.5), 1e-12)
        assertEquals(0.5, DistanceLighting.darknessAtRealKm(30.0), 1e-12)
    }

    @Test fun repeatsEveryThirtyKmWithoutBoundaryJump() {
        for (offset in listOf(0.0, 0.1, 0.5, 10.0, 19.9, 20.0, 20.1, 25.0, 29.9)) {
            val expected = DistanceLighting.darknessAtRealKm(offset)
            assertEquals(expected, DistanceLighting.darknessAtRealKm(offset + 30.0), 1e-12)
            assertEquals(expected, DistanceLighting.darknessAtRealKm(offset + 60.0), 1e-12)
        }
        assertEquals(
            DistanceLighting.darknessAtRealKm(30.0 - 1e-6),
            DistanceLighting.darknessAtRealKm(30.0 + 1e-6),
            1e-5,
        )
    }

    @Test fun transitionsAreSmoothAndMonotone() {
        assertTrue(DistanceLighting.darknessAtRealKm(19.75) < DistanceLighting.darknessAtRealKm(20.0))
        assertTrue(DistanceLighting.darknessAtRealKm(20.0) < DistanceLighting.darknessAtRealKm(20.25))
        assertTrue(DistanceLighting.darknessAtRealKm(29.75) > DistanceLighting.darknessAtRealKm(30.0))
        assertTrue(DistanceLighting.darknessAtRealKm(30.0) > DistanceLighting.darknessAtRealKm(30.25))
    }

    @Test fun browsingPastPositionDoesNotDependOnCallOrder() {
        val future = DistanceLighting.darknessAtRealKm(45.0)
        assertEquals(0.0, DistanceLighting.darknessAtRealKm(5.0), 1e-12)
        assertEquals(future, DistanceLighting.darknessAtRealKm(45.0), 1e-12)
    }

    @Test fun invalidDistancesAreRejected() {
        for (bad in listOf(-0.01, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { DistanceLighting.darknessAtRealKm(bad) }
        }
    }
}
