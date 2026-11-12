package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.RouteDatasetV1
import com.boxowl.aroundtheworld.expedition.route.RouteMeasure
import com.boxowl.aroundtheworld.expedition.route.Transport

/** Text-only route leg at a confirmed book distance; no persisted state. */
object V2CurrentTransport {
    data class Status(
        val from: String,
        val to: String,
        val transport: String,
        val chapter: Int,
        val completed: Boolean,
    )

    fun at(bookKm: Double): Status {
        require(bookKm.isFinite() && bookKm >= 0.0)
        val legs = RouteDatasetV1.legs
        val cumulative = RouteMeasure.cumulativeKm()
        val index = cumulative.indexOfFirst { bookKm <= it }.takeIf { it >= 0 } ?: legs.lastIndex
        val leg = legs[index]
        return Status(
            from = RouteDatasetV1.node(leg.fromId).nameRu,
            to = RouteDatasetV1.node(leg.toId).nameRu,
            transport = transportName(leg.transport),
            chapter = leg.chapter,
            completed = bookKm >= cumulative.last(),
        )
    }

    private fun transportName(transport: Transport): String = when (transport) {
        Transport.TRAIN -> "Поезд"
        Transport.SHIP -> "Судно"
        Transport.FERRY -> "Паром"
        Transport.ELEPHANT -> "Слон"
        Transport.SLEDGE -> "Сани"
        Transport.WATERWAY -> "Водный путь"
    }
}
