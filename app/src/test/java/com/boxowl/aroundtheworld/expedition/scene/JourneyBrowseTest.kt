package com.boxowl.aroundtheworld.expedition.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * V2.1 browse-over-the-past contract tests (owner prompt §3.1): the visual
 * browse position is strictly separated from the confirmed progress position,
 * can never pass it (drag, inertia and backward corrections included), and
 * returning to the confirmed position is idempotent. Pure functions only —
 * the gesture/camera wiring lives in SeamlessJourneyCanvas.
 */
class JourneyBrowseTest {

    private val max = 9f

    @Test fun browseNeverExceedsConfirmed() {
        assertEquals(4f, JourneyBrowse.clampBrowse(6f, 4f, max), 0f)
        assertEquals(4f, JourneyBrowse.clampBrowse(4f, 4f, max), 0f)
        assertEquals(2.5f, JourneyBrowse.clampBrowse(2.5f, 4f, max), 0f)
        // For every combination the result stays at or behind the confirmed edge.
        for (confirmed in listOf(0f, 0.3f, 2f, 9f)) {
            for (browse in listOf(-1f, 0f, 1.7f, 5f, 20f)) {
                val clamped = JourneyBrowse.clampBrowse(browse, confirmed, max)
                assertTrue("browse $browse beyond confirmed $confirmed", clamped <= confirmed)
                assertTrue(clamped >= 0f)
            }
        }
    }

    @Test fun clampRespectsRouteBounds() {
        assertEquals(0f, JourneyBrowse.clampBrowse(-2f, 5f, max), 0f)
        // A confirmed value beyond the route end is itself clamped first.
        assertEquals(max, JourneyBrowse.clampBrowse(20f, 15f, max), 0f)
        assertEquals(0f, JourneyBrowse.clampBrowse(3f, -1f, max), 0f)
    }

    @Test fun dragPixelsConvertToPositionsWithContentFollowingFinger() {
        val heightPx = 1000f
        // Swipe right by a full canvas height looks back exactly one world unit.
        assertEquals(-1f / SEGMENT_LENGTH, JourneyBrowse.dragDeltaPositions(heightPx, heightPx), 1e-6f)
        // Swipe left moves forward, same magnitude.
        assertEquals(1f / SEGMENT_LENGTH, JourneyBrowse.dragDeltaPositions(-heightPx, heightPx), 1e-6f)
        // Half the pixels, half the delta; taller canvas, smaller delta.
        assertEquals(
            JourneyBrowse.dragDeltaPositions(heightPx, heightPx) / 2f,
            JourneyBrowse.dragDeltaPositions(heightPx / 2f, heightPx),
            1e-6f,
        )
        assertEquals(0f, JourneyBrowse.dragDeltaPositions(heightPx, 0f), 0f)
    }

    @Test fun inertiaDecaysExponentiallyAndKeepsSign() {
        val v0 = 2f
        var v = v0
        var elapsed = 0f
        while (elapsed < 2f) {
            val next = JourneyBrowse.inertiaVelocityAt(v, 0.016f)
            assertTrue("inertia grew at ${elapsed}s", abs(next) <= abs(v))
            assertTrue(next > 0f)
            v = next
            elapsed += 0.016f
        }
        assertTrue("inertia did not decay: $v", v < v0 * 0.01f)
        assertTrue(JourneyBrowse.inertiaVelocityAt(-1.5f, 0.1f) < 0f)
        assertEquals(-1.5f, JourneyBrowse.inertiaVelocityAt(-1.5f, 0f), 0f)
        assertEquals(0f, JourneyBrowse.inertiaVelocityAt(0f, 0.5f), 0f)
    }

    @Test fun inertiaStepIsHardClampedAtConfirmed() {
        // A fast forward fling from just behind the edge lands exactly on it.
        val confirmed = 4f
        var position = 3.9f
        var velocity = 3f
        while (abs(velocity) > JourneyBrowse.INERTIA_STOP_SPEED && position < confirmed) {
            position = JourneyBrowse.clampBrowse(position + velocity * 0.016f, confirmed, max)
            if (position >= confirmed) break
            velocity = JourneyBrowse.inertiaVelocityAt(velocity, 0.016f)
        }
        assertEquals(confirmed, position, 0f)
        // Backward inertia is hard-clamped at the route start.
        assertEquals(0f, JourneyBrowse.clampBrowse(0.05f - 3f * 0.5f, confirmed, max), 0f)
    }

    @Test fun backwardCorrectionSqueezesBrowseToTheNewBoundary() {
        // Confirmed drops below the browsed position: browse follows the edge down.
        assertEquals(3f, JourneyBrowse.squeezeBrowse(5f, 3f, max)!!, 0f)
        // Confirmed moves forward: the browsed position is NOT yanked along.
        assertEquals(2f, JourneyBrowse.squeezeBrowse(2f, 6f, max)!!, 0f)
        // Not browsing stays not browsing.
        assertNull(JourneyBrowse.squeezeBrowse(null, 3f, max))
    }

    @Test fun returnToCurrentIsIdempotent() {
        val confirmed = 6.5f
        // Not browsing: the render target is exactly the confirmed position.
        assertEquals(confirmed, JourneyBrowse.effectiveTarget(null, confirmed, max), 0f)
        // Clearing browse twice changes nothing.
        val afterReturn = JourneyBrowse.squeezeBrowse(null, confirmed, max)
        assertNull(afterReturn)
        assertEquals(confirmed, JourneyBrowse.effectiveTarget(afterReturn, confirmed, max), 0f)
        // A browse squeezed onto the boundary renders exactly the confirmed position.
        val squeezed = JourneyBrowse.squeezeBrowse(8f, confirmed, max)
        assertEquals(confirmed, JourneyBrowse.effectiveTarget(squeezed, confirmed, max), 0f)
    }

    @Test fun browsingLabelThreshold() {
        val confirmed = 5f
        assertTrue(JourneyBrowse.isBrowsing(confirmed - 2 * JourneyBrowse.BROWSE_EPSILON, confirmed))
        assertTrue(JourneyBrowse.isBrowsing(0f, confirmed))
        assertFalse(JourneyBrowse.isBrowsing(confirmed - JourneyBrowse.BROWSE_EPSILON / 2f, confirmed))
        assertFalse(JourneyBrowse.isBrowsing(confirmed, confirmed))
        assertFalse(JourneyBrowse.isBrowsing(null, confirmed))
    }

    @Test fun browsingNeverMovesTheConfirmedPosition() {
        // All browse math is pure: feeding any gesture/correction sequence
        // through it leaves the confirmed value untouched and the render target
        // at or behind it — steps, events and stamps derive from the confirmed
        // position, which browse functions only read.
        val confirmed = 4.25f
        var browse: Float? = null
        browse = JourneyBrowse.clampBrowse(confirmed, confirmed, max) // drag start at the camera
        repeat(50) { // drag back, drag forward past the edge, inertia, correction
            val delta = JourneyBrowse.dragDeltaPositions(if (it % 2 == 0) 480f else -900f, 1080f)
            browse = JourneyBrowse.clampBrowse(browse!! + delta, confirmed, max)
            browse = JourneyBrowse.squeezeBrowse(browse, confirmed, max)
            assertEquals(confirmed, 4.25f, 0f)
            assertTrue(JourneyBrowse.effectiveTarget(browse, confirmed, max) <= confirmed)
        }
        assertEquals(confirmed, JourneyBrowse.effectiveTarget(null, confirmed, max), 0f)
    }
}
