package com.boxowl.aroundtheworld.expedition

import com.boxowl.aroundtheworld.health.Availability
import com.boxowl.aroundtheworld.health.ReadWindow
import com.boxowl.aroundtheworld.health.StepsGateway
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

interface ExpeditionLedger {
    suspend fun reconcile(replacements: Map<LocalDate, Long>, readAt: Instant): Expedition
}

sealed interface SyncResult {
    data class Updated(val expedition: Expedition, val gaps: Int, val scanned: Int, val limited: Boolean) : SyncResult
    data object PermissionRequired : SyncResult
    data object Unavailable : SyncResult
    data object UpdateRequired : SyncResult
    data object ReadError : SyncResult
}

/** Foreground reconciliation. A null aggregate or failed historical window never becomes zero. */
class ExpeditionSync(private val gateway: StepsGateway, private val ledger: ExpeditionLedger) {
    suspend fun refresh(expedition: Expedition, now: Instant): SyncResult {
        when (gateway.availability()) {
            Availability.UNAVAILABLE -> return SyncResult.Unavailable
            Availability.UPDATE_REQUIRED -> return SyncResult.UpdateRequired
            Availability.AVAILABLE -> Unit
        }
        if (now < expedition.startedAt) return SyncResult.ReadError
        try {
            if (!gateway.hasPermission()) return SyncResult.PermissionRequired
            val lastDate = now.atZone(expedition.zone).toLocalDate()
            val firstDate = expedition.startDate
            // F02 covers the 80-day first journey, plus corrections shortly afterward. Bound long-running
            // free journeys to keep foreground reads finite; never clear dates outside the scanned range.
            val scanStart = maxOf(firstDate, lastDate.minusDays(119))
            val limited = scanStart > firstDate
            val replacements = mutableMapOf<LocalDate, Long>()
            var gaps = 0
            var scanned = 0
            var errors = 0
            if (now > expedition.startedAt) {
                var date = scanStart
                while (date <= lastDate) {
                    val start = maxOf(date.atStartOfDay(expedition.zone).toInstant(), expedition.startedAt)
                    val end = minOf(date.plusDays(1).atStartOfDay(expedition.zone).toInstant(), now)
                    if (end > start) {
                        scanned++
                        try {
                            val count = gateway.aggregate(ReadWindow(start, end)).count
                            if (count == null) gaps++ else replacements[date] = count
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: SecurityException) {
                            // History access may be denied while READ_STEPS remains granted.
                            if (!gateway.hasPermission()) return SyncResult.PermissionRequired
                            gaps++; errors++
                        }
                        catch (_: Exception) { gaps++; errors++ }
                    }
                    date = date.plusDays(1)
                }
            }
            // An access revocation during a scan must not persist a misleading partial result.
            if (!gateway.hasPermission()) return SyncResult.PermissionRequired
            if (errors > 0 && errors == scanned) return SyncResult.ReadError
            return SyncResult.Updated(ledger.reconcile(replacements, now), gaps, scanned, limited)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { return SyncResult.PermissionRequired }
        catch (_: Exception) { return SyncResult.ReadError }
    }
}
