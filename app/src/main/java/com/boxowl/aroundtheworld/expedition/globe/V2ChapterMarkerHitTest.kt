package com.boxowl.aroundtheworld.expedition.globe

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot

/** Visible chapter-finish markers in the same orthographic frame as the globe Canvas. */
object V2ChapterMarkerHitTest {
    data class Target(val occurrenceId: String, val center: Offset, val reachedBookKm: Double)

    fun targets(center: Offset, radius: Float, centerLat: Double, centerLon: Double): List<Target> {
        require(radius > 0f)
        val frame = GlobeFrame(centerLat, centerLon)
        return RoutePosition.chapterMarks.mapNotNull { mark ->
            if (!frame.isVisible(mark.pos)) return@mapNotNull null
            val card = GlobeStopCardsV1.cards.single {
                it.nodeId == mark.nodeId && it.occurrenceId != "london#1"
            }
            val point = frame.project(mark.pos)
            Target(
                card.occurrenceId,
                Offset(center.x + (point.x * radius).toFloat(), center.y - (point.y * radius).toFloat()),
                mark.bookKm,
            )
        }
    }

    fun hit(targets: List<Target>, tap: Offset, maxDistancePx: Float): String? {
        require(maxDistancePx > 0f)
        return targets.minByOrNull { hypot((tap.x - it.center.x).toDouble(), (tap.y - it.center.y).toDouble()) }
            ?.takeIf { hypot((tap.x - it.center.x).toDouble(), (tap.y - it.center.y).toDouble()) <= maxDistancePx }
            ?.occurrenceId
    }
}
