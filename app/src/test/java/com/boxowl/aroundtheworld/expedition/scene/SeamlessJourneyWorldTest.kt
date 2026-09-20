package com.boxowl.aroundtheworld.expedition.scene

import com.boxowl.aroundtheworld.expedition.Expedition
import com.boxowl.aroundtheworld.expedition.FirstLegMapProjection
import com.boxowl.aroundtheworld.expedition.JOURNEY_SCENES
import com.boxowl.aroundtheworld.expedition.JourneyMode
import com.boxowl.aroundtheworld.expedition.PACES
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P07 contract tests for the seamless world: continuous coordinates at every
 * internal stop boundary, one clamped camera, hero pinned near 42 % of the
 * viewport, monotone progress at all three paces, backward correction and
 * unknown progress.
 */
class SeamlessJourneyWorldTest {
    private val layout = FIRST_LEG_LAYOUT
    private val viewWidths = listOf(0.7f, 0.9f, 1.1f, 1.3f)

    private fun expeditionWith(steps: Long, pace: Int = 7_000): Expedition {
        val start = Instant.parse("2026-09-01T10:00:00Z")
        val zone = ZoneId.of("Europe/Moscow")
        return Expedition(
            startedAt = start,
            zone = zone,
            mode = JourneyMode.FREE,
            paceStepsPerDay = pace,
            dailySteps = mapOf(LocalDate.of(2026, 9, 1) to steps),
            lastReadAt = start.plusSeconds(3_600),
        )
    }

    private fun positionOf(steps: Long, pace: Int = 7_000): Float =
        routePositionOf(FirstLegMapProjection.from(expeditionWith(steps, pace)))

    @Test fun anchorsAreUniformAndStrictlyIncreasing() {
        for (i in 1 until layout.stopCount) {
            assertEquals(SEGMENT_LENGTH, layout.anchorX(i) - layout.anchorX(i - 1), 1e-6f)
        }
        assertTrue(layout.anchorX(0) > layout.start)
        assertTrue(layout.anchorX(layout.stopCount - 1) < layout.end)
    }

    @Test fun coordinatesConvergeAtEveryInternalBoundary() {
        val eps = 1e-5f
        for (i in 1 until layout.stopCount - 1) {
            val boundary = layout.anchorX(i)
            for (viewW in viewWidths) {
                val camera = JourneyCamera.cameraXFor(boundary, layout, viewW)
                for ((name, f) in listOf<Pair<String, (Float) -> Float>>(
                    "worldXAt" to { p -> layout.worldXAt(p) },
                    "cameraXFor" to { p -> JourneyCamera.cameraXFor(layout.worldXAt(p), layout, viewW) },
                    "groundYAt" to { p -> TerrainProfile.groundYAt(layout, layout.worldXAt(p)) },
                    "groundSlopeAt" to { p -> TerrainProfile.groundSlopeAt(layout, layout.worldXAt(p)) },
                )) {
                    val left = f(i - eps)
                    val at = f(i.toFloat())
                    val right = f(i + eps)
                    assertTrue("$name diverges at stop $i (viewW=$viewW): $left / $at / $right",
                        kotlin.math.abs(left - at) < 1e-4f && kotlin.math.abs(right - at) < 1e-4f)
                }
                // The boundary anchor itself is seen identically from both sides.
                assertEquals(boundary, layout.worldXAt(i.toFloat()), 1e-6f)
                assertTrue(camera in JourneyCamera.minCameraX(layout, viewW)..
                    JourneyCamera.maxCameraX(layout, viewW))
            }
        }
    }

    @Test fun positionIsMonotoneInStepsAtAllPaces() {
        for (pace in PACES) {
            val goal = pace * 7L
            var previous = -1f
            var steps = 0L
            while (steps <= goal) {
                val position = positionOf(steps, pace)
                assertTrue("position decreases at $steps steps (pace $pace)", position >= previous)
                previous = position
                steps += pace / 20L
            }
            assertEquals((layout.stopCount - 1).toFloat(), positionOf(goal, pace), 1e-4f)
        }
    }

    @Test fun thresholdCrossingInStepsIsContinuous() {
        val expedition = expeditionWith(0)
        for (stop in expedition.stops.drop(1).dropLast(1)) {
            val t = stop.threshold
            val before = layout.worldXAt(positionOf(t - 1))
            val at = layout.worldXAt(positionOf(t))
            val after = layout.worldXAt(positionOf(t + 1))
            assertTrue(before < at && at < after)
            assertTrue("jump at threshold $t", at - before < 0.02f && after - at < 0.02f)
        }
    }

    @Test fun heroStaysInsideScreenBandAcrossTheWholeRoute() {
        for (viewW in viewWidths) {
            var position = 0f
            while (position <= layout.stopCount - 1f) {
                val heroX = layout.worldXAt(position)
                val camera = JourneyCamera.cameraXFor(heroX, layout, viewW)
                val fraction = JourneyCamera.heroScreenFraction(heroX, camera, viewW)
                assertTrue("hero at $fraction (position $position, viewW $viewW)",
                    fraction in 0.35f..0.60f)
                position += 0.01f
            }
        }
    }

    @Test fun cameraClampsAtWorldEdges() {
        val wide = 1.5f // wider than the decorated margins: forces both clamps
        val startCamera = JourneyCamera.cameraXFor(layout.worldXAt(0f), layout, wide)
        assertEquals(JourneyCamera.minCameraX(layout, wide), startCamera, 1e-6f)
        assertTrue(startCamera > layout.worldXAt(0f))
        val endCamera = JourneyCamera.cameraXFor(layout.worldXAt(9f), layout, wide)
        assertEquals(JourneyCamera.maxCameraX(layout, wide), endCamera, 1e-6f)
        assertTrue(endCamera < layout.worldXAt(9f))
        // Edges of the world never enter the screen in the NEAR layer.
        for (viewW in viewWidths + wide) {
            var position = 0f
            while (position <= layout.stopCount - 1f) {
                val camera = JourneyCamera.cameraXFor(layout.worldXAt(position), layout, viewW)
                assertTrue(JourneyCamera.screenFraction(layout.start, SceneLayer.NEAR, camera, viewW) <= 1e-4f)
                assertTrue(JourneyCamera.screenFraction(layout.end, SceneLayer.NEAR, camera, viewW) >= 1f - 1e-4f)
                position += 0.05f
            }
        }
    }

    @Test fun backwardCorrectionMovesCameraBackOnTheSameWorld() {
        val forward = JourneyCamera.cameraXFor(layout.worldXAt(5.5f), layout, 0.9f)
        val back = JourneyCamera.cameraXFor(layout.worldXAt(2.3f), layout, 0.9f)
        assertTrue(back < forward)
        assertTrue(layout.worldXAt(2.3f) < layout.worldXAt(5.5f))
        // Corrected steps map to the same continuous function, no world rebuild.
        assertEquals(positionOf(20_000), positionOf(20_000), 0f)
        assertTrue(positionOf(12_000) < positionOf(30_000))
    }

    @Test fun unknownProgressPinsTheViewToTheStart() {
        val expedition = Expedition(
            startedAt = Instant.parse("2026-09-01T10:00:00Z"),
            zone = ZoneId.of("Europe/Moscow"),
            mode = JourneyMode.FREE,
        )
        assertEquals(0f, routePositionOf(FirstLegMapProjection.from(expedition)), 0f)
    }

    @Test fun zeroStepsIsAConfirmedStartNotAnUnknown() {
        assertEquals(0f, positionOf(0), 0f)
        assertEquals(layout.anchorX(0), layout.worldXAt(positionOf(0)), 1e-6f)
    }

    @Test fun repeatedPositionIsIdempotent() {
        val position = positionOf(21_500)
        assertEquals(layout.worldXAt(position), layout.worldXAt(position), 0f)
        assertEquals(
            JourneyCamera.cameraXFor(layout.worldXAt(position), layout, 0.8f),
            JourneyCamera.cameraXFor(layout.worldXAt(position), layout, 0.8f),
            0f,
        )
        assertEquals(
            TerrainProfile.groundYAt(layout, layout.worldXAt(position)),
            TerrainProfile.groundYAt(layout, layout.worldXAt(position)),
            0f,
        )
    }

    @Test fun terrainJoinsDeckAndBankWithoutSteps() {
        // Brindisi pier → Mediterranean deck plateau → Suez bank.
        val a8 = layout.anchorX(8)
        val a9 = layout.anchorX(9)
        assertEquals(0.83f, TerrainProfile.groundYAt(layout, a8), 1e-4f)
        assertEquals(0.78f, TerrainProfile.groundYAt(layout, a9), 1e-4f)
        var wx = a8
        var previous = TerrainProfile.groundYAt(layout, wx)
        while (wx <= a9) {
            val y = TerrainProfile.groundYAt(layout, wx)
            assertTrue("deck dips below bank level at $wx", y >= 0.77f)
            assertTrue("deck jump at $wx", kotlin.math.abs(y - previous) < 0.01f)
            previous = y
            wx += 0.01f
        }
    }

    @Test fun sceneCountMatchesTheRoute() {
        assertEquals(JOURNEY_SCENES.size, layout.stopCount)
        assertEquals(10, layout.stopCount)
    }
}
