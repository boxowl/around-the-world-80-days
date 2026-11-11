package com.boxowl.aroundtheworld.expedition.globe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V2BrowsePositionTest {
    @Test fun followsConfirmedWhenNotBrowsing() {
        assertEquals(15_000L, V2BrowsePosition.effective(null, 15_000L))
        assertFalse(V2BrowsePosition.isBrowsing(null, 15_000L))
    }

    @Test fun requestedPositionNeverEntersFutureOrNegativeDistance() {
        assertEquals(0L, V2BrowsePosition.effective(-1L, 15_000L))
        assertEquals(15_000L, V2BrowsePosition.effective(20_000L, 15_000L))
        assertEquals(0L, V2BrowsePosition.fromFraction(-1f, 15_000L))
        assertEquals(15_000L, V2BrowsePosition.fromFraction(2f, 15_000L))
    }

    @Test fun backwardCorrectionSqueezesStoredBrowse() {
        assertNull(V2BrowsePosition.squeeze(8_000L, 6_000L))
        assertEquals(4_000L, V2BrowsePosition.squeeze(4_000L, 6_000L))
        assertEquals(6_000L, V2BrowsePosition.effective(8_000L, 6_000L))
        assertFalse(V2BrowsePosition.isBrowsing(8_000L, 6_000L))
    }

    @Test fun zeroHasNoBrowseAndHalfwayIsStable() {
        assertEquals(0L, V2BrowsePosition.fromFraction(0.5f, 0L))
        assertFalse(V2BrowsePosition.isBrowsing(null, 0L))
        assertEquals(7_500L, V2BrowsePosition.fromFraction(0.5f, 15_000L))
        assertTrue(V2BrowsePosition.isBrowsing(7_500L, 15_000L))
    }
}
