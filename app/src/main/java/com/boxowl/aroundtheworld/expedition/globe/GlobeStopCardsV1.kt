package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.NodeRole.PORT
import com.boxowl.aroundtheworld.expedition.route.NodeRole.STOP
import com.boxowl.aroundtheworld.expedition.route.ROUTE_GEOMETRY_VERSION
import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteLeg
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.expedition.route.Transport

/**
 * One stop card of the globe (V2.4c, endpoint-only slice).
 *
 * A card is one route occurrence of a STOP/PORT node that is an endpoint
 * (`fromId`/`toId`) of at least one [RouteLeg]. `london` closes the loop, so
 * it yields two cards (`london#1` start, `london#2` finish); every other node
 * yields one. Mid-leg STOP/CALL waypoints (`chicago`, `nagasaki`) and
 * WAYPOINT-role leg endpoints (`hooghly-mouth`, `shanghai-mouth`) have no
 * card in this slice.
 *
 * [arrivalChapter] and [arrivalTransport] describe the arriving (terminating)
 * leg, not the node itself: for `london#2` they come from leg-24 (chapter 34),
 * not from the node (chapter 3). The start occurrence `london#1` has no
 * arriving leg: [arrivalTransport] is `null` (the start transport is not
 * invented) and [arrivalChapter] is the chapter of the first outgoing leg —
 * the convention «старт принадлежит главе выезда».
 *
 * [thresholdBookKm] is measured book distance at this occurrence, taken only
 * from [RouteMeasure.cumulativeKm]: 0 for the start, the cumulative after the
 * arriving leg for the rest, [RouteMeasure.totalKm] for the finish.
 */
data class GlobeStopCard(
    /** `<nodeId>#<n>` — n is the 1-based occurrence number of this node in route order. */
    val occurrenceId: String,
    val nodeId: String,
    val nameRu: String,
    /** Chapter of the arriving leg; for the start occurrence, of the first outgoing leg. */
    val arrivalChapter: Int,
    /** Transport of the arriving leg; `null` for the start occurrence. */
    val arrivalTransport: Transport?,
    /** Book distance threshold of this occurrence, km, from [RouteMeasure]. */
    val thresholdBookKm: Double,
)

/**
 * The deterministic, derived list of stop cards for route dataset V1.
 *
 * Built only from [RouteDatasetV1] + [RouteMeasure]: no node or threshold
 * literals. The walk is the start node of the first leg followed by the
 * `toId` of every leg in route order; every walked node is an endpoint of at
 * least one leg by construction, and the role filter keeps exactly
 * STOP/PORT. Rebuild on [ROUTE_GEOMETRY_VERSION] bump — the cards carry no
 * persisted state.
 */
object GlobeStopCardsV1 {

    /** All stop cards in route order: `london#1`, …, `london#2` (25 for dataset V1). */
    val cards: List<GlobeStopCard> = build()

    /** Pure derived state: `true` when the expedition reached this card's threshold. */
    fun reached(card: GlobeStopCard, totalBookMeters: Double): Boolean =
        totalBookMeters >= card.thresholdBookKm * 1000.0

    private data class Occurrence(
        val nodeId: String,
        val thresholdBookKm: Double,
        val arrivalChapter: Int,
        val arrivalTransport: Transport?,
    )

    private fun build(): List<GlobeStopCard> {
        val legs = RouteDatasetV1.legs
        val cumulativeKm = RouteMeasure.cumulativeKm()

        val occurrences = ArrayList<Occurrence>(legs.size + 1)
        val firstLeg = legs.first()
        occurrences += Occurrence(
            nodeId = firstLeg.fromId,
            thresholdBookKm = 0.0,
            arrivalChapter = firstLeg.chapter,
            arrivalTransport = null,
        )
        legs.forEachIndexed { index, leg ->
            occurrences += Occurrence(
                nodeId = leg.toId,
                thresholdBookKm = cumulativeKm[index],
                arrivalChapter = leg.chapter,
                arrivalTransport = leg.transport,
            )
        }

        val seen = HashMap<String, Int>()
        return occurrences.mapNotNull { occurrence ->
            val node = RouteDatasetV1.node(occurrence.nodeId)
            if (node.role != STOP && node.role != PORT) return@mapNotNull null
            val n = (seen[node.id] ?: 0) + 1
            seen[node.id] = n
            GlobeStopCard(
                occurrenceId = "${node.id}#$n",
                nodeId = node.id,
                nameRu = node.nameRu,
                arrivalChapter = occurrence.arrivalChapter,
                arrivalTransport = occurrence.arrivalTransport,
                thresholdBookKm = occurrence.thresholdBookKm,
            )
        }
    }
}
