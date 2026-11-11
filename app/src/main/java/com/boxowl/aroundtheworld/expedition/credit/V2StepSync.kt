package com.boxowl.aroundtheworld.expedition.credit

import com.boxowl.aroundtheworld.health.Availability
import com.boxowl.aroundtheworld.health.ReadWindow
import com.boxowl.aroundtheworld.health.StepsGateway
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException

sealed interface V2SyncResult {
    data class Updated(val snapshot: V2CreditSnapshot, val gaps: Int, val scanned: Int, val limited: Boolean) : V2SyncResult
    data object PermissionRequired : V2SyncResult
    data object Unavailable : V2SyncResult
    data object UpdateRequired : V2SyncResult
    data object ReadError : V2SyncResult
}

/** Foreground v2 read. The repository owns conversion to metres and the atomic write. */
class V2StepSync(
    private val gateway: StepsGateway,
    private val reconcile: suspend (Long, Map<LocalDate, Long>, Instant) -> V2CreditSnapshot,
) {
    suspend fun refresh(current: V2CreditSnapshot, now: Instant): V2SyncResult {
        return try {
            val expedition = current.expedition
            val startedAt = Instant.parse(expedition.startedAt)
            val lastReadAt = expedition.lastReadAt?.let(Instant::parse)
            if (now < startedAt || (lastReadAt != null && now < lastReadAt)) return V2SyncResult.ReadError
            when (gateway.availability()) {
                Availability.UNAVAILABLE -> return V2SyncResult.Unavailable
                Availability.UPDATE_REQUIRED -> return V2SyncResult.UpdateRequired
                Availability.AVAILABLE -> Unit
            }
            if (!gateway.hasPermission()) return V2SyncResult.PermissionRequired

            val zone = ZoneId.of(expedition.zone)
            val firstDate = startedAt.atZone(zone).toLocalDate()
            val lastDate = now.atZone(zone).toLocalDate()
            val scanStart = maxOf(firstDate, lastDate.minusDays(119))
            val limited = scanStart > firstDate
            val replacements = mutableMapOf<LocalDate, Long>()
            var gaps = 0
            var scanned = 0
            var errors = 0
            if (now > startedAt) {
                var date = scanStart
                while (date <= lastDate) {
                    val start = maxOf(date.atStartOfDay(zone).toInstant(), startedAt)
                    val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant(), now)
                    if (end > start) {
                        scanned++
                        try {
                            val count = gateway.aggregate(ReadWindow(start, end)).count
                            if (count == null) gaps++ else replacements[date] = count
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: SecurityException) {
                            if (!gateway.hasPermission()) return V2SyncResult.PermissionRequired
                            gaps++
                            errors++
                        } catch (_: Exception) {
                            gaps++
                            errors++
                        }
                    }
                    date = date.plusDays(1)
                }
            }
            if (!gateway.hasPermission()) return V2SyncResult.PermissionRequired
            if (errors > 0 && errors == scanned) return V2SyncResult.ReadError
            V2SyncResult.Updated(reconcile(expedition.id, replacements, now), gaps, scanned, limited)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            V2SyncResult.PermissionRequired
        } catch (_: Exception) {
            V2SyncResult.ReadError
        }
    }
}
