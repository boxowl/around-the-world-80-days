package com.boxowl.aroundtheworld.health

import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class StepDiagnosticsTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")
    private val zone = ZoneId.of("Europe/Moscow")
    private class Fake : StepsGateway {
        var available = Availability.AVAILABLE
        var permitted = true
        var value: Long? = 3500
        var failure: Exception? = null
        val windows = mutableListOf<ReadWindow>()
        override fun availability() = available
        override suspend fun hasPermission() = permitted
        override suspend fun aggregate(window: ReadWindow): StepTotal {
            failure?.let { throw it }
            windows += window
            return StepTotal(value)
        }
    }
    @Test fun repeatedReadsReplaceAndAcceptCorrections() = runBlocking {
        val fake = Fake()
        val reader = StepDiagnostics(fake)
        fun count(state: DiagnosticState) = (state as DiagnosticState.Ready).snapshot.today.count
        assertEquals(3500L, count(reader.read(now, zone)))
        assertEquals(3500L, count(reader.read(now, zone)))
        fake.value = 3000
        assertEquals(3000L, count(reader.read(now, zone)))
    }
    @Test fun noDataIsDistinctFromZero() = runBlocking {
        val fake = Fake()
        val reader = StepDiagnostics(fake)
        fake.value = null
        val noData = (reader.read(now, zone) as DiagnosticState.Ready).snapshot
        assertNull(noData.today.count)
        assertNull(noData.yesterday.count)
        fake.value = 0
        val zero = (reader.read(now, zone) as DiagnosticState.Ready).snapshot
        assertEquals(0L, zero.today.count)
        assertEquals(0L, zero.yesterday.count)
    }
    @Test fun lateYesterdayCorrectionReplacesOnlyYesterdayAndWeek() = runBlocking {
        var yesterdayCount = 200L
        val gateway = object : StepsGateway {
            override fun availability() = Availability.AVAILABLE
            override suspend fun hasPermission() = true
            override suspend fun aggregate(window: ReadWindow): StepTotal = StepTotal(
                when (window.start) {
                    Instant.parse("2026-09-12T21:00:00Z") -> 1_000L // today
                    Instant.parse("2026-09-11T21:00:00Z") -> yesterdayCount
                    Instant.parse("2026-09-06T21:00:00Z") -> 1_000L + yesterdayCount
                    else -> error("unexpected window: $window")
                },
            )
        }
        val reader = StepDiagnostics(gateway)
        val before = (reader.read(now, zone) as DiagnosticState.Ready).snapshot
        assertEquals(1_000L, before.today.count)
        assertEquals(200L, before.yesterday.count)
        assertEquals(1_200L, before.week.count)
        yesterdayCount = 250L
        val after = (reader.read(now, zone) as DiagnosticState.Ready).snapshot
        assertEquals(1_000L, after.today.count)
        assertEquals(250L, after.yesterday.count)
        assertEquals(1_250L, after.week.count)
    }
    @Test fun unavailableOrDeniedNeverRead() = runBlocking {
        val fake = Fake()
        val reader = StepDiagnostics(fake)
        fake.available = Availability.UNAVAILABLE
        assertEquals(DiagnosticState.Unavailable, reader.read(now, zone))
        fake.available = Availability.UPDATE_REQUIRED
        assertEquals(DiagnosticState.UpdateRequired, reader.read(now, zone))
        fake.available = Availability.AVAILABLE
        fake.permitted = false
        assertEquals(DiagnosticState.PermissionRequired, reader.read(now, zone))
        assertTrue(fake.windows.isEmpty())
    }
    @Test fun errorsAndRevocationDoNotBecomeZero() = runBlocking {
        val fake = Fake()
        val reader = StepDiagnostics(fake)
        fake.failure = SecurityException()
        assertEquals(DiagnosticState.PermissionRequired, reader.read(now, zone))
        fake.failure = IllegalStateException()
        assertEquals(DiagnosticState.ReadError, reader.read(now, zone))
    }
    @Test fun cancellationPropagates() = runBlocking {
        val fake = Fake().apply { failure = CancellationException() }
        try {
            StepDiagnostics(fake).read(now, zone)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }
    @Test fun windowsUseCalendarDatesAcrossDaylightSaving() = runBlocking {
        val fake = Fake()
        val time = Instant.parse("2026-03-29T12:00:00Z")
        val result = StepDiagnostics(fake).read(time, ZoneId.of("Europe/London")) as DiagnosticState.Ready
        assertEquals(Instant.parse("2026-03-29T00:00:00Z"), result.snapshot.todayWindow.start)
        assertEquals(Instant.parse("2026-03-28T00:00:00Z"), result.snapshot.yesterdayWindow.start)
        assertEquals(Instant.parse("2026-03-29T00:00:00Z"), result.snapshot.yesterdayWindow.end)
        assertEquals(Instant.parse("2026-03-23T00:00:00Z"), result.snapshot.weekWindow.start)
        assertEquals(time, result.snapshot.weekWindow.end)
    }
    @Test fun yesterdayUsesCalendarDayAcrossDaylightSaving() = runBlocking {
        val fake = Fake()
        val time = Instant.parse("2026-03-30T12:00:00Z")
        val snapshot = (StepDiagnostics(fake).read(time, ZoneId.of("Europe/London")) as DiagnosticState.Ready).snapshot
        assertEquals(Instant.parse("2026-03-29T00:00:00Z"), snapshot.yesterdayWindow.start)
        assertEquals(Instant.parse("2026-03-29T23:00:00Z"), snapshot.yesterdayWindow.end)
        assertEquals(23 * 60 * 60L, snapshot.yesterdayWindow.end.epochSecond - snapshot.yesterdayWindow.start.epochSecond)
    }
    @Test fun midnightDoesNotQueryEmptyInterval() = runBlocking {
        val fake = Fake()
        val time = Instant.parse("2026-09-12T21:00:00Z")
        val result = StepDiagnostics(fake).read(time, zone) as DiagnosticState.Ready
        assertNull(result.snapshot.today.count)
        assertEquals(2, fake.windows.size)
        assertEquals(Instant.parse("2026-09-11T21:00:00Z"), fake.windows[0].start)
        assertEquals(Instant.parse("2026-09-12T21:00:00Z"), fake.windows[0].end)
        assertEquals(Instant.parse("2026-09-06T21:00:00Z"), fake.windows[1].start)
    }
    @Test fun revokedPermissionAfterReadingDiscardsSnapshot() = runBlocking {
        val gateway = object : StepsGateway {
            var checks = 0
            override fun availability() = Availability.AVAILABLE
            override suspend fun hasPermission() = ++checks == 1
            override suspend fun aggregate(window: ReadWindow) = StepTotal(100)
        }
        assertEquals(DiagnosticState.PermissionRequired, StepDiagnostics(gateway).read(now, zone))
    }
    @Test fun secondReadFailureDiscardsPartialSnapshot() = runBlocking {
        val gateway = object : StepsGateway {
            var reads = 0
            override fun availability() = Availability.AVAILABLE
            override suspend fun hasPermission() = true
            override suspend fun aggregate(window: ReadWindow): StepTotal {
                if (++reads == 2) throw IllegalStateException("Provider unavailable")
                return StepTotal(100)
            }
        }
        assertEquals(DiagnosticState.ReadError, StepDiagnostics(gateway).read(now, zone))
    }
    @Test fun weekReadFailureDiscardsTodayAndYesterday() = runBlocking {
        val gateway = object : StepsGateway {
            var reads = 0
            override fun availability() = Availability.AVAILABLE
            override suspend fun hasPermission() = true
            override suspend fun aggregate(window: ReadWindow): StepTotal {
                if (++reads == 3) throw IllegalStateException("Provider unavailable")
                return StepTotal(100)
            }
        }
        assertEquals(DiagnosticState.ReadError, StepDiagnostics(gateway).read(now, zone))
    }

}
