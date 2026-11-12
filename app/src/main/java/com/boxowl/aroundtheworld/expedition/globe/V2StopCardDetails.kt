package com.boxowl.aroundtheworld.expedition.globe

import com.boxowl.aroundtheworld.expedition.route.Transport

/** Read-only presentation of a measured endpoint card for one confirmed v2 snapshot. */
object V2StopCardDetails {
    data class Detail(
        val occurrenceId: String,
        val name: String,
        val chapter: Int,
        val arrival: String,
        val thresholdBookKm: Double,
        val reached: Boolean,
    )

    fun of(card: GlobeStopCard, confirmedBookMeters: Long): Detail {
        require(confirmedBookMeters >= 0L)
        return Detail(
            occurrenceId = card.occurrenceId,
            name = card.nameRu,
            chapter = card.arrivalChapter,
            arrival = card.arrivalTransport?.let(::transportName) ?: "Старт маршрута",
            thresholdBookKm = card.thresholdBookKm,
            reached = GlobeStopCardsV1.reached(card, confirmedBookMeters.toDouble()),
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
