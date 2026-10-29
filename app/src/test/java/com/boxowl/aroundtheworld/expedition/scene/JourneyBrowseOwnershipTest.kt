package com.boxowl.aroundtheworld.expedition.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.boxowl.aroundtheworld.expedition.scene.JourneyBrowse.CameraOwner

/**
 * V2.1 camera-ownership contract tests (review fix): exactly one writer of the
 * camera position at a time. A drag captures the camera atomically and keeps
 * it through the fling; the chase plan must check ownership before applying
 * every frame, so a stale plan frame can never roll the camera back; after the
 * fling ends the plan resumes from the currently displayed value.
 */
class JourneyBrowseOwnershipTest {

    private val max = 9f

    /** Minimal harness mirroring the loop guard: a plan frame applies only under CHASE. */
    private class Camera(var position: Float) {
        var owner: CameraOwner = CameraOwner.CHASE
        var appliedFrames = 0

        /** Returns true when the frame was written (ownership held), false when blocked. */
        fun applyPlanFrame(from: Float, target: Float, elapsedMs: Long): Boolean {
            if (!JourneyBrowse.chaseMayWrite(owner)) return false
            position = ChaseAnimation.chaseValueAt(from, target, elapsedMs)
            appliedFrames++
            return true
        }
    }

    @Test fun chaseWritesOnlyUnderChaseOwnership() {
        assertTrue(JourneyBrowse.chaseMayWrite(CameraOwner.CHASE))
        assertFalse(JourneyBrowse.chaseMayWrite(CameraOwner.DRAG))
        assertFalse(JourneyBrowse.chaseMayWrite(CameraOwner.INERTIA))
    }

    @Test fun dragStartDuringReturnToCurrentStopsStalePlanFrames() {
        // Browsed at 5.3, user taps "К текущей позиции": browse cleared, plan 5.3 → 6.0.
        val camera = Camera(position = 5.3f)
        val confirmed = 6f
        assertTrue(camera.applyPlanFrame(5.3f, confirmed, 200L)) // mid-flight, moving forward
        assertTrue(camera.position > 5.3f)
        // Mid-return the user grabs the scene: ownership is captured atomically.
        camera.owner = JourneyBrowse.ownerOnDragStart()
        val capturedAt = camera.position
        // From here on every stale plan frame is blocked — no rollback toward the old plan.
        repeat(20) { assertFalse(camera.applyPlanFrame(5.3f, confirmed, 400L + it * 50L)) }
        assertEquals(capturedAt, camera.position, 0f)
        assertEquals(0 + 1, camera.appliedFrames) // only the pre-capture frame was ever applied
    }

    @Test fun dragStartDuringConfirmedChangeCapturesImmediately() {
        // A sync moves the confirmed position 6.0 → 7.5; the plan starts chasing.
        val camera = Camera(position = 6.0f)
        assertTrue(camera.applyPlanFrame(6.0f, 7.5f, 300L))
        assertTrue(camera.position > 6.0f)
        // The user starts a drag mid-chase: DRAG from any state, plan blocked at once.
        camera.owner = JourneyBrowse.ownerOnDragStart()
        assertEquals(CameraOwner.DRAG, camera.owner)
        val capturedAt = camera.position
        repeat(10) { assertFalse(camera.applyPlanFrame(6.0f, 7.5f, 500L + it * 80L)) }
        assertEquals(capturedAt, camera.position, 0f)
        // The browse position entered from the displayed value stays within bounds.
        val browse = JourneyBrowse.clampBrowse(capturedAt, 7.5f, max)
        assertEquals(capturedAt, browse, 0f)
    }

    @Test fun inertiaKeepsOwnershipUntilFullyStopped() {
        var owner = JourneyBrowse.ownerOnDragStart()
        owner = JourneyBrowse.ownerOnDragEnd(startsInertia = true)
        assertEquals(CameraOwner.INERTIA, owner)
        // While the fling decays the plan is blocked on every simulated frame.
        val camera = Camera(position = 5.0f)
        camera.owner = owner
        var velocity = 0.8f
        while (kotlin.math.abs(velocity) > JourneyBrowse.INERTIA_STOP_SPEED) {
            assertEquals(CameraOwner.INERTIA, camera.owner)
            assertFalse(camera.applyPlanFrame(5.0f, 6f, 100L))
            velocity = JourneyBrowse.inertiaVelocityAt(velocity, 0.016f)
        }
        // Only after the fling really ends does the plan get the camera back.
        camera.owner = JourneyBrowse.ownerOnInertiaEnd(camera.owner)
        assertEquals(CameraOwner.CHASE, camera.owner)
        assertTrue(camera.applyPlanFrame(5.0f, 6f, 100L))
    }

    @Test fun cancelledFlingDoesNotDowngradeANewDrag() {
        // Fling running (INERTIA); a new drag captures the camera; the cancelled
        // fling's delayed release must not hand the camera back under the finger.
        var owner = JourneyBrowse.ownerOnDragEnd(startsInertia = true)
        assertEquals(CameraOwner.INERTIA, owner)
        owner = JourneyBrowse.ownerOnDragStart() // new gesture while the fling flies
        assertEquals(CameraOwner.DRAG, owner)
        owner = JourneyBrowse.ownerOnInertiaEnd(owner) // cancelled fling's finally arrives late
        assertEquals(CameraOwner.DRAG, owner)
    }

    @Test fun afterReleasePlanResumesFromTheDisplayedValue() {
        // Fling ends parked behind the confirmed position; the return plan must
        // start from the displayed value (no jump back to the pre-gesture point)
        // and still finish exactly at the confirmed position within the cap.
        val confirmed = 6f
        val displayed = 5.42f // wherever the finger/fling left the camera
        val duration = ChaseAnimation.planDurationMs(kotlin.math.abs(confirmed - displayed))
        assertTrue(duration <= ChaseAnimation.MAX_DURATION_MS)
        assertEquals(displayed, ChaseAnimation.chaseValueAt(displayed, confirmed, 0L), 0f)
        var previous = -1f
        var elapsed = 0L
        while (elapsed <= duration) {
            val value = ChaseAnimation.chaseValueAt(displayed, confirmed, elapsed)
            assertTrue("not monotone at $elapsed ms", value >= previous)
            assertTrue(value in displayed..confirmed)
            previous = value
            elapsed += 37L
        }
        assertEquals(confirmed, ChaseAnimation.chaseValueAt(displayed, confirmed, duration), 0f)
    }

    @Test fun gestureWithoutFlingHandsOwnershipStraightBack() {
        var owner = JourneyBrowse.ownerOnDragStart()
        owner = JourneyBrowse.ownerOnDragEnd(startsInertia = false) // slow drag / reduced-motion
        assertEquals(CameraOwner.CHASE, owner)
        assertEquals(CameraOwner.CHASE, JourneyBrowse.ownerOnGestureCancel())
    }
}
