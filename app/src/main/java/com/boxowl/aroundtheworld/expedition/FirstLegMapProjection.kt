package com.boxowl.aroundtheworld.expedition

internal enum class MapStopState { CURRENT, PASSED, AHEAD, REMEMBERED }

internal data class MapStop(
    val stop: RouteStop,
    val state: MapStopState,
    val diaryAvailable: Boolean,
)

/** A read-only view of the saved route. Past unlocks survive a downward step correction. */
internal data class FirstLegMapProjection(
    val stops: List<MapStop>,
    val knownSteps: Long?,
    val goal: Long,
    val currentIndex: Int,
    val nextIndex: Int?,
    val fractionToNext: Float,
) {
    companion object {
        fun from(expedition: Expedition): FirstLegMapProjection {
            val route = expedition.stops
            val known = expedition.totalSteps.takeIf { expedition.dailySteps.isNotEmpty() }
            val steps = (known ?: 0L).coerceAtMost(expedition.firstLegGoal)
            val current = route.indexOfLast { it.threshold <= steps }.coerceAtLeast(0)
            val next = (current + 1).takeIf { it < route.size }
            val fraction = next?.let {
                ((steps - route[current].threshold).toFloat() /
                    (route[it].threshold - route[current].threshold)).coerceIn(0f, 1f)
            } ?: 0f
            val stops = route.mapIndexed { index, stop ->
                val diaryAvailable = stop.id in expedition.unlocked
                val state = when {
                    index == current -> MapStopState.CURRENT
                    index < current -> MapStopState.PASSED
                    diaryAvailable -> MapStopState.REMEMBERED
                    else -> MapStopState.AHEAD
                }
                MapStop(stop, state, diaryAvailable)
            }
            return FirstLegMapProjection(stops, known, expedition.firstLegGoal, current, next, fraction)
        }
    }
}
