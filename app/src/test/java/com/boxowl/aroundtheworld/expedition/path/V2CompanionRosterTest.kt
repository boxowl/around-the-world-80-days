package com.boxowl.aroundtheworld.expedition.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class V2CompanionRosterTest {
    @Test fun joinsAtMeasuredKholbyBoundaryAndLeavesOnCorrection() {
        val joins = requireNotNull(PathRouteV1.startThresholdKm(11))
        val first = setOf(PathHero.FOGG, PathHero.PASSEPARTOUT)
        val withAouda = first + PathHero.AOUDA
        assertEquals(first, V2CompanionRoster.at(0.0))
        assertEquals(first, V2CompanionRoster.at(Math.nextDown(joins)))
        assertEquals(withAouda, V2CompanionRoster.at(joins))
        assertEquals(withAouda, V2CompanionRoster.at(PathRouteV1.endThresholdKm(28)!!))
        assertEquals(first, V2CompanionRoster.at(Math.nextDown(joins)))
        assertEquals("Фогг, Паспарту, Ауда", V2CompanionRoster.label(joins))
    }

    @Test fun invalidDistancesAreRejected() {
        for (bookKm in listOf(-1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { V2CompanionRoster.at(bookKm) }
        }
    }
}
