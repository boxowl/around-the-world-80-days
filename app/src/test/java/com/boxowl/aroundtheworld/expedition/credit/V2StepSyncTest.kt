package com.boxowl.aroundtheworld.expedition.credit

import com.boxowl.aroundtheworld.expedition.ExpeditionV2Row
import com.boxowl.aroundtheworld.expedition.JourneyMode
import com.boxowl.aroundtheworld.expedition.passport.PassportSnapshot
import com.boxowl.aroundtheworld.expedition.route.ROUTE_GEOMETRY_VERSION
import com.boxowl.aroundtheworld.health.Availability
import com.boxowl.aroundtheworld.health.ReadWindow
import com.boxowl.aroundtheworld.health.StepTotal
import com.boxowl.aroundtheworld.health.StepsGateway
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class V2StepSyncTest {
    private val start = Instant.parse("2026-11-10T08:00:00Z")

    @Test fun firstDayIsClippedAndExplicitZeroIsStored() = runBlocking {
        val gateway = FakeGateway { window ->
            StepTotal(if (window.start == start) 42L else 0L)
        }
        val saved = mutableListOf<Map<LocalDate, Long>>()
        val current = snapshot()
        val now = Instant.parse("2026-11-11T10:00:00Z")
        val result = V2StepSync(gateway) { seq, days, readAt ->
            assertEquals(7L, seq)
            assertEquals(now, readAt)
            saved += days.toMap()
            current
        }.refresh(current, now) as V2SyncResult.Updated

        assertEquals(2, result.scanned)
        assertEquals(0, result.gaps)
        assertEquals(false, result.limited)
        assertEquals(start, gateway.windows.first().start)
        assertEquals(Instant.parse("2026-11-10T21:00:00Z"), gateway.windows.first().end)
        assertEquals(now, gateway.windows.last().end)
        assertEquals(mapOf(LocalDate.parse("2026-11-10") to 42L, LocalDate.parse("2026-11-11") to 0L), saved.single())
    }

    @Test fun scanIsBoundedAndNullNeverBecomesZero() = runBlocking {
        val gateway = FakeGateway { window ->
            StepTotal(if (window.end == Instant.parse("2027-04-15T12:00:00Z")) 90L else null)
        }
        val current = snapshot()
        var saved = emptyMap<LocalDate, Long>()
        val now = Instant.parse("2027-04-15T12:00:00Z")
        val result = V2StepSync(gateway) { _, days, _ -> saved = days.toMap(); current }
            .refresh(current, now) as V2SyncResult.Updated

        assertEquals(120, result.scanned)
        assertEquals(119, result.gaps)
        assertTrue(result.limited)
        assertEquals(now.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate().minusDays(119),
            gateway.windows.first().start.atZone(java.time.ZoneId.of("Europe/Moscow")).toLocalDate())
        assertEquals(mapOf(LocalDate.parse("2027-04-15") to 90L), saved)
    }

    @Test fun partialReadKeepsOnlyKnownDaysAndRevocationPreventsWrite() = runBlocking {
        val current = snapshot()
        val now = Instant.parse("2026-11-11T10:00:00Z")
        val gateway = FakeGateway { window ->
            if (window.start == start) error("history unavailable") else StepTotal(17L)
        }
        var writes = 0
        val sync = V2StepSync(gateway) { _, days, _ ->
            writes++
            assertEquals(mapOf(LocalDate.parse("2026-11-11") to 17L), days)
            current
        }
        val partial = sync.refresh(current, now) as V2SyncResult.Updated
        assertEquals(1, partial.gaps)
        assertEquals(1, writes)

        gateway.permissionChecks = 0
        gateway.revokeAfterFirstCheck = true
        assertEquals(V2SyncResult.PermissionRequired, sync.refresh(current, now))
        assertEquals(1, writes)
    }

    @Test fun allFailedReadsAndStaleTimeNeverWrite() = runBlocking {
        val current = snapshot(lastReadAt = "2026-11-11T11:00:00Z")
        val gateway = FakeGateway { error("source failed") }
        var writes = 0
        val sync = V2StepSync(gateway) { _, _, _ -> writes++; current }

        assertEquals(V2SyncResult.ReadError, sync.refresh(current, Instant.parse("2026-11-11T10:00:00Z")))
        assertTrue(gateway.windows.isEmpty())
        assertEquals(V2SyncResult.ReadError, sync.refresh(current, Instant.parse("2026-11-11T12:00:00Z")))
        assertEquals(0, writes)
    }

    @Test fun deniedHistoricalWindowIsGapWhenPermissionRemainsGranted() = runBlocking {
        val current = snapshot()
        val gateway = FakeGateway { window ->
            if (window.start == start) throw SecurityException("history window denied")
            StepTotal(17L)
        }
        var saved = emptyMap<LocalDate, Long>()
        val result = V2StepSync(gateway) { _, days, _ -> saved = days.toMap(); current }
            .refresh(current, Instant.parse("2026-11-11T10:00:00Z")) as V2SyncResult.Updated

        assertEquals(2, result.scanned)
        assertEquals(1, result.gaps)
        assertEquals(mapOf(LocalDate.parse("2026-11-11") to 17L), saved)
    }

    @Test fun cancellationPropagatesWithoutWriting() = runBlocking {
        val current = snapshot()
        val gateway = FakeGateway { throw CancellationException("cancelled") }
        var writes = 0
        val sync = V2StepSync(gateway) { _, _, _ -> writes++; current }

        assertThrows(CancellationException::class.java) {
            runBlocking { sync.refresh(current, Instant.parse("2026-11-11T10:00:00Z")) }
        }
        assertEquals(0, writes)
    }

    @Test fun exactStartHasNoReadWindowButKeepsUnknownDay() = runBlocking {
        val current = snapshot()
        val gateway = FakeGateway { error("must not read an empty interval") }
        var saved: Map<LocalDate, Long>? = null
        val result = V2StepSync(gateway) { _, days, _ -> saved = days.toMap(); current }
            .refresh(current, start) as V2SyncResult.Updated

        assertEquals(0, result.scanned)
        assertTrue(gateway.windows.isEmpty())
        assertEquals(emptyMap<LocalDate, Long>(), saved)
    }

    @Test fun rereadSendsReplacementRatherThanIncrement() = runBlocking {
        var count = 42L
        val gateway = FakeGateway { StepTotal(count) }
        val current = snapshot()
        val saved = mutableListOf<Map<LocalDate, Long>>()
        val sync = V2StepSync(gateway) { _, days, _ -> saved += days.toMap(); current }
        val now = Instant.parse("2026-11-10T10:00:00Z")

        assertTrue(sync.refresh(current, now) is V2SyncResult.Updated)
        count = 51L
        assertTrue(sync.refresh(current, now.plusSeconds(60)) is V2SyncResult.Updated)
        assertEquals(listOf(42L, 51L), saved.map { it.getValue(LocalDate.parse("2026-11-10")) })
        assertFalse(saved.last().containsValue(93L))
    }

    private fun snapshot(lastReadAt: String? = null) = V2CreditSnapshot(
        expedition = ExpeditionV2Row(
            id = 7L, startedAt = start.toString(), zone = "Europe/Moscow", mode = JourneyMode.FREE.name,
            state = "ACTIVE", activeSlot = 1, endedAt = null, lastReadAt = lastReadAt,
            routeVersion = 2, geometryVersion = ROUTE_GEOMETRY_VERSION,
            strideMillimeters = 750L, bookMetersPerRealMeter = 20,
        ),
        stepsByDay = emptyMap(), ledger = CreditLedger.of(), passport = PassportSnapshot.of(),
    )

    private class FakeGateway(private val read: (ReadWindow) -> StepTotal) : StepsGateway {
        val windows = mutableListOf<ReadWindow>()
        var permissionChecks = 0
        var revokeAfterFirstCheck = false
        override fun availability() = Availability.AVAILABLE
        override suspend fun hasPermission(): Boolean {
            permissionChecks++
            return !revokeAfterFirstCheck || permissionChecks == 1
        }
        override suspend fun aggregate(window: ReadWindow): StepTotal {
            windows += window
            return read(window)
        }
    }
}
