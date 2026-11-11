package com.boxowl.aroundtheworld.expedition

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.boxowl.aroundtheworld.expedition.credit.V2CreditRepository
import com.boxowl.aroundtheworld.expedition.passport.StampId
import com.boxowl.aroundtheworld.expedition.route.ROUTE_GEOMETRY_VERSION
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class V2CreditRepositoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val day = LocalDate.parse("2026-11-10")
    private val start = Instant.parse("2026-11-10T08:00:00Z")
    private val zone = ZoneId.of("Europe/Moscow")

    @Test fun startReconcileRestartAndSecondExpeditionKeepSeparateHistory() = runBlocking {
        val name = "v2-credit-${System.nanoTime()}.db"
        try {
            val db = Room.databaseBuilder(context, ExpeditionDatabase::class.java, name).build()
            val firstSeq: Long
            try {
                val repository = V2CreditRepository(db)
                assertNull(repository.loadActive())
                val first = repository.start(null, start, zone, JourneyMode.FREE, 750L)
                firstSeq = first.expedition.id
                assertEquals(start, first.passport.granted[StampId.LONDON_START])
                val afterWalk = repository.reconcile(firstSeq, mapOf(day to 300_000L), start.plusSeconds(60))
                assertEquals(225_000L, afterWalk.ledger.totalRealMeters)
                assertEquals(4_500_000L, afterWalk.ledger.totalBookMeters)
                assertTrue(StampId.SUEZ_FINISH in afterWalk.passport.granted)
                assertEquals(start.plusSeconds(60), afterWalk.passport.granted[StampId.SUEZ_FINISH])
                val corrected = repository.reconcile(firstSeq, mapOf(day to 0L), start.plusSeconds(120))
                assertEquals(0L, corrected.ledger.totalRealMeters)
                assertTrue(StampId.SUEZ_FINISH in corrected.passport.granted)
                assertEquals(4_500_000L, db.v2Dao().meta(firstSeq)!!.baselineBookMeters)
            } finally { db.close() }

            val reopened = Room.databaseBuilder(context, ExpeditionDatabase::class.java, name).build()
            try {
                val repository = V2CreditRepository(reopened)
                val restored = repository.loadActive()!!
                assertEquals(mapOf(day to 0L), restored.stepsByDay)
                assertTrue(StampId.SUEZ_FINISH in restored.passport.granted)
                val second = repository.start(firstSeq, start.plusSeconds(180), zone, JourneyMode.WAGER, 800L)
                assertTrue(second.expedition.id > firstSeq)
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.start(firstSeq, start.plusSeconds(181), zone, JourneyMode.WAGER, 800L) }
                }
                assertTrue(second.stepsByDay.isEmpty())
                assertEquals(setOf(StampId.LONDON_START), second.passport.granted.keys)
                assertThrows(IllegalArgumentException::class.java) {
                    runBlocking { repository.reconcile(firstSeq, mapOf(day to 99_000L), start.plusSeconds(240)) }
                }
                val secondWalk = repository.reconcile(second.expedition.id, mapOf(day to 1_000L), start.plusSeconds(240))
                assertEquals(800L, secondWalk.ledger.totalRealMeters)
                assertEquals(1_000L, reopened.v2Dao().days(secondWalk.expedition.id).single().steps)
                assertEquals(0L, reopened.v2Dao().days(firstSeq).single().steps)
                assertTrue(reopened.v2Dao().stamps(firstSeq).any { it.stampId == StampId.SUEZ_FINISH.id })
                assertEquals("ENDED", reopened.v2Dao().expedition(firstSeq)!!.state)
                assertEquals(1, reopened.v2Dao().active().size)
            } finally { reopened.close() }
        } finally { context.deleteDatabase(name) }
    }

    @Test fun missingBaselineRestoresOldProgressWithUnknownDates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ExpeditionDatabase::class.java).build()
        try {
            val seq = db.v2Dao().insertExpedition(ExpeditionV2Row(
                startedAt = start.toString(), zone = zone.id, mode = JourneyMode.FREE.name,
                state = "ACTIVE", activeSlot = 1, endedAt = null, lastReadAt = null, routeVersion = 2,
                geometryVersion = ROUTE_GEOMETRY_VERSION, strideMillimeters = 750L,
                bookMetersPerRealMeter = 20,
            ))
            db.v2Dao().putDays(listOf(DailyCreditRow(seq, day.toString(), 300_000L, 225_000L)))
            val restored = V2CreditRepository(db).loadActive()!!
            assertTrue(StampId.SUEZ_FINISH in restored.passport.granted)
            assertNull(restored.passport.granted[StampId.SUEZ_FINISH])
            assertEquals(restored.ledger.totalBookMeters, db.v2Dao().meta(seq)!!.baselineBookMeters)
            assertNull(db.v2Dao().expedition(seq)!!.lastReadAt)
            val later = V2CreditRepository(db).reconcile(seq, mapOf(day to 0L), start.plusSeconds(120))
            assertTrue(StampId.SUEZ_FINISH in later.passport.granted)
            assertNull(later.passport.granted[StampId.SUEZ_FINISH])
        } finally { db.close() }
    }

    @Test fun oneReconcileSeparatesHistoricalAndNewStampDates() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ExpeditionDatabase::class.java).build()
        try {
            val seq = db.v2Dao().insertExpedition(ExpeditionV2Row(
                startedAt = start.toString(), zone = zone.id, mode = JourneyMode.FREE.name,
                state = "ACTIVE", activeSlot = 1, endedAt = null, lastReadAt = null, routeVersion = 2,
                geometryVersion = ROUTE_GEOMETRY_VERSION, strideMillimeters = 750L,
                bookMetersPerRealMeter = 20,
            ))
            db.v2Dao().putMeta(PassportMetaRow(seq, 3_000_000L))
            val observed = start.plusSeconds(60)
            val first = V2CreditRepository(db).reconcile(seq, mapOf(day to 300_000L), observed)
            assertTrue(StampId.LONDON_START in first.passport.granted)
            assertNull(first.passport.granted[StampId.LONDON_START])
            assertEquals(observed, first.passport.granted[StampId.SUEZ_FINISH])
            val repeated = V2CreditRepository(db).reconcile(seq, mapOf(day to 300_000L), observed)
            assertEquals(first.passport, repeated.passport)
            assertEquals(first.ledger, repeated.ledger)
        } finally { db.close() }
    }

    @Test fun databasePreventsTwoActiveRowsEvenThroughDao() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ExpeditionDatabase::class.java).build()
        try {
            val row = ExpeditionV2Row(
                startedAt = start.toString(), zone = zone.id, mode = JourneyMode.FREE.name,
                state = "ACTIVE", activeSlot = 1, endedAt = null, lastReadAt = null, routeVersion = 2,
                geometryVersion = ROUTE_GEOMETRY_VERSION, strideMillimeters = 750L,
                bookMetersPerRealMeter = 20,
            )
            db.v2Dao().insertExpedition(row)
            assertThrows(Exception::class.java) { runBlocking { db.v2Dao().insertExpedition(row) } }
            assertEquals(1, db.v2Dao().active().size)
        } finally { db.close() }
    }

    @Test fun invalidReplacementAndStaleReadDoNotChangeStoredDays() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ExpeditionDatabase::class.java).build()
        try {
            val repository = V2CreditRepository(db)
            val seq = repository.start(null, start, zone, JourneyMode.FREE, 750L).expedition.id
            repository.reconcile(seq, mapOf(day to 1_000L), start.plusSeconds(60))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.reconcile(seq, mapOf(day to -1L), start.plusSeconds(120)) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { repository.reconcile(seq, mapOf(day to 2_000L), start.plusSeconds(30)) }
            }
            assertEquals(1_000L, repository.loadActive()!!.stepsByDay[day])
            assertFalse(repository.loadActive()!!.passport.granted.isEmpty())
        } finally { db.close() }
    }

    @Test fun stampWriteFailureRollsBackDailyCreditAndBaseline() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, ExpeditionDatabase::class.java).build()
        try {
            val repository = V2CreditRepository(db)
            val seq = repository.start(null, start, zone, JourneyMode.FREE, 750L).expedition.id
            db.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_suez BEFORE INSERT ON passport_stamps " +
                    "WHEN NEW.stampId = 'stamp-suez-finish' BEGIN SELECT RAISE(ABORT, 'test failure'); END"
            )
            assertThrows(Exception::class.java) {
                runBlocking { repository.reconcile(seq, mapOf(day to 300_000L), start.plusSeconds(60)) }
            }
            assertTrue(db.v2Dao().days(seq).isEmpty())
            assertEquals(0L, db.v2Dao().meta(seq)!!.baselineBookMeters)
            assertNull(db.v2Dao().expedition(seq)!!.lastReadAt)
            assertEquals(setOf(StampId.LONDON_START), repository.loadActive()!!.passport.granted.keys)
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_suez")
            assertTrue(StampId.SUEZ_FINISH in repository.reconcile(seq, mapOf(day to 300_000L), start.plusSeconds(60)).passport.granted)
        } finally { db.close() }
    }
}
