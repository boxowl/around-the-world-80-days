package com.boxowl.aroundtheworld.expedition.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * P08 contract tests for the boarding geometry: the pier is flat stone until a
 * short gangway, the deck is flat until the Suez lip, and [TerrainProfile.supportAt]
 * classifies LAND / PIER_RAMP / DECK / BANK exactly at their world boundaries.
 */
class TerrainSupportTest {
    private val layout = FIRST_LEG_LAYOUT
    private val a8 = layout.anchorX(8)
    private val a9 = layout.anchorX(9)
    private val rampStart = TerrainProfile.rampStartX(layout)

    @Test fun supportClassificationMatchesWorldBoundaries() {
        assertEquals(TerrainProfile.Support.LAND, TerrainProfile.supportAt(layout, 0f))
        assertEquals(TerrainProfile.Support.LAND, TerrainProfile.supportAt(layout, layout.anchorX(0)))
        assertEquals(TerrainProfile.Support.LAND, TerrainProfile.supportAt(layout, rampStart - 1e-4f))
        assertEquals(TerrainProfile.Support.PIER_RAMP, TerrainProfile.supportAt(layout, rampStart))
        assertEquals(TerrainProfile.Support.PIER_RAMP, TerrainProfile.supportAt(layout, (rampStart + a8) / 2))
        assertEquals(TerrainProfile.Support.PIER_RAMP, TerrainProfile.supportAt(layout, a8 - 1e-4f))
        assertEquals(TerrainProfile.Support.DECK, TerrainProfile.supportAt(layout, a8))
        assertEquals(TerrainProfile.Support.DECK, TerrainProfile.supportAt(layout, (a8 + a9) / 2))
        assertEquals(TerrainProfile.Support.DECK, TerrainProfile.supportAt(layout, a9 - 1e-4f))
        assertEquals(TerrainProfile.Support.BANK, TerrainProfile.supportAt(layout, a9))
        assertEquals(TerrainProfile.Support.BANK, TerrainProfile.supportAt(layout, layout.end))
    }

    @Test fun rampEndsExactlyAtTheDeckAnchor() {
        assertEquals(a8 - TerrainProfile.RAMP_LEN, rampStart, 1e-6f)
        assertEquals(0.68f, TerrainProfile.groundYAt(layout, rampStart), 1e-4f)
        assertEquals(0.83f, TerrainProfile.groundYAt(layout, a8), 1e-4f)
    }

    @Test fun pierIsFlatBeforeTheGangway() {
        var wx = layout.anchorX(7)
        while (wx <= rampStart) {
            assertEquals(0.68f, TerrainProfile.groundYAt(layout, wx), 1e-4f)
            assertEquals(0f, TerrainProfile.groundSlopeAt(layout, wx), 1e-4f)
            wx += 0.05f
        }
    }

    @Test fun deckIsFlatUntilTheSuezLip() {
        var wx = a8
        val lip = a8 + (a9 - a8) * 0.85f
        while (wx <= lip) {
            assertEquals(0.83f, TerrainProfile.groundYAt(layout, wx), 1e-4f)
            wx += 0.05f
        }
        assertEquals(0.78f, TerrainProfile.groundYAt(layout, a9), 1e-4f)
    }

    @Test fun profileIsContinuousAcrossRampDeckAndLip() {
        val eps = 1e-5f
        for (wx in listOf(rampStart, a8, a8 + (a9 - a8) * 0.85f, a9)) {
            val left = TerrainProfile.groundYAt(layout, wx - eps)
            val at = TerrainProfile.groundYAt(layout, wx)
            val right = TerrainProfile.groundYAt(layout, wx + eps)
            assertTrue("value diverges at $wx: $left/$at/$right",
                abs(left - at) < 1e-3f && abs(right - at) < 1e-3f)
            val sLeft = TerrainProfile.groundSlopeAt(layout, wx - eps)
            val sAt = TerrainProfile.groundSlopeAt(layout, wx)
            val sRight = TerrainProfile.groundSlopeAt(layout, wx + eps)
            assertTrue("slope diverges at $wx: $sLeft/$sAt/$sRight",
                abs(sLeft - sAt) < 0.05f && abs(sRight - sAt) < 0.05f)
        }
    }

    @Test fun bankStaysFlatAfterSuez() {
        var wx = a9
        while (wx <= layout.end) {
            assertEquals(0.78f, TerrainProfile.groundYAt(layout, wx), 1e-4f)
            wx += 0.05f
        }
    }
}
