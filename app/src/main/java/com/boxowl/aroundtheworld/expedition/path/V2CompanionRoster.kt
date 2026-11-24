package com.boxowl.aroundtheworld.expedition.path

/** Companions at the confirmed route position, using the approved Path rows. */
object V2CompanionRoster {
    fun at(bookKm: Double): Set<PathHero> {
        require(bookKm.isFinite() && bookKm >= 0.0)
        val aoudaJoinsKm = requireNotNull(PathRouteV1.startThresholdKm(11))
        return if (bookKm >= aoudaJoinsKm) PathRouteV1.row(11).heroes else PathRouteV1.row(2).heroes
    }

    fun label(bookKm: Double): String = at(bookKm).joinToString(", ") { hero ->
        when (hero) {
            PathHero.FOGG -> "Фогг"
            PathHero.PASSEPARTOUT -> "Паспарту"
            PathHero.AOUDA -> "Ауда"
        }
    }
}
