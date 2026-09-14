package com.boxowl.aroundtheworld.expedition

import com.boxowl.aroundtheworld.health.Availability
import com.boxowl.aroundtheworld.health.ReadWindow
import com.boxowl.aroundtheworld.health.StepTotal
import com.boxowl.aroundtheworld.health.StepsGateway
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExpeditionSyncTest {
    private val zone = ZoneId.of("Europe/London")
    private val start = Instant.parse("2026-03-28T12:00:00Z")
    private var saved = Expedition(start, zone, JourneyMode.FREE)
    private val ledger = object : ExpeditionLedger {
        override suspend fun reconcile(replacements: Map<LocalDate, Long>, readAt: Instant): Expedition =
            saved.reconcile(replacements, readAt).also { saved = it }
    }
    private class FakeGateway : StepsGateway {
        var available = Availability.AVAILABLE
        var permission = true
        var revokeOnRead = false
        val windows = mutableListOf<ReadWindow>()
        val values = mutableMapOf<ReadWindow, StepTotal>()
        val errors = mutableSetOf<ReadWindow>()
        val historyDenied = mutableSetOf<ReadWindow>()
        override fun availability() = available
        override suspend fun hasPermission() = permission
        override suspend fun aggregate(window: ReadWindow): StepTotal {
            windows += window
            if (revokeOnRead) permission = false
            if (window in historyDenied) throw SecurityException("history is restricted")
            if (window in errors) throw IllegalStateException("historical read unavailable")
            return values[window] ?: StepTotal(null)
        }
    }

    @Test fun firstDayStartsAtExactInstantAndMidnightDstUsesFixedZone() = runBlocking {
        val gateway = FakeGateway()
        val now = Instant.parse("2026-03-30T10:00:00Z")
        val first = ReadWindow(start, Instant.parse("2026-03-29T00:00:00Z"))
        val dstDay = ReadWindow(first.end, Instant.parse("2026-03-29T23:00:00Z"))
        val third = ReadWindow(dstDay.end, now)
        gateway.values[first] = StepTotal(300)
        gateway.values[dstDay] = StepTotal(500)
        gateway.values[third] = StepTotal(700)
        val result = ExpeditionSync(gateway, ledger).refresh(saved, now) as SyncResult.Updated
        assertEquals(listOf(first, dstDay, third), gateway.windows)
        assertEquals(23, java.time.Duration.between(dstDay.start, dstDay.end).toHours())
        assertEquals(mapOf(LocalDate.parse("2026-03-28") to 300L, LocalDate.parse("2026-03-29") to 500L,
            LocalDate.parse("2026-03-30") to 700L), result.expedition.dailySteps)
    }

    @Test fun aggregateAcrossTwoOriginsIsOneReplacementAndCanDecrease() = runBlocking {
        val gateway = FakeGateway()
        val now = start.plusSeconds(100)
        val window = ReadWindow(start, now)
        gateway.values[window] = StepTotal(4_000, setOf("phone", "watch"))
        val sync = ExpeditionSync(gateway, ledger)
        assertEquals(4_000L, (sync.refresh(saved, now) as SyncResult.Updated).expedition.totalSteps)
        assertEquals(4_000L, (sync.refresh(saved, now) as SyncResult.Updated).expedition.totalSteps)
        gateway.values[window] = StepTotal(1_500, setOf("watch"))
        assertEquals(1_500L, (sync.refresh(saved, now) as SyncResult.Updated).expedition.totalSteps)
        assertTrue("dover" in saved.unlocked)
    }

    @Test fun nullAndHistoricalErrorRetainKnownDaysButUpdateAvailableDays() = runBlocking {
        val gateway = FakeGateway()
        val day1 = LocalDate.parse("2026-03-28")
        saved = saved.reconcile(mapOf(day1 to 2_000), start.plusSeconds(1))
        val now = Instant.parse("2026-03-30T10:00:00Z")
        gateway.errors += ReadWindow(start, Instant.parse("2026-03-29T00:00:00Z"))
        gateway.values[ReadWindow(Instant.parse("2026-03-29T23:00:00Z"), now)] = StepTotal(600)
        val result = ExpeditionSync(gateway, ledger).refresh(saved, now) as SyncResult.Updated
        assertEquals(2, result.gaps)
        assertEquals(2_000L, saved.dailySteps[day1])
        assertEquals(600L, saved.dailySteps[LocalDate.parse("2026-03-30")])
    }

    @Test fun deniedOrRevokedPermissionNeverCommitsPartialRead() = runBlocking {
        val gateway = FakeGateway()
        val now = start.plusSeconds(100)
        gateway.permission = false
        assertEquals(SyncResult.PermissionRequired, ExpeditionSync(gateway, ledger).refresh(saved, now))
        assertNull(saved.lastReadAt)
        gateway.permission = true
        gateway.revokeOnRead = true
        gateway.values[ReadWindow(start, now)] = StepTotal(10_000)
        assertEquals(SyncResult.PermissionRequired, ExpeditionSync(gateway, ledger).refresh(saved, now))
        assertTrue(saved.dailySteps.isEmpty())
    }

    @Test fun allReadErrorsDoNotTurnMissingHistoryIntoZero() = runBlocking {
        val gateway = FakeGateway()
        val now = start.plusSeconds(100)
        gateway.errors += ReadWindow(start, now)
        assertEquals(SyncResult.ReadError, ExpeditionSync(gateway, ledger).refresh(saved, now))
        assertNull(saved.lastReadAt)
    }

    @Test fun restrictedOldHistoryDoesNotBlockFreshAccessibleDays() = runBlocking {
        val gateway = FakeGateway()
        val now = Instant.parse("2026-03-30T10:00:00Z")
        gateway.historyDenied += ReadWindow(start, Instant.parse("2026-03-29T00:00:00Z"))
        gateway.values[ReadWindow(Instant.parse("2026-03-29T23:00:00Z"), now)] = StepTotal(900)
        val result = ExpeditionSync(gateway, ledger).refresh(saved, now) as SyncResult.Updated
        assertEquals(2, result.gaps)
        assertEquals(900L, result.expedition.totalSteps)
        assertEquals(3, gateway.windows.size)
    }
}
