package com.boxowl.aroundtheworld.expedition.globe

import kotlin.math.roundToLong

/** Visual-only position: null follows the confirmed journey, a value browses its past. */
object V2BrowsePosition {
    fun effective(requested: Long?, confirmed: Long): Long {
        require(confirmed >= 0L)
        return (requested ?: confirmed).coerceIn(0L, confirmed)
    }

    fun squeeze(requested: Long?, confirmed: Long): Long? =
        requested?.let { effective(it, confirmed) }?.takeIf { it < confirmed }

    fun isBrowsing(requested: Long?, confirmed: Long): Boolean =
        requested != null && effective(requested, confirmed) < confirmed

    fun fromFraction(fraction: Float, confirmed: Long): Long {
        require(confirmed >= 0L)
        return (fraction.coerceIn(0f, 1f).toDouble() * confirmed).roundToLong().coerceIn(0L, confirmed)
    }
}
