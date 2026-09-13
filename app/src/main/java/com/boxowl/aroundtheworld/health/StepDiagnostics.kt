package com.boxowl.aroundtheworld.health

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

enum class Availability { AVAILABLE, UPDATE_REQUIRED, UNAVAILABLE }
data class StepTotal(val count: Long?, val origins: Set<String> = emptySet())
data class ReadWindow(val start: Instant, val end: Instant)
data class DiagnosticSnapshot(
    val today: StepTotal,
    val week: StepTotal,
    val todayWindow: ReadWindow,
    val weekWindow: ReadWindow,
    val zone: ZoneId,
)
sealed interface DiagnosticState {
    data object Loading : DiagnosticState
    data object Unavailable : DiagnosticState
    data object UpdateRequired : DiagnosticState
    data object PermissionRequired : DiagnosticState
    data class Ready(val snapshot: DiagnosticSnapshot) : DiagnosticState
    data object ReadError : DiagnosticState
}
interface StepsGateway {
    fun availability(): Availability
    suspend fun hasPermission(): Boolean
    suspend fun aggregate(window: ReadWindow): StepTotal
}

/** Re-read snapshots, never accumulate previous reads. No expedition accounting yet. */
class StepDiagnostics(private val gateway: StepsGateway) {
    suspend fun read(now: Instant, zone: ZoneId): DiagnosticState = try {
        when (gateway.availability()) {
            Availability.UNAVAILABLE -> DiagnosticState.Unavailable
            Availability.UPDATE_REQUIRED -> DiagnosticState.UpdateRequired
            Availability.AVAILABLE -> {
                if (!gateway.hasPermission()) {
                    DiagnosticState.PermissionRequired
                } else {
                    val date = now.atZone(zone).toLocalDate()
                    val today = ReadWindow(date.atStartOfDay(zone).toInstant(), now)
                    val week = ReadWindow(date.minusDays(6).atStartOfDay(zone).toInstant(), now)
                    val todayTotal = if (today.start == today.end) StepTotal(null) else gateway.aggregate(today)
                    val weekTotal = gateway.aggregate(week)
                    // Permission may have been revoked while reading.
                    if (!gateway.hasPermission()) DiagnosticState.PermissionRequired
                    else DiagnosticState.Ready(DiagnosticSnapshot(todayTotal, weekTotal, today, week, zone))
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: SecurityException) {
        DiagnosticState.PermissionRequired
    } catch (_: Exception) {
        DiagnosticState.ReadError
    }
}
